#!/usr/bin/env sh
# -----------------------------------------------------------------------------
# Hazelcast Inspector (TsarevnaCache) start script for Linux and macOS.
#
#   bin/start.sh [extra Spring Boot arguments, e.g. --server.port=9090]
#
# Environment:
#   JAVA_HOME   JDK/JRE 17+ to use (otherwise "java" from the PATH)
#   JAVA_OPTS   additional JVM options, e.g. "-Xmx1g"
#   Any variable used in config/application.yml (ADMIN_PASSWORD, HZ_MEMBERS, ...)
# -----------------------------------------------------------------------------
set -eu

APP_HOME=$(cd "$(dirname "$0")/.." && pwd -P)
APP_JAR="$APP_HOME/hazelcast-inspector.jar"

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD=$(command -v java)
else
    echo "ERROR: Java 17 or newer is required. Install it or set JAVA_HOME." >&2
    exit 1
fi

JAVA_VERSION=$("$JAVA_CMD" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')
JAVA_MAJOR=$(echo "$JAVA_VERSION" | awk -F '.' '{ if ($1 == "1") print $2; else print $1 }')
case "$JAVA_MAJOR" in
    ''|*[!0-9]*) echo "WARNING: could not determine the Java version ($JAVA_VERSION)." >&2 ;;
    *)
        if [ "$JAVA_MAJOR" -lt 17 ]; then
            echo "ERROR: Java 17 or newer is required, found $JAVA_VERSION ($JAVA_CMD)." >&2
            exit 1
        fi
        ;;
esac

if [ ! -f "$APP_JAR" ]; then
    echo "ERROR: $APP_JAR not found." >&2
    exit 1
fi

# G1, heap sized relative to the container/host memory, fail fast on OOM.
DEFAULT_JVM_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

# Module access recommended by Hazelcast for Java 17+ (avoids reflective access warnings).
HAZELCAST_JVM_OPTS="--add-modules java.se \
 --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
 --add-opens java.base/java.lang=ALL-UNNAMED \
 --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
 --add-opens java.management/sun.management=ALL-UNNAMED \
 --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

cd "$APP_HOME"

# shellcheck disable=SC2086 # option lists are intentionally word-split
exec "$JAVA_CMD" $DEFAULT_JVM_OPTS $HAZELCAST_JVM_OPTS ${JAVA_OPTS:-} \
    "-Dloader.path=$APP_HOME/lib" \
    -jar "$APP_JAR" \
    "--spring.config.additional-location=optional:file:$APP_HOME/config/" \
    "$@"
