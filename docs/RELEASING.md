# Releasing Hazelcast Inspector

A release is a standalone archive that users download, unpack and start — no Node.js, no separate web
server:

```
hazelcast-inspector-<version>/
├── hazelcast-inspector.jar     backend + web UI (executable Spring Boot jar)
├── config/application.yml      external configuration: cluster, credentials, serializers
├── lib/README-LIBS.txt         drop custom DTO jars here (added via -Dloader.path)
├── bin/start.sh                Linux / macOS
├── bin/start.bat               Windows
├── LICENSE
└── README.md
```

Archives: `target/hazelcast-inspector-<version>-release.zip` and `.tar.gz`.

## Automated release (recommended)

```bash
scripts/create-release.sh 1.0.0
```

The script

1. switches to `release/v1.0.0` (creating it from `main` if needed),
2. sets the version in `pom.xml` and `frontend/package.json`,
3. builds and tests the release archive locally (`mvn clean verify -Prelease`),
4. commits the version change and creates the annotated tag `v1.0.0`,
5. pushes the branch and the tag.

Pushing the tag triggers CI, which builds the archive again and publishes it:

| CI | Release branch push | Tag `v*` push |
|---|---|---|
| GitHub Actions (`.github/workflows/release.yml`) | build + test, archives attached to the workflow run | GitHub Release with `.zip` and `.tar.gz` |
| GitLab CI (`.gitlab-ci.yml`) | build + test, archives as job artifacts (30 days) | archives in the Generic Package Registry + GitLab Release |

Both pipelines fail when the tag does not match the Maven version and verify that the archive
contains the jar, the UI bundle, the configuration and the start scripts. Tags with a suffix
(`v1.1.0-rc.1`) are published as pre-releases on GitHub.

Options of the script:

| Option | Effect |
|---|---|
| `--no-push` | Do everything locally; push branch and tag yourself later |
| `--skip-tests` | Build the archive without running the test suites |
| `--base <branch>` | Create the release branch from another branch (default `main`) |
| `--remote <name>` | Push to another remote (default `origin`) |

On Windows run the script from Git Bash.

## Manual release (step by step)

```bash
git switch main
git pull
git switch -c release/v1.0.0
./mvnw -B versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
(cd frontend && npm version 1.0.0 --no-git-tag-version)
./mvnw -B clean verify -Prelease
unzip -l target/hazelcast-inspector-1.0.0-release.zip
git commit -am "Release 1.0.0"
git tag -a v1.0.0 -m "Hazelcast Inspector 1.0.0"
git push --set-upstream origin release/v1.0.0
git push origin v1.0.0
```

## After the release

Continue development on `main` with the next snapshot version, e.g.:

```bash
git switch main
./mvnw -B versions:set -DnewVersion=1.1.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Start 1.1.0-SNAPSHOT"
```

Fixes for a released version go to its `release/v1.0.x` branch and are released as `v1.0.1`, then
merged back into `main`.

## Building a distribution without releasing

```bash
./mvnw clean package -Prelease
```

The archive in `target/` is identical to a published one (with the current, possibly `-SNAPSHOT`,
version). `-Prelease` refuses `-Dfrontend.skip=true`: a release always contains the UI.
