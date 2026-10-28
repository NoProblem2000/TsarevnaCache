#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Compiles the native Windows launcher (launcher.c) with mingw-w64.
#
#   src/launcher/build-native.sh <version> [output.exe]
#
#   src/launcher/build-native.sh 1.0.1 target/HazelcastInspector-native.exe
#
# Needs x86_64-w64-mingw32-gcc and x86_64-w64-mingw32-windres on the PATH:
#   Debian/Ubuntu: apt-get install gcc-mingw-w64-x86-64 binutils-mingw-w64-x86-64
#   Windows:       llvm-mingw (https://github.com/mstorsjo/llvm-mingw), run from Git Bash
# -----------------------------------------------------------------------------
set -euo pipefail

VERSION="${1:?usage: build-native.sh <version> [output.exe]}"
OUTPUT="${2:-target/HazelcastInspector-native.exe}"
SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"
CC="${CC:-x86_64-w64-mingw32-gcc}"
WINDRES="${WINDRES:-x86_64-w64-mingw32-windres}"

command -v "$CC" > /dev/null || { echo "ERROR: $CC not found (install mingw-w64)" >&2; exit 1; }
command -v "$WINDRES" > /dev/null || { echo "ERROR: $WINDRES not found (install mingw-w64 binutils)" >&2; exit 1; }

# Numeric Windows file version from e.g. 1.2.3, 1.2.3-rc.1 or 1.2.3-SNAPSHOT
IFS='.-' read -r MAJOR MINOR PATCH _ <<< "$VERSION"
for part in "$MAJOR" "$MINOR" "$PATCH"; do
    [[ "$part" =~ ^[0-9]+$ ]] || { echo "ERROR: version must start with major.minor.patch, got '$VERSION'" >&2; exit 1; }
done

mkdir -p "$(dirname "$OUTPUT")"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

"$WINDRES" "-DLAUNCHER_VERSION=$MAJOR,$MINOR,$PATCH,0" "-DLAUNCHER_VERSION_STR=\\\"$VERSION\\\"" \
    "$SOURCE_DIR/launcher.rc" -O coff -o "$WORK_DIR/launcher.res"
"$CC" -O2 -s -municode -Wall -Wextra -Werror -o "$OUTPUT" "$SOURCE_DIR/launcher.c" "$WORK_DIR/launcher.res"

echo "Built $OUTPUT ($VERSION)"
