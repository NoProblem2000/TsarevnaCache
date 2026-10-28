@echo off
rem ---------------------------------------------------------------------------
rem Hazelcast Inspector (TsarevnaCache) start script for Windows.
rem
rem   bin\start.bat [extra Spring Boot arguments, e.g. --server.port=9090]
rem
rem Environment:
rem   JAVA_HOME   JDK/JRE 17+ to use (otherwise "java" from the PATH)
rem   JAVA_OPTS   additional JVM options, e.g. -Xmx1g
rem   Any variable used in config\application.yml (ADMIN_PASSWORD, HZ_MEMBERS, ...)
rem ---------------------------------------------------------------------------
setlocal EnableExtensions

for %%I in ("%~dp0..") do set "APP_HOME=%%~fI"
set "APP_JAR=%APP_HOME%\hazelcast-inspector.jar"

if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java 17 or newer is required. Install it or set JAVA_HOME. 1>&2
    exit /b 1
)

set "JAVA_VERSION="
rem "call" keeps a quoted path with spaces intact inside for /f
for /f "tokens=3" %%V in ('call "%JAVA_CMD%" -version 2^>^&1 ^| findstr /i "version"') do (
    if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
)
for /f "tokens=1,2 delims=." %%A in ("%JAVA_VERSION%") do (
    if "%%A"=="1" (set "JAVA_MAJOR=%%B") else (set "JAVA_MAJOR=%%A")
)
if not defined JAVA_MAJOR (
    echo WARNING: could not determine the Java version. 1>&2
) else (
    if %JAVA_MAJOR% LSS 17 (
        echo ERROR: Java 17 or newer is required, found %JAVA_VERSION%. 1>&2
        exit /b 1
    )
)

if not exist "%APP_JAR%" (
    echo ERROR: %APP_JAR% not found. 1>&2
    exit /b 1
)

rem G1, heap sized relative to the host memory, fail fast on OOM.
set "DEFAULT_JVM_OPTS=-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"

rem Module access recommended by Hazelcast for Java 17+ (avoids reflective access warnings).
set "HAZELCAST_JVM_OPTS=--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

rem Spring expects forward slashes in file: locations.
set "CONFIG_DIR=%APP_HOME:\=/%/config/"

cd /d "%APP_HOME%"

"%JAVA_CMD%" %DEFAULT_JVM_OPTS% %HAZELCAST_JVM_OPTS% %JAVA_OPTS% "-Dloader.path=%APP_HOME%\lib" -jar "%APP_JAR%" "--spring.config.additional-location=optional:file:%CONFIG_DIR%" %*
exit /b %ERRORLEVEL%
