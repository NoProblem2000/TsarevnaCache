/*
 * HazelcastInspector.exe - native Windows launcher for the Hazelcast Inspector distribution.
 *
 * Starts java.exe directly with CreateProcessW (no cmd.exe, no PowerShell), so it also works
 * where script interpreters are blocked by policy.
 *
 *   - Java lookup: <exe dir>\jre\bin\java.exe, then %JAVA_HOME%\bin\java.exe, then java.exe on PATH
 *   - Working directory: the directory of this executable (lib\ and config\ resolve against it)
 *   - JVM options: same as bin\start.bat, plus %JAVA_OPTS% when set
 *   - Arguments given to the launcher are appended to the Spring Boot command line
 *   - Shares the console: Spring Boot logs appear in this window; Ctrl+C / Ctrl+Break reach java
 *     directly and the launcher waits for it to shut down. A job object kills java whenever the
 *     launcher itself ends (window closed, process killed), so no orphan JVM is left behind.
 *   - Exit code: the exit code of java
 *
 * Build (mingw-w64, e.g. on Linux or with llvm-mingw on Windows):
 *   x86_64-w64-mingw32-windres launcher.rc -O coff -o launcher.res
 *   x86_64-w64-mingw32-gcc -O2 -s -municode -Wall -Wextra -o HazelcastInspector.exe launcher.c launcher.res
 */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

#define APP_JAR L"hazelcast-inspector.jar"
#define CMDLINE_CAPACITY 32768 /* CreateProcessW limit */

static const wchar_t *DEFAULT_JVM_OPTS =
    L"-Xms256m -Xmx1024m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8";

/* Module access recommended by Hazelcast for Java 17+. */
static const wchar_t *HAZELCAST_JVM_OPTS =
    L"--add-modules java.se"
    L" --add-exports java.base/jdk.internal.ref=ALL-UNNAMED"
    L" --add-opens java.base/java.lang=ALL-UNNAMED"
    L" --add-opens java.base/sun.nio.ch=ALL-UNNAMED"
    L" --add-opens java.management/sun.management=ALL-UNNAMED"
    L" --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED";

static const wchar_t *APP_ARGS =
    L"-Dloader.path=lib/ -jar " APP_JAR
    L" --spring.config.location=optional:classpath:/,optional:file:./config/";

static void fail(const wchar_t *message, DWORD error) {
    fwprintf(stderr, L"HazelcastInspector: %ls", message);
    if (error != 0) {
        wchar_t *text = NULL;
        FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS,
                       NULL, error, 0, (LPWSTR)&text, 0, NULL);
        fwprintf(stderr, L" (error %lu: %ls)", error, text != NULL ? text : L"unknown");
        if (text != NULL) {
            LocalFree(text);
        }
    }
    fwprintf(stderr, L"\n");
}

static BOOL file_exists(const wchar_t *path) {
    DWORD attributes = GetFileAttributesW(path);
    return attributes != INVALID_FILE_ATTRIBUTES && !(attributes & FILE_ATTRIBUTE_DIRECTORY);
}

/* Directory of this executable, without trailing backslash. */
static BOOL executable_dir(wchar_t *buffer, DWORD capacity) {
    DWORD length = GetModuleFileNameW(NULL, buffer, capacity);
    if (length == 0 || length >= capacity) {
        return FALSE;
    }
    wchar_t *slash = wcsrchr(buffer, L'\\');
    if (slash == NULL) {
        return FALSE;
    }
    *slash = L'\0';
    return TRUE;
}

/* Bundled JRE, then JAVA_HOME, then PATH. */
static BOOL find_java(const wchar_t *app_dir, wchar_t *java, DWORD capacity) {
    if (_snwprintf(java, capacity, L"%ls\\jre\\bin\\java.exe", app_dir) > 0 && file_exists(java)) {
        return TRUE;
    }
    wchar_t java_home[MAX_PATH];
    DWORD home_length = GetEnvironmentVariableW(L"JAVA_HOME", java_home, MAX_PATH);
    if (home_length > 0 && home_length < MAX_PATH) {
        size_t end = wcslen(java_home);
        while (end > 0 && (java_home[end - 1] == L'\\' || java_home[end - 1] == L'/' || java_home[end - 1] == L'"')) {
            java_home[--end] = L'\0';
        }
        const wchar_t *home = java_home[0] == L'"' ? java_home + 1 : java_home;
        if (_snwprintf(java, capacity, L"%ls\\bin\\java.exe", home) > 0 && file_exists(java)) {
            return TRUE;
        }
    }
    DWORD found = SearchPathW(NULL, L"java.exe", NULL, capacity, java, NULL);
    return found > 0 && found < capacity;
}

