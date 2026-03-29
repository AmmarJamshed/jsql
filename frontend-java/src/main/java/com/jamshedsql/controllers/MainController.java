package com.jamshedsql.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.jamshedsql.services.ApiService;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML
    private Button btnDataLab;
    @FXML
    private Button btnQuery;
    @FXML
    private Button btnAi;
    @FXML
    private Button btnInsights;
    @FXML
    private Button btnActivity;
    @FXML
    private ScrollPane scrollDataLab;
    @FXML
    private ScrollPane scrollQuery;
    @FXML
    private ScrollPane scrollAi;
    @FXML
    private ScrollPane scrollInsights;
    @FXML
    private ScrollPane scrollActivity;
    @FXML
    private Label healthLabel;
    @FXML
    private ToggleButton toggleAi;
    @FXML
    private Label uploadStatus;
    @FXML
    @SuppressWarnings("rawtypes")
    private TableView previewTable;
    @FXML
    private TextArea sqlEditor;
    @FXML
    private ProgressIndicator sqlBusy;
    @FXML
    private Label sqlMessage;
    @FXML
    @SuppressWarnings("rawtypes")
    private TableView resultTable;
    @FXML
    private TextField nlPrompt;
    @FXML
    private ProgressIndicator nlBusy;
    @FXML
    private TextArea generatedSql;
    @FXML
    private Label nlMessage;
    @FXML
    @SuppressWarnings("rawtypes")
    private TableView nlResultTable;
    @FXML
    private Label docStatus;
    @FXML
    private TextField semanticQuery;
    @FXML
    private TextArea semanticResults;
    @FXML
    private ProgressIndicator insightBusy;
    @FXML
    private TextArea insightOutput;
    @FXML
    private TextArea activityLog;
    @FXML
    private StackPane loadingOverlay;

    private ApiService api;
    private Timeline activityTimeline;
    private final List<String> lastColumns = new ArrayList<>();
    private final List<Map<String, Object>> lastRows = new ArrayList<>();

    public void setApiService(ApiService api) {
        this.api = api;
    }

    @FXML
    private void initialize() {
        selectNav(btnDataLab);
        showDataLab();
        runAsync(() -> {
            for (int attempt = 0; attempt < 8; attempt++) {
                try {
                    JsonNode h = api.health();
                    boolean ollama = h.path("ollama").path("reachable").asBoolean(false);
                    double ram = h.path("system").path("ram_gb").asDouble(0);
                    JsonNode models = h.path("ollama").path("models");
                    int n = models.isArray() ? models.size() : 0;
                    String ollamaLine = !ollama
                            ? "down — install/run Ollama locally (ollama.com)"
                            : (n > 0 ? "local · " + n + " model(s)" : "local · pull: ollama pull llama3.2");
                    String msg = String.format("Backend OK · %.1f GB RAM · Ollama: %s", ram, ollamaLine);
                    Platform.runLater(() -> healthLabel.setText(msg));
                    return;
                } catch (Exception ignored) {
                    if (attempt < 7) {
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            Platform.runLater(() -> healthLabel.setText("Backend offline — start Python API on :8765"));
        });
        startActivityPolling();
    }

    private void startActivityPolling() {
        activityTimeline = new Timeline(new KeyFrame(Duration.millis(1000), e -> pullActivityLog()));
        activityTimeline.setCycleCount(Timeline.INDEFINITE);
        activityTimeline.play();
    }

    private void pullActivityLog() {
        runAsync(() -> {
            try {
                JsonNode j = api.activityLog();
                JsonNode arr = j.path("lines");
                if (!arr.isArray()) {
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (JsonNode ln : arr) {
                    sb.append(ln.asText()).append('\n');
                }
                String text = sb.toString();
                Platform.runLater(() -> {
                    if (activityLog != null) {
                        activityLog.setText(text);
                        activityLog.positionCaret(text.length());
                        activityLog.setScrollTop(Double.MAX_VALUE);
                    }
                });
            } catch (Exception ignored) {
                // backend down — leave last text or empty
            }
        });
    }

    @FXML
    private void showDataLab() {
        selectNav(btnDataLab);
        switchTo(scrollDataLab);
    }

    @FXML
    private void showQuery() {
        selectNav(btnQuery);
        switchTo(scrollQuery);
    }

    @FXML
    private void showAi() {
        selectNav(btnAi);
        switchTo(scrollAi);
    }

    @FXML
    private void showInsights() {
        selectNav(btnInsights);
        switchTo(scrollInsights);
    }

    @FXML
    private void showActivity() {
        selectNav(btnActivity);
        switchTo(scrollActivity);
    }

    private void selectNav(Button active) {
        for (Button b : List.of(btnDataLab, btnQuery, btnAi, btnInsights, btnActivity)) {
            b.getStyleClass().remove("nav-btn-active");
        }
        if (!active.getStyleClass().contains("nav-btn-active")) {
            active.getStyleClass().add("nav-btn-active");
        }
    }

    private void switchTo(ScrollPane target) {
        for (ScrollPane p : List.of(scrollDataLab, scrollQuery, scrollAi, scrollInsights, scrollActivity)) {
            boolean on = p == target;
            p.setVisible(on);
            p.setManaged(on);
            if (on) {
                p.setOpacity(0);
                FadeTransition ft = new FadeTransition(Duration.millis(220), p);
                ft.setFromValue(0);
                ft.setToValue(1);
                ft.play();
            }
        }
    }

    @FXML
    private void onAiToggle() {
        boolean on = toggleAi.isSelected();
        toggleAi.setText(on ? "ON" : "OFF");
        runAsync(() -> {
            try {
                api.setAiMode(on);
            } catch (Exception ignored) {
                // backend may be down
            }
        });
    }

    @FXML
    private void onUploadCsv() {
        Window w = uploadStatus.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        var f = fc.showOpenDialog(w);
        if (f == null) {
            return;
        }
        setBusy(true);
        uploadStatus.setText("Uploading…");
        runAsync(() -> {
            try {
                JsonNode r = api.uploadCsv(f.toPath());
                Platform.runLater(() -> {
                    setBusy(false);
                    if (r.path("ok").asBoolean(false)) {
                        uploadStatus.setText("Loaded table `" + r.path("table").asText() + "` — " + r.path("rows").asInt() + " rows.");
                        fillTable(previewTable, r.path("preview"));
                    } else {
                        uploadStatus.setText("Upload failed.");
                        alert(Alert.AlertType.ERROR, "Upload", r.path("message").asText("Error"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    uploadStatus.setText("");
                    alert(Alert.AlertType.ERROR, "Upload", friendlyError(e));
                });
            }
        });
    }

    @FXML
    private void onRunSql() {
        String sql = sqlEditor.getText();
        sqlBusy.setVisible(true);
        sqlMessage.setText("");
        runAsync(() -> {
            try {
                JsonNode r = api.querySql(sql, null);
                Platform.runLater(() -> {
                    sqlBusy.setVisible(false);
                    if (r.path("ok").asBoolean(false)) {
                        sqlMessage.setText(r.path("truncated").asBoolean(false) ? "Results truncated to max rows." : "");
                        bindResultTable(resultTable, r);
                        captureLastResult(r);
                    } else {
                        sqlMessage.setText(r.path("message").asText("Query failed"));
                        clearTable(resultTable);
                        lastColumns.clear();
                        lastRows.clear();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    sqlBusy.setVisible(false);
                    sqlMessage.setText(friendlyError(e));
                });
            }
        });
    }

    @FXML
    private void onNlToSql() {
        String p = nlPrompt.getText() == null ? "" : nlPrompt.getText().trim();
        if (p.isEmpty()) {
            nlMessage.setText("Type a question first.");
            return;
        }
        nlBusy.setVisible(true);
        nlMessage.setText("");
        generatedSql.clear();
        runAsync(() -> {
            try {
                JsonNode r = api.nlToSql(p, null);
                Platform.runLater(() -> {
                    nlBusy.setVisible(false);
                    generatedSql.setText(r.path("sql").asText(""));
                    if (r.path("ok").asBoolean(false)) {
                        nlMessage.setText(r.path("truncated").asBoolean(false) ? "Truncated." : "");
                        bindResultTable(nlResultTable, r);
                        captureLastResult(r);
                    } else {
                        nlMessage.setText(r.path("message").asText("Could not run NL→SQL"));
                        clearTable(nlResultTable);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    nlBusy.setVisible(false);
                    nlMessage.setText(friendlyError(e));
                });
            }
        });
    }

    @FXML
    private void onUploadDoc() {
        Window w = docStatus.getScene().getWindow();
        FileChooser fc = new FileChooser();
        fc.setTitle("Text or PDF");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documents", "*.txt", "*.md", "*.pdf"),
                new FileChooser.ExtensionFilter("All", "*.*"));
        var f = fc.showOpenDialog(w);
        if (f == null) {
            return;
        }
        docStatus.setText("Ingesting…");
        runAsync(() -> {
            try {
                JsonNode r = api.uploadDocument(f.toPath());
                Platform.runLater(() -> {
                    if (r.path("ok").asBoolean(false)) {
                        docStatus.setText("Indexed " + r.path("chunks").asInt() + " chunks from " + r.path("source").asText());
                    } else {
                        docStatus.setText(r.path("message").asText("Failed"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> docStatus.setText(friendlyError(e)));
            }
        });
    }

    @FXML
    private void onConvertPdfToCsv() {
        Window w = docStatus.getScene().getWindow();
        FileChooser open = new FileChooser();
        open.setTitle("Choose PDF to convert to CSV");
        open.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        var pdfFile = open.showOpenDialog(w);
        if (pdfFile == null) {
            return;
        }
        FileChooser save = new FileChooser();
        save.setTitle("Save CSV file");
        String base = pdfFile.getName().replaceAll("(?i)\\.pdf$", "");
        save.setInitialFileName(base + ".csv");
        save.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        var outFile = save.showSaveDialog(w);
        if (outFile == null) {
            return;
        }
        docStatus.setText("Converting PDF → CSV…");
        Path pdfPath = pdfFile.toPath();
        Path csvPath = outFile.toPath();
        runAsync(() -> {
            try {
                ApiService.PdfCsvResult meta = api.convertPdfToCsv(pdfPath, csvPath);
                Platform.runLater(() -> {
                    docStatus.setText("Saved " + outFile.getName() + " (" + meta.mode() + ", ~" + meta.rowCount() + " data rows)");
                    ButtonType load = new ButtonType("Load into Data Lab", ButtonBar.ButtonData.OK_DONE);
                    ButtonType skip = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);
                    Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Load this CSV into DuckDB now?", load, skip);
                    a.setTitle("Import CSV?");
                    a.setHeaderText(null);
                    Optional<ButtonType> choice = a.showAndWait();
                    if (choice.isPresent() && choice.get() == load) {
                        importCsvToDataLab(csvPath);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> docStatus.setText(friendlyError(e)));
            }
        });
    }

    private void importCsvToDataLab(Path csvPath) {
        setBusy(true);
        runAsync(() -> {
            try {
                JsonNode r = api.uploadCsv(csvPath);
                Platform.runLater(() -> {
                    setBusy(false);
                    showDataLab();
                    if (r.path("ok").asBoolean(false)) {
                        uploadStatus.setText("Loaded table `" + r.path("table").asText() + "` — "
                                + r.path("rows").asInt() + " rows (from PDF → CSV).");
                        fillTable(previewTable, r.path("preview"));
                    } else {
                        alert(Alert.AlertType.ERROR, "Import", r.path("message").asText("Failed"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setBusy(false);
                    alert(Alert.AlertType.ERROR, "Import", friendlyError(e));
                });
            }
        });
    }

    @FXML
    private void onSemanticSearch() {
        String q = semanticQuery.getText() == null ? "" : semanticQuery.getText().trim();
        if (q.isEmpty()) {
            semanticResults.setText("Enter a search query.");
            return;
        }
        runAsync(() -> {
            try {
                JsonNode r = api.semanticSearch(q, 8);
                Platform.runLater(() -> {
                    StringBuilder sb = new StringBuilder();
                    JsonNode arr = r.path("results");
                    if (arr.isArray()) {
                        for (JsonNode x : arr) {
                            sb.append(String.format("[%.3f] %s — %s%n%n",
                                    x.path("score").asDouble(),
                                    x.path("source").asText(),
                                    x.path("text").asText()));
                        }
                    }
                    if (sb.isEmpty()) {
                        sb.append(r.path("message").asText("No hits."));
                    }
                    semanticResults.setText(sb.toString());
                });
            } catch (Exception e) {
                Platform.runLater(() -> semanticResults.setText(friendlyError(e)));
            }
        });
    }

    @FXML
    private void onExplainDataset() {
        insightBusy.setVisible(true);
        runAsync(() -> {
            try {
                JsonNode r = api.explainDataset();
                Platform.runLater(() -> {
                    insightBusy.setVisible(false);
                    if (r.path("ok").asBoolean(false)) {
                        insightOutput.setText(r.path("text").asText());
                    } else {
                        insightOutput.setText(r.path("message").asText("AI error"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    insightBusy.setVisible(false);
                    insightOutput.setText(friendlyError(e));
                });
            }
        });
    }

    @FXML
    private void onSummarizeResults() {
        if (lastColumns.isEmpty()) {
            alert(Alert.AlertType.INFORMATION, "Summarize", "Run a query first so there are results to summarize.");
            return;
        }
        insightBusy.setVisible(true);
        runAsync(() -> {
            try {
                JsonNode r = api.summarizeResults(lastColumns, lastRows);
                Platform.runLater(() -> {
                    insightBusy.setVisible(false);
                    if (r.path("ok").asBoolean(false)) {
                        insightOutput.setText(r.path("text").asText());
                    } else {
                        insightOutput.setText(r.path("message").asText("AI error"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    insightBusy.setVisible(false);
                    insightOutput.setText(friendlyError(e));
                });
            }
        });
    }

    private void captureLastResult(JsonNode r) {
        lastColumns.clear();
        lastRows.clear();
        JsonNode cols = r.path("columns");
        if (cols.isArray()) {
            cols.forEach(c -> lastColumns.add(c.asText()));
        }
        JsonNode rows = r.path("rows");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                lastRows.add(jsonObjectToMap(row));
            }
        }
    }

    private static Map<String, Object> jsonObjectToMap(JsonNode row) {
        Map<String, Object> m = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = row.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            m.put(e.getKey(), jsonValue(e.getValue()));
        }
        return m;
    }

    private static Object jsonValue(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isInt()) {
            return n.asInt();
        }
        if (n.isLong()) {
            return n.asLong();
        }
        if (n.isDouble() || n.isFloat()) {
            return n.asDouble();
        }
        return n.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bindResultTable(TableView table, JsonNode r) {
        JsonNode cols = r.path("columns");
        JsonNode rows = r.path("rows");
        fillTableFromColumns(table, cols, rows);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void fillTable(TableView table, JsonNode previewRows) {
        if (!previewRows.isArray() || previewRows.isEmpty()) {
            clearTable(table);
            return;
        }
        JsonNode first = previewRows.get(0);
        List<String> names = new ArrayList<>();
        first.fieldNames().forEachRemaining(names::add);
        JsonNode cols = previewRows;
        fillTableFromColumnNames(table, names, cols);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void fillTableFromColumns(TableView table, JsonNode columnsNode, JsonNode rowsNode) {
        List<String> names = new ArrayList<>();
        if (columnsNode != null && columnsNode.isArray()) {
            columnsNode.forEach(c -> names.add(c.asText()));
        }
        fillTableFromColumnNames(table, names, rowsNode);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void fillTableFromColumnNames(TableView table, List<String> names, JsonNode rowsNode) {
        table.getColumns().clear();
        table.setItems(FXCollections.observableArrayList());
        if (names.isEmpty()) {
            return;
        }
        for (String name : names) {
            TableColumn<Map<String, Object>, String> col = new TableColumn<>(name);
            col.setCellValueFactory(cd -> {
                Map<String, Object> m = cd.getValue();
                Object v = m == null ? null : m.get(name);
                return new SimpleStringProperty(v == null ? "" : String.valueOf(v));
            });
            table.getColumns().add(col);
        }
        List<Map<String, Object>> data = new ArrayList<>();
        if (rowsNode != null && rowsNode.isArray()) {
            for (JsonNode row : rowsNode) {
                data.add(jsonObjectToMap(row));
            }
        }
        table.setItems(FXCollections.observableArrayList(data));
    }

    @SuppressWarnings("rawtypes")
    private void clearTable(TableView table) {
        table.getColumns().clear();
        table.setItems(FXCollections.observableArrayList());
    }

    private void setBusy(boolean on) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(on);
            loadingOverlay.setManaged(on);
        }
    }

    private void runAsync(Runnable work) {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() {
                work.run();
                return null;
            }
        };
        new Thread(t, "jsql-bg").start();
    }

    private static void alert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private static String friendlyError(Throwable e) {
        if (e instanceof IOException) {
            return e.getMessage() == null ? "Network error" : e.getMessage();
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
