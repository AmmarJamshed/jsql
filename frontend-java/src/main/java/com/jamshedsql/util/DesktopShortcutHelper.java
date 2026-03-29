package com.jamshedsql.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Creates a Windows .lnk via a short-lived helper PowerShell script (reliable quoting).
 */
public final class DesktopShortcutHelper {

    private DesktopShortcutHelper() {}

    public static void createShortcutToPowerShellScript(Path scriptPs1, Path shortcutLnk) throws IOException, InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IOException("Desktop shortcuts are only supported on Windows.");
        }
        String lnkEsc = shortcutLnk.toAbsolutePath().toString().replace("'", "''");
        String scriptPath = scriptPs1.toAbsolutePath().toString();
        String workEsc = scriptPs1.getParent().toAbsolutePath().toString().replace("'", "''");
        String body = """
                $w = New-Object -ComObject WScript.Shell
                $s = $w.CreateShortcut('%s')
                $s.TargetPath = 'powershell.exe'
                $s.Arguments = '-NoProfile -ExecutionPolicy Bypass -File "%s"'
                $s.WorkingDirectory = '%s'
                $s.Description = 'JSQL (JamshedSQL)'
                $s.Save()
                """.formatted(lnkEsc, scriptPath, workEsc);
        Path tmp = Files.createTempFile("jsql-create-lnk-", ".ps1");
        try {
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File",
                    tmp.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(45, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Timeout creating shortcut");
            }
            if (p.exitValue() != 0) {
                throw new IOException("PowerShell exited with " + p.exitValue());
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
