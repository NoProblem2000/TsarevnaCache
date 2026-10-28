// HazelcastInspector.exe - C# variant of the native Windows launcher (alternative to launcher.c).
//
// Starts java.exe directly via Process/ProcessStartInfo with UseShellExecute = false (no cmd.exe,
// no PowerShell). Behaviour matches launcher.c and bin\start.bat:
//   - Java lookup: <exe dir>\jre\bin\java.exe, then %JAVA_HOME%\bin\java.exe, then java.exe on PATH
//   - Working directory: the directory of the executable
//   - JVM options as in bin\start.bat plus %JAVA_OPTS%; launcher arguments are passed to Spring Boot
//   - java shares this console (logs appear here); Ctrl+C reaches java directly and the launcher
//     waits for it, closing the window or ending the launcher stops java as well
//
// Build (requires the .NET 8 SDK, see HazelcastInspector.csproj):
//   dotnet publish src/launcher/HazelcastInspector.csproj -c Release -r win-x64 -o target/launcher-cs
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;

internal static class Program
{
    private const string AppJar = "hazelcast-inspector.jar";

    private static readonly string[] DefaultJvmOptions =
    {
        "-Xms256m", "-Xmx1024m", "-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError", "-Dfile.encoding=UTF-8",
    };

    // Module access recommended by Hazelcast for Java 17+.
    private static readonly string[] HazelcastJvmOptions =
    {
        "--add-modules", "java.se",
        "--add-exports", "java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.management/sun.management=ALL-UNNAMED",
        "--add-opens", "jdk.management/com.sun.management.internal=ALL-UNNAMED",
    };

    private static readonly string[] AppArguments =
    {
        "-Dloader.path=lib/", "-jar", AppJar,
        "--spring.config.location=optional:classpath:/,optional:file:./config/",
    };

    private static Process? _java;

    private static int Main(string[] args)
    {
        string appDir = AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
        Directory.SetCurrentDirectory(appDir);

        if (!File.Exists(Path.Combine(appDir, AppJar)))
        {
            Console.Error.WriteLine($"HazelcastInspector: {AppJar} not found next to the launcher.");
            return 1;
        }

        string? java = FindJava(appDir);
        if (java == null)
        {
            Console.Error.WriteLine("HazelcastInspector: Java 17 or newer is required.");
            Console.Error.WriteLine($"Install it, set JAVA_HOME, or unpack a JRE into {Path.Combine(appDir, "jre")}");
            return 1;
        }

        var startInfo = new ProcessStartInfo(java)
        {
            UseShellExecute = false,      // CreateProcess directly, no shell
            WorkingDirectory = appDir,
            CreateNoWindow = false,       // inherit this console: logs appear here
        };
        foreach (string option in DefaultJvmOptions) startInfo.ArgumentList.Add(option);
        foreach (string option in HazelcastJvmOptions) startInfo.ArgumentList.Add(option);
        foreach (string option in SplitJavaOpts(Environment.GetEnvironmentVariable("JAVA_OPTS"))) startInfo.ArgumentList.Add(option);
        foreach (string argument in AppArguments) startInfo.ArgumentList.Add(argument);
        foreach (string argument in args) startInfo.ArgumentList.Add(argument);

        // Ctrl+C / Ctrl+Break reach java as well; keep the launcher alive while java shuts down.
        Console.CancelKeyPress += (_, e) => e.Cancel = true;
        // Window closed / launcher ending: give java a moment, then make sure it is gone.
        AppDomain.CurrentDomain.ProcessExit += (_, _) => StopJava(TimeSpan.FromSeconds(4));
        SetConsoleCtrlHandler(OnConsoleEvent, true);

        Console.WriteLine($"Starting Hazelcast Inspector with {java}");
        try
        {
            _java = Process.Start(startInfo);
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"HazelcastInspector: cannot start java.exe ({e.Message})");
            return 1;
        }
        if (_java == null)
        {
            return 1;
        }
        _java.WaitForExit();
        return _java.ExitCode;
    }

    private static string? FindJava(string appDir)
    {
        var candidates = new List<string> { Path.Combine(appDir, "jre", "bin", "java.exe") };
        string? javaHome = Environment.GetEnvironmentVariable("JAVA_HOME")?.Trim().Trim('"');
        if (!string.IsNullOrEmpty(javaHome))
        {
            candidates.Add(Path.Combine(javaHome, "bin", "java.exe"));
        }
        foreach (string dir in (Environment.GetEnvironmentVariable("PATH") ?? "").Split(Path.PathSeparator))
        {
            if (!string.IsNullOrWhiteSpace(dir))
            {
                candidates.Add(Path.Combine(dir.Trim().Trim('"'), "java.exe"));
            }
        }
        foreach (string candidate in candidates)
        {
            if (File.Exists(candidate))
            {
                return candidate;
            }
        }
        return null;
    }

    /// <summary>Splits JAVA_OPTS on whitespace, honouring double quotes.</summary>
    private static IEnumerable<string> SplitJavaOpts(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
        {
            yield break;
        }
        var current = new System.Text.StringBuilder();
        bool quoted = false;
        foreach (char c in value)
        {
            if (c == '"')
            {
                quoted = !quoted;
            }
            else if (char.IsWhiteSpace(c) && !quoted)
            {
                if (current.Length > 0)
                {
                    yield return current.ToString();
                    current.Clear();
                }
            }
            else
            {
                current.Append(c);
            }
        }
        if (current.Length > 0)
        {
            yield return current.ToString();
        }
    }

    private static void StopJava(TimeSpan grace)
    {
        Process? java = _java;
        if (java == null || java.HasExited)
        {
            return;
        }
        if (!java.WaitForExit((int)grace.TotalMilliseconds))
        {
            java.Kill(entireProcessTree: true);
        }
    }

    // ---- console close handling ------------------------------------------------------------

    private delegate bool ConsoleEventHandler(int eventType);

    private const int CtrlCloseEvent = 2;
    private const int CtrlLogoffEvent = 5;
    private const int CtrlShutdownEvent = 6;

    // Keep a reference so the delegate is not garbage collected while registered.
    private static readonly ConsoleEventHandler OnConsoleEvent = eventType =>
    {
        if (eventType is CtrlCloseEvent or CtrlLogoffEvent or CtrlShutdownEvent)
        {
            StopJava(TimeSpan.FromSeconds(4));
        }
        return false;
    };

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetConsoleCtrlHandler(ConsoleEventHandler handler, bool add);
}
