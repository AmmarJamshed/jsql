package com.jamshedsql.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * On first open, offers (with explicit consent) to add a Windows Desktop shortcut.
 * Product rule: never create a desktop icon without the user choosing Yes.
 */
public final class FirstLaunchShortcut {

    private static final String PREF_NODE = "com/jamshedsql/desktop";
    private static final String KEY_PROMPT_DONE = "shortcut_prompt_answered";

    private FirstLaunchShortcut() {}

    public static void offerAfterShown(Stage stage) {
        if (!isWindows()) {
            return;
        }
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        if (prefs.getBoolean(KEY_PROMPT_DONE, false)) {
            return;
        }
        Platform.runLater(() -> showDialog(stage, prefs));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void showDialog(Stage stage, Preferences prefs) {
        ButtonType yes = new ButtonType("Yes, add Desktop icon", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("Not now", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Add a JSQL shortcut to your Windows Desktop? You can delete it anytime.\n\n"
                        + "We only create it if you click Yes — not without your permission.",
                yes, no);
        alert.setTitle("Desktop shortcut");
        alert.setHeaderText("Optional desktop icon");
        alert.initOwner(stage);
        Optional<ButtonType> result = alert.showAndWait();
        prefs.putBoolean(KEY_PROMPT_DONE, true);
        if (result.isEmpty() || result.get().getButtonData() != ButtonBar.ButtonData.YES) {
            return;
        }
        try {
            Path script = resolveLauncherScript();
            if (script == null || !Files.isRegularFile(script)) {
                warn(stage, "Could not find scripts\\Open-JSQL-Desktop.ps1. Create a shortcut manually to that file.");
                return;
            }
            Path desktop = Path.of(System.getProperty("user.home"), "Desktop");
            if (!Files.isDirectory(desktop)) {
                warn(stage, "Desktop folder not found: " + desktop);
                return;
            }
            Path lnk = desktop.resolve("JSQL.lnk");
            DesktopShortcutHelper.createShortcutToPowerShellScript(script, lnk);
            info(stage, "Shortcut created on your Desktop: JSQL.lnk");
        } catch (Exception e) {
            warn(stage, "Could not create shortcut: " + e.getMessage());
        }
    }

    /** Walk up from the running code location until ../scripts/Open-JSQL-Desktop.ps1 exists. */
    static Path resolveLauncherScript() {
        try {
            var loc = FirstLaunchShortcut.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc == null) {
                return fallbackDJsql();
            }
            Path code = Path.of(loc.toURI()).toAbsolutePath().normalize();
            Path d = Files.isRegularFile(code) ? code.getParent() : code;
            for (int i = 0; i < 14 && d != null; i++) {
                Path ps1 = d.resolve("scripts").resolve("Open-JSQL-Desktop.ps1");
                if (Files.isRegularFile(ps1)) {
                    return ps1;
                }
                d = d.getParent();
            }
        } catch (Exception ignored) {
            // ignore
        }
        return fallbackDJsql();
    }

    private static Path fallbackDJsql() {
        Path p = Path.of("D:/jsql/scripts/Open-JSQL-Desktop.ps1");
        return Files.isRegularFile(p) ? p : null;
    }

    private static void warn(Stage stage, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.initOwner(stage);
        a.show();
    }

    private static void info(Stage stage, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.initOwner(stage);
        a.show();
    }

    /** Development: show the prompt again next launch. */
    public static void resetPromptFlagForDevelopment() {
        Preferences.userRoot().node(PREF_NODE).remove(KEY_PROMPT_DONE);
    }
}
