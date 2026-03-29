package com.jamshedsql.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the FastAPI backend without a visible console (Windows: pythonw.exe).
 * Live request logs appear in the app's Activity tab via {@code /activity_log}.
 */
public final class BackendLauncher {

    private static final String HEALTH_URL = System.getProperty("jsql.api", "http://127.0.0.1:8765") + "/health";
    private static final AtomicReference<Process> BACKEND = new AtomicReference<>();

    private BackendLauncher() {}

    /**
     * If the API (default {@code http://127.0.0.1:8765}) is not up, spawn uvicorn in the background (no window on Windows).
     * Non-blocking — runs on a daemon thread.
     */
    public static void startEmbeddedBackendIfNeeded() {
        Thread t = new Thread(BackendLauncher::bootstrap, "jsql-backend-bootstrap");
        t.setDaemon(true);
        t.start();
    }

    private static void bootstrap() {
        if (Boolean.parseBoolean(System.getProperty("jsql.skipBackend", "false"))) {
            return;
        }
        if (healthReachable()) {
            return;
        }
        Path backendRoot = resolveBackendRoot();
        Path python = resolvePythonNoWindow(backendRoot);
        if (python == null || !Files.isRegularFile(python)) {
            System.err.println("[JSQL] Backend not running and no venv python found under " + backendRoot
                    + ". Start manually: python -m uvicorn main:app --host 127.0.0.1 --port 8765");
            return;
        }
        if (!Files.isRegularFile(backendRoot.resolve("main.py"))) {
            System.err.println("[JSQL] main.py missing in " + backendRoot);
            return;
        }
        try {
            Path logFile = Files.createTempFile("jsql-backend-", ".log");
            ProcessBuilder pb = new ProcessBuilder(
                    python.toString(),
                    "-m", "uvicorn",
                    "main:app",
                    "--host", "127.0.0.1",
                    "--port", "8765");
            pb.directory(backendRoot.toFile());
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            Process p = pb.start();
            BACKEND.set(p);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Process proc = BACKEND.getAndSet(null);
                if (proc != null) {
                    proc.destroy();
                }
            }, "jsql-backend-shutdown"));
        } catch (IOException e) {
            System.err.println("[JSQL] Could not start embedded backend: " + e.getMessage());
        }
    }

    public static void shutdownEmbeddedBackend() {
        Process p = BACKEND.getAndSet(null);
        if (p != null) {
            p.destroy();
        }
    }

    private static boolean healthReachable() {
        try {
            HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(HEALTH_URL)).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = c.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path resolvePythonNoWindow(Path backendRoot) {
        Path venv = backendRoot.resolve(".venv");
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (win) {
            Path pythonw = venv.resolve("Scripts/pythonw.exe");
            if (Files.isRegularFile(pythonw)) {
                return pythonw;
            }
            Path py = venv.resolve("Scripts/python.exe");
            if (Files.isRegularFile(py)) {
                return py;
            }
        } else {
            Path bin = venv.resolve("bin/python3");
            if (Files.isRegularFile(bin)) {
                return bin;
            }
            bin = venv.resolve("bin/python");
            if (Files.isRegularFile(bin)) {
                return bin;
            }
        }
        return null;
    }

    static Path resolveBackendRoot() {
        String prop = System.getProperty("jsql.backend.home");
        if (prop != null && !prop.isBlank()) {
            Path p = Paths.get(prop).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("main.py"))) {
                return p;
            }
        }
        String env = System.getenv("JSQL_BACKEND_HOME");
        if (env != null && !env.isBlank()) {
            Path p = Paths.get(env).toAbsolutePath().normalize();
            if (Files.isRegularFile(p.resolve("main.py"))) {
                return p;
            }
        }
        List<Path> candidates = new ArrayList<>();
        try {
            var loc = BackendLauncher.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path code = Paths.get(loc.toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(code)) {
                    Path dir = code.getParent();
                    candidates.add(dir.resolve("backend-python"));
                    if (dir.getParent() != null) {
                        candidates.add(dir.getParent().resolve("backend-python"));
                    }
                } else {
                    candidates.add(code.resolve("backend-python"));
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("../backend-python").normalize());
        candidates.add(cwd.resolve("../../backend-python").normalize());
        candidates.add(Paths.get("D:/jsql/backend-python").normalize());

        for (Path c : candidates) {
            if (Files.isRegularFile(c.resolve("main.py"))) {
                return c;
            }
        }
        return cwd.resolve("../backend-python").normalize();
    }
}
