package com.jamshedsql.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST client for JSQL FastAPI. All calls use OkHttp so JSON bodies and multipart
 * are encoded reliably (java.net.http.HttpClient can omit POST bodies in some setups).
 */
public final class ApiService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient okHttp = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private String baseUrl;

    public ApiService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public JsonNode health() throws IOException {
        return getJson("/health");
    }

    /** Live backend log lines for the Activity tab. */
    public JsonNode activityLog() throws IOException {
        return getJson("/activity_log");
    }

    public JsonNode uploadCsv(Path file) throws IOException {
        byte[] body = Files.readAllBytes(file);
        String filename = file.getFileName().toString();
        RequestBody fileBody = RequestBody.create(MediaType.parse("text/csv"), body);
        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileBody)
                .build();
        return executeMultipart("/upload_csv", multipart);
    }

    public JsonNode querySql(String sql, Integer maxRows) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sql", sql);
        if (maxRows != null) {
            m.put("max_rows", maxRows);
        }
        return postJson("/query_sql", m);
    }

    public JsonNode nlToSql(String prompt, Integer maxRows) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prompt", prompt);
        if (maxRows != null) {
            m.put("max_rows", maxRows);
        }
        return postJson("/nl_to_sql", m);
    }

    public JsonNode uploadDocument(Path file) throws IOException {
        byte[] body = Files.readAllBytes(file);
        String name = file.getFileName().toString().toLowerCase();
        String ct = name.endsWith(".pdf") ? "application/pdf" : "text/plain; charset=utf-8";
        RequestBody fileBody = RequestBody.create(MediaType.parse(ct), body);
        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getFileName().toString(), fileBody)
                .build();
        return executeMultipart("/upload_text", multipart);
    }

    public JsonNode semanticSearch(String query, int topK) throws IOException {
        Map<String, Object> m = Map.of("query", query, "top_k", topK);
        return postJson("/semantic_search", m);
    }

    public JsonNode explainDataset() throws IOException {
        return postJson("/ai/explain_dataset", Map.of());
    }

    public JsonNode summarizeResults(List<String> columns, List<Map<String, Object>> rows) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("columns", columns);
        m.put("rows", rows);
        return postJson("/ai/summarize_results", m);
    }

    public JsonNode setAiMode(boolean enabled) throws IOException {
        return postJson("/ai/mode", Map.of("enabled", enabled));
    }

    /** POST PDF multipart; writes CSV response to {@code outputCsv}. */
    public PdfCsvResult convertPdfToCsv(Path pdfPath, Path outputCsv) throws IOException {
        byte[] pdfBytes = Files.readAllBytes(pdfPath);
        RequestBody fileBody = RequestBody.create(MediaType.parse("application/pdf"), pdfBytes);
        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", pdfPath.getFileName().toString(), fileBody)
                .build();
        Request request = new Request.Builder()
                .url(baseUrl + "/convert_pdf_csv")
                .post(multipart)
                .build();
        try (Response response = okHttp.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("HTTP " + response.code() + ": " + err);
            }
            byte[] body = response.body() != null ? response.body().bytes() : new byte[0];
            Files.write(outputCsv, body);
            String mode = response.header("X-JSQL-PDF-Mode");
            String rows = response.header("X-JSQL-Row-Count");
            return new PdfCsvResult(mode != null ? mode : "", rows != null ? rows : "");
        }
    }

    public record PdfCsvResult(String mode, String rowCount) {}

    private JsonNode getJson(String path) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .get()
                .build();
        return execute(request);
    }

    private JsonNode postJson(String path, Object body) throws IOException {
        String json = mapper.writeValueAsString(body);
        RequestBody rb = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(rb)
                .header("Content-Type", "application/json; charset=utf-8")
                .build();
        return execute(request);
    }

    private JsonNode executeMultipart(String path, RequestBody multipartBody) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .post(multipartBody)
                .build();
        return execute(request);
    }

    private JsonNode execute(Request request) throws IOException {
        try (Response response = okHttp.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + respBody);
            }
            return mapper.readTree(respBody);
        }
    }
}