/* Raw arguments of this process (everything after the program name), preserving quoting. */
static const wchar_t *launcher_arguments(void) {
    const wchar_t *cursor = GetCommandLineW();
    if (*cursor == L'"') {
        cursor++;
        while (*cursor != L'\0' && *cursor != L'"') {
            cursor++;
        }
        if (*cursor == L'"') {
            cursor++;
        }
    } else {
        while (*cursor != L'\0' && *cursor != L' ' && *cursor != L'\t') {
            cursor++;
        }
    }
    while (*cursor == L' ' || *cursor == L'\t') {
        cursor++;
    }
    return cursor;
}

static HANDLE child_process = NULL;

/*
 * Ctrl+C, Ctrl+Break and closing the window are delivered to every process attached to the
 * console, java included. The launcher ignores Ctrl+C / Ctrl+Break and keeps waiting so java can
 * run its shutdown hooks. On close / logoff / shutdown Windows ends the launcher after about five
 * seconds, so it waits up to 4.5 s for java to stop; the job object then kills whatever is left.
 */
static BOOL WINAPI on_console_event(DWORD event) {
    switch (event) {
        case CTRL_C_EVENT:
        case CTRL_BREAK_EVENT:
            return TRUE;
        case CTRL_CLOSE_EVENT:
        case CTRL_LOGOFF_EVENT:
        case CTRL_SHUTDOWN_EVENT:
            if (child_process != NULL) {
                WaitForSingleObject(child_process, 4500);
            }
            return FALSE;
        default:
            return FALSE;
    }
}

int wmain(void) {
    wchar_t app_dir[MAX_PATH];
    if (!executable_dir(app_dir, MAX_PATH)) {
        fail(L"cannot determine the launcher directory", GetLastError());
        return 1;
    }
    if (!SetCurrentDirectoryW(app_dir)) {
        fail(L"cannot change to the launcher directory", GetLastError());
        return 1;
    }

    wchar_t jar_path[MAX_PATH];
    _snwprintf(jar_path, MAX_PATH, L"%ls\\%ls", app_dir, APP_JAR);
    if (!file_exists(jar_path)) {
        fwprintf(stderr, L"HazelcastInspector: %ls not found next to the launcher.\n", APP_JAR);
        return 1;
    }

    wchar_t java[MAX_PATH];
    if (!find_java(app_dir, java, MAX_PATH)) {
        fwprintf(stderr, L"HazelcastInspector: Java 17 or newer is required.\n"
                         L"Install it, set JAVA_HOME, or unpack a JRE into %ls\\jre\n", app_dir);
        return 1;
    }

    wchar_t java_opts[8192] = L"";
    DWORD opts_length = GetEnvironmentVariableW(L"JAVA_OPTS", java_opts, 8192);
    if (opts_length >= 8192) {
        fail(L"JAVA_OPTS is too long", 0);
        return 1;
    }

    static wchar_t command_line[CMDLINE_CAPACITY];
    int written = _snwprintf(command_line, CMDLINE_CAPACITY, L"\"%ls\" %ls %ls %ls %ls %ls",
                             java, DEFAULT_JVM_OPTS, HAZELCAST_JVM_OPTS, java_opts, APP_ARGS, launcher_arguments());
    if (written < 0 || written >= CMDLINE_CAPACITY) {
        fail(L"command line is too long", 0);
        return 1;
    }

    /* Kill java together with the launcher, whatever ends the launcher. */
    HANDLE job = CreateJobObjectW(NULL, NULL);
    if (job != NULL) {
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits;
        ZeroMemory(&limits, sizeof(limits));
        limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        SetInformationJobObject(job, JobObjectExtendedLimitInformation, &limits, sizeof(limits));
    }
    SetConsoleCtrlHandler(on_console_event, TRUE);

    fwprintf(stdout, L"Starting Hazelcast Inspector with %ls\n", java);
    fflush(stdout);

    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    ZeroMemory(&startup, sizeof(startup));
    ZeroMemory(&process, sizeof(process));
    startup.cb = sizeof(startup);

    /* Same console, inherited stdout/stderr; suspended until it is placed in the job. */
    if (!CreateProcessW(java, command_line, NULL, NULL, TRUE, CREATE_SUSPENDED, NULL, app_dir, &startup, &process)) {
        fail(L"cannot start java.exe", GetLastError());
        return 1;
    }
    if (job != NULL && !AssignProcessToJobObject(job, process.hProcess)) {
        /* e.g. an outer job without breakaway; java still runs, only orphan protection is lost */
        CloseHandle(job);
        job = NULL;
    }
    child_process = process.hProcess;
    ResumeThread(process.hThread);
    CloseHandle(process.hThread);

    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exit_code = 1;
    GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hProcess);
    if (job != NULL) {
        CloseHandle(job);
    }
    return (int)exit_code;
}
