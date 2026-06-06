package com.cfduel.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient.Version;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class CodeRunService {

    private static final int COMPILE_TIMEOUT_MS = 30_000;
    private static final int RUN_TIMEOUT_MS = 10_000;

    /** Language key -> [pistonLanguage, sourceFileName] */
    private static final Map<String, String[]> LANGUAGE_META = Map.of(
            "cpp",        new String[]{"c++",         "main.cpp"},
            "python",     new String[]{"python",      "main.py"},
            "java",       new String[]{"java",        "Main.java"},
            "javascript", new String[]{"javascript",  "main.js"},
            "go",         new String[]{"go",          "main.go"},
            "rust",       new String[]{"rust",        "main.rs"});

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String executeUrl;

    public CodeRunService(
            ObjectMapper objectMapper,
            @Value("${app.code-runner.url}") String runnerUrl) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().version(Version.HTTP_1_1).build();
        this.executeUrl = runnerUrl + "/api/v2/execute";
    }

    public CodeRunResponse run(CodeRunRequest request) {
        String[] meta = LANGUAGE_META.get(request.language());
        if (meta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("language", meta[0]);
        body.put("version", "*");
        body.put("stdin", request.stdin() == null ? "" : request.stdin());
        body.put("run_timeout", RUN_TIMEOUT_MS);
        body.put("compile_timeout", COMPILE_TIMEOUT_MS);

        ArrayNode files = body.putArray("files");
        files.addObject()
                .put("name", meta[1])
                .put("content", request.code());

        String bodyJson = body.toString();
        log.debug("Piston request: {}", bodyJson);

        JsonNode result;
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(executeUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 400) {
                log.warn("Piston rejected: {} body={}", response.statusCode(), response.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Code runner unavailable");
            }
            result = objectMapper.readTree(response.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Piston call failed: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Code runner unavailable");
        }

        return mapResult(result);
    }

    private CodeRunResponse mapResult(JsonNode result) {
        JsonNode compile = result.path("compile");
        JsonNode run = result.path("run");

        String stdout = textOrEmpty(run, "stdout");
        String stderr = textOrEmpty(run, "stderr");

        if (!compile.isMissingNode()) {
            String compileStdout = textOrEmpty(compile, "stdout");
            String compileStderr = textOrEmpty(compile, "stderr");
            String compileOut = (compileStdout + compileStderr).trim();
            if (!compileOut.isEmpty() && stdout.isBlank() && stderr.isBlank()) {
                stderr = compileOut;
            } else if (!compileStderr.isBlank()) {
                stderr = compileStderr.isBlank() ? stderr : compileStderr + (stderr.isBlank() ? "" : "\n" + stderr);
            }
        }

        if (stdout.isBlank() && stderr.isBlank()) {
            String signal = textOrEmpty(run, "signal");
            if (!signal.isBlank()) stderr = signal;
        }

        Integer exitCode = run.path("code").isNumber() ? run.path("code").asInt() : null;

        Long executionTimeMs = null;
        JsonNode wallTime = run.path("wall_time");
        if (!wallTime.isMissingNode() && !wallTime.isNull()) {
            try {
                executionTimeMs = (long) wallTime.asDouble();
            } catch (Exception ignored) {}
        }

        return new CodeRunResponse(stdout, stderr, exitCode, executionTimeMs);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? "" : f.asText();
    }
}
