#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Prepares a release of Hazelcast Inspector (TsarevnaCache).
#
#   scripts/create-release.sh <version> [--no-push] [--skip-tests] [--base <branch>] [--remote <name>]
#
#   scripts/create-release.sh 1.0.0
#
# Steps:
#   1. switches to (or creates) the branch release/v<version>
#   2. sets the version in pom.xml and frontend/package.json
#   3. builds the release archive locally (mvn clean verify -Prelease) for a check
#   4. commits the version change and creates the annotated tag v<version>
#   5. pushes branch and tag (CI then publishes the GitHub/GitLab Release)
#
# Options:
#   --no-push      do everything locally, push later yourself
#   --skip-tests   build the archive without running the test suites
#   --base BRANCH  branch to create the release branch from (default: main)
#   --remote NAME  git remote to push to (default: origin)
# -----------------------------------------------------------------------------
set -euo pipefail

usage() {
    sed -n '4,22p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

info() { printf '\033[36m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

VERSION=""
PUSH=true
SKIP_TESTS=false
BASE_BRANCH="main"
REMOTE="origin"

while [ $# -gt 0 ]; do
    case "$1" in
        --no-push) PUSH=false ;;
        --skip-tests) SKIP_TESTS=true ;;
        --base) [ $# -ge 2 ] || fail "--base needs a branch name"; BASE_BRANCH="$2"; shift ;;
        --remote) [ $# -ge 2 ] || fail "--remote needs a remote name"; REMOTE="$2"; shift ;;
        -h|--help) usage 0 ;;
        -*) fail "unknown option $1" ;;
        *) [ -z "$VERSION" ] || fail "version given twice"; VERSION="$1" ;;
    esac
    shift
done

[ -n "$VERSION" ] || usage 1
VERSION="${VERSION#v}"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]] \
    || fail "version must look like 1.0.0 or 1.0.0-rc.1, got '$VERSION'"
[[ "$VERSION" != *-SNAPSHOT ]] || fail "release versions must not end with -SNAPSHOT"

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || fail "run this inside the git repository"
cd "$ROOT"

BRANCH="release/v$VERSION"
TAG="v$VERSION"
DIST_NAME="hazelcast-inspector"

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) MVNW="./mvnw.cmd" ;;
    *) MVNW="./mvnw" ;;
esac

# --- preconditions -----------------------------------------------------------
[ -z "$(git status --porcelain)" ] || fail "the working tree has uncommitted changes; commit or stash them first"
git rev-parse -q --verify "refs/tags/$TAG" > /dev/null && fail "tag $TAG already exists"

if git remote get-url "$REMOTE" > /dev/null 2>&1; then
    info "Fetching $REMOTE"
    git fetch --quiet --tags "$REMOTE"
    git rev-parse -q --verify "refs/tags/$TAG" > /dev/null && fail "tag $TAG already exists on $REMOTE"
elif $PUSH; then
    fail "remote '$REMOTE' does not exist (use --remote or --no-push)"
fi

# --- 1. release branch ---------------------------------------------------------
if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
    info "Switching to existing branch $BRANCH"
    git switch --quiet "$BRANCH"
elif git show-ref --verify --quiet "refs/remotes/$REMOTE/$BRANCH"; then
    info "Checking out $BRANCH from $REMOTE"
    git switch --quiet --track "$REMOTE/$BRANCH"
else
    if git show-ref --verify --quiet "refs/remotes/$REMOTE/$BASE_BRANCH"; then
        START="$REMOTE/$BASE_BRANCH"
    elif git show-ref --verify --quiet "refs/heads/$BASE_BRANCH"; then
        START="$BASE_BRANCH"
    else
        fail "base branch '$BASE_BRANCH' not found"
    fi
    info "Creating $BRANCH from $START"
    git switch --quiet --create "$BRANCH" "$START"
fi

# --- 2. version ------------------------------------------------------------------
info "Setting version $VERSION"
"$MVNW" -B -q -ntp versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false -DprocessAllModules=true

pom_property() {
    "$MVNW" -B -q -ntp help:evaluate -Dexpression="$1" -DforceStdout
}

# package.json and package-lock.json are updated with npm: a global one, or the portable Node
# that the Maven build downloads into frontend/node (fetched here first when missing).
NODE_DIR="$ROOT/frontend/node"
if command -v npm > /dev/null 2>&1; then
    (cd frontend && npm version "$VERSION" --no-git-tag-version --allow-same-version > /dev/null)
else
    if [ ! -f "$NODE_DIR/node_modules/npm/bin/npm-cli.js" ]; then
        info "Downloading the portable Node.js used by the build"
        "$MVNW" -B -q -ntp "com.github.eirslett:frontend-maven-plugin:$(pom_property frontend-maven-plugin.version):install-node-and-npm" \
            -DnodeVersion="$(pom_property frontend.node.version)" -DworkingDirectory=frontend -DinstallDirectory=frontend
    fi
    NODE_BIN="$NODE_DIR/node"
    [ -f "$NODE_BIN" ] || NODE_BIN="$NODE_DIR/node.exe"
    (cd frontend && "$NODE_BIN" node/node_modules/npm/bin/npm-cli.js version "$VERSION" --no-git-tag-version --allow-same-version > /dev/null)
fi

# --- 3. local release build ---------------------------------------------------
BUILD_ARGS=(-B -ntp clean verify -Prelease)
$SKIP_TESTS && BUILD_ARGS+=(-DskipTests)
info "Building the release archive (${BUILD_ARGS[*]})"
"$MVNW" "${BUILD_ARGS[@]}"

ZIP="target/$DIST_NAME-$VERSION-release.zip"
[ -f "$ZIP" ] || fail "expected archive $ZIP was not produced"
info "Archive: $ZIP ($(du -h "$ZIP" | cut -f1))"
if command -v unzip > /dev/null 2>&1; then
    unzip -l "$ZIP"
fi

# --- 4. commit and tag ---------------------------------------------------------
if [ -n "$(git status --porcelain)" ]; then
    git add pom.xml frontend/package.json frontend/package-lock.json
    git commit --quiet -m "Release $VERSION"
    info "Committed version $VERSION"
else
    info "Version $VERSION was already committed"
fi
git tag -a "$TAG" -m "Hazelcast Inspector $VERSION"
info "Created tag $TAG"

# --- 5. push -------------------------------------------------------------------------
if $PUSH; then
    info "Pushing $BRANCH and $TAG to $REMOTE"
    git push --set-upstream "$REMOTE" "$BRANCH"
    git push "$REMOTE" "$TAG"
    info "Done. CI builds $TAG and publishes the release archive."
else
    info "Done (not pushed). Publish with:"
    echo "    git push --set-upstream $REMOTE $BRANCH"
    echo "    git push $REMOTE $TAG"
fi
