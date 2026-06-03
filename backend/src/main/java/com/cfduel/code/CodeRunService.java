package com.cfduel.code;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proxies code-run requests to a sandboxed Judge0 instance (spec §12).
 *
 * <p>Maps the six supported languages to Judge0 {@code language_id}s, enforces a
 * 10s CPU/wall limit, and normalises the Judge0 response into a
 * {@link CodeRunResponse}. All outbound failures degrade to a clear 502 so the
 * duel room never hangs on a flaky runner.
 */
@Service
@Slf4j
public class CodeRunService {

    /** 10s per-execution limit (spec §12). */
    private static final double CPU_TIME_LIMIT_SEC = 10.0;
    private static final double WALL_TIME_LIMIT_SEC = 10.0;

    /** Language -> Judge0 language_id. Centralised per spec §12. */
    private static final Map<String, Integer> LANGUAGE_IDS = Map.of(
            "cpp", 54,        // C++ (GCC 9.2.0)
            "python", 71,     // Python (3.8.1)
            "java", 62,       // Java (OpenJDK 13.0.1)
            "javascript", 63, // JavaScript (Node.js 12.14.0)
            "go", 60,         // Go (1.13.5)
            "rust", 73);      // Rust (1.40.0)

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public CodeRunService(
            ObjectMapper objectMapper,
            @Value("${app.code-runner.url}") String runnerUrl,
            @Value("${app.code-runner.api-key}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.restClient = RestClient.builder().baseUrl(runnerUrl).build();
    }

    /**
     * Submits {@code request} to Judge0 synchronously ({@code wait=true}) and maps
     * the verdict back. Throws {@link ResponseStatusException} (400/502) on bad
     * language or runner failure.
     */
    public CodeRunResponse run(CodeRunRequest request) {
        Integer languageId = LANGUAGE_IDS.get(request.language());
        if (languageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("source_code", request.code());
        body.put("language_id", languageId);
        body.put("stdin", request.stdin() == null ? "" : request.stdin());
        body.put("cpu_time_limit", CPU_TIME_LIMIT_SEC);
        body.put("wall_time_limit", WALL_TIME_LIMIT_SEC);

        JsonNode result;
        try {
            String response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/submissions")
                            .queryParam("base64_encoded", "false")
                            .queryParam("wait", "true")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Auth-Token", apiKey)
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from code runner");
            }
            result = objectMapper.readTree(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Judge0 call failed: {}", e.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Code runner unavailable");
        }

        return mapResult(result);
    }

    /** Normalises a Judge0 submission result into {@link CodeRunResponse}. */
    private CodeRunResponse mapResult(JsonNode result) {
        String stdout = textOrEmpty(result, "stdout");
        String stderr = textOrEmpty(result, "stderr");
        String compileOutput = textOrEmpty(result, "compile_output");

        // Surface compile errors (which Judge0 reports separately) via stderr.
        if (!compileOutput.isBlank()) {
            stderr = stderr.isBlank() ? compileOutput : compileOutput + "\n" + stderr;
        }
        // Include a human-readable status message (e.g. "Time Limit Exceeded")
        // when the program produced no other diagnostic output.
        String statusDesc = textOrEmpty(result.path("status"), "description");
        if (stdout.isBlank() && stderr.isBlank() && !statusDesc.isBlank()
                && !"Accepted".equalsIgnoreCase(statusDesc)) {
            stderr = statusDesc;
        }

        Integer exitCode = result.path("exit_code").isNumber()
                ? result.path("exit_code").asInt() : null;

        Long executionTimeMs = null;
        JsonNode time = result.path("time");
        if (time.isNumber() || (time.isTextual() && !time.asText().isBlank())) {
            try {
                executionTimeMs = Math.round(Double.parseDouble(time.asText()) * 1000.0);
            } catch (NumberFormatException ignored) {
                // leave null
            }
        }

        return new CodeRunResponse(stdout, stderr, exitCode, executionTimeMs);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? "" : f.asText();
    }
}
