package com.cfduel.cf;

import com.cfduel.cf.dto.CfRatingChange;
import com.cfduel.cf.dto.CfSubmission;
import com.cfduel.cf.dto.CfUserInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Thin client over the Codeforces REST API v1 (spec §10).
 *
 * <p>Enforces a minimum {@value #MIN_INTERVAL_MS}ms spacing between any two
 * outbound API calls via a process-wide lock, retries once on HTTP 429, and
 * caches solved-problem sets ({@code cf:solved:{handle}}, 5min) and per-rating
 * problem lists ({@code cf:problems:{rating}}, 1h) in Redis.
 *
 * <p>Requests are signed only when both {@code app.cf.api-key} and
 * {@code app.cf.api-secret} are configured; otherwise public endpoints are used.
 */
@Service
@Slf4j
public class CfApiClient {

    private static final String BASE_URL = "https://codeforces.com/api";
    private static final long MIN_INTERVAL_MS = 1100L;
    private static final long RETRY_BACKOFF_MS = 1500L;
    private static final Duration SOLVED_TTL = Duration.ofMinutes(5);
    private static final Duration PROBLEMS_TTL = Duration.ofHours(1);

    private final RestClient restClient = RestClient.create();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;

    /** Serialises all outbound calls and guards the spacing window. */
    private final ReentrantLock gate = new ReentrantLock(true);
    private volatile long lastCallTimestamp = 0L;

    public CfApiClient(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.cf.api-key:}") String apiKey,
            @Value("${app.cf.api-secret:}") String apiSecret) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Fetches a user's submissions. Always FRESH (uncached) so verdict polling
     * can detect new accepted submissions immediately.
     */
    public List<CfSubmission> getUserStatus(String handle, int count) {
        JsonNode result = call("/user.status", "handle", handle, "from", "1", "count", String.valueOf(count));
        List<CfSubmission> out = new ArrayList<>();
        if (result == null || !result.isArray()) {
            return out;
        }
        for (JsonNode node : result) {
            JsonNode problem = node.path("problem");
            out.add(new CfSubmission(
                    node.path("id").isMissingNode() ? null : node.path("id").asLong(),
                    textOrNull(node, "verdict"),
                    problem.path("contestId").isMissingNode() ? null : problem.path("contestId").asInt(),
                    textOrNull(problem, "index"),
                    node.path("creationTimeSeconds").isMissingNode() ? null : node.path("creationTimeSeconds").asLong()));
        }
        return out;
    }

    /**
     * Returns the set of problem keys the handle has solved (verdict OK),
     * cached under {@code cf:solved:{handle}} for 5 minutes.
     */
    public Set<String> getSolvedProblemKeys(String handle) {
        String cacheKey = "cf:solved:" + handle;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return new HashSet<>(objectMapper.readValue(cached, new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.warn("Failed to parse cached solved set for {}: {}", handle, e.toString());
            }
        }
        Set<String> solved = new HashSet<>();
        for (CfSubmission sub : getUserStatus(handle, 1000)) {
            if ("OK".equals(sub.verdict()) && sub.contestId() != null && sub.index() != null) {
                solved.add(sub.problemKey());
            }
        }
        writeCache(cacheKey, new ArrayList<>(solved), SOLVED_TTL);
        return solved;
    }

    /**
     * Returns all problems with the exact given rating, joined with their
     * solved counts. Cached under {@code cf:problems:{rating}} for 1 hour.
     */
    public List<CfProblem> getProblemsByRating(int rating) {
        String cacheKey = "cf:problems:" + rating;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new TypeReference<List<CfProblem>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse cached problems for rating {}: {}", rating, e.toString());
            }
        }

        JsonNode result = call("/problemset.problems");
        List<CfProblem> out = new ArrayList<>();
        if (result == null) {
            return out;
        }
        JsonNode problems = result.path("problems");
        JsonNode stats = result.path("problemStatistics");

        // Build solvedCount lookup keyed by problemKey from problemStatistics.
        java.util.Map<String, Long> solvedCounts = new java.util.HashMap<>();
        if (stats.isArray()) {
            for (JsonNode s : stats) {
                if (s.path("contestId").isMissingNode() || s.path("index").isMissingNode()) {
                    continue;
                }
                solvedCounts.put(
                        s.path("contestId").asInt() + "-" + s.path("index").asText(),
                        s.path("solvedCount").isMissingNode() ? null : s.path("solvedCount").asLong());
            }
        }

        if (problems.isArray()) {
            for (JsonNode p : problems) {
                if (p.path("rating").isMissingNode() || p.path("rating").asInt() != rating) {
                    continue;
                }
                Integer contestId = p.path("contestId").isMissingNode() ? null : p.path("contestId").asInt();
                String index = textOrNull(p, "index");
                String key = contestId + "-" + index;
                out.add(new CfProblem(contestId, index, textOrNull(p, "name"), rating, solvedCounts.get(key)));
            }
        }
        writeCache(cacheKey, out, PROBLEMS_TTL);
        return out;
    }

    /** Minimal user info lookup via {@code /user.info}. */
    public Optional<CfUserInfo> getUserInfo(String handle) {
        JsonNode result = call("/user.info", "handles", handle);
        if (result == null || !result.isArray() || result.isEmpty()) {
            return Optional.empty();
        }
        JsonNode u = result.get(0);
        return Optional.of(new CfUserInfo(
                textOrNull(u, "handle"),
                u.path("rating").isMissingNode() ? null : u.path("rating").asInt(),
                u.path("maxRating").isMissingNode() ? null : u.path("maxRating").asInt(),
                textOrNull(u, "rank"),
                textOrNull(u, "maxRank"),
                textOrNull(u, "titlePhoto")));
    }

    /** Rating history via {@code /user.rating}; reserved for Phase 3. */
    public List<CfRatingChange> getUserRating(String handle) {
        JsonNode result = call("/user.rating", "handle", handle);
        List<CfRatingChange> out = new ArrayList<>();
        if (result == null || !result.isArray()) {
            return out;
        }
        for (JsonNode r : result) {
            out.add(new CfRatingChange(
                    r.path("contestId").isMissingNode() ? null : r.path("contestId").asInt(),
                    r.path("ratingUpdateTimeSeconds").isMissingNode() ? null : r.path("ratingUpdateTimeSeconds").asLong(),
                    r.path("newRating").isMissingNode() ? null : r.path("newRating").asInt(),
                    r.path("oldRating").isMissingNode() ? null : r.path("oldRating").asInt()));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Performs a rate-gated GET against the CF API and returns the {@code result}
     * node, or {@code null} if the response was FAILED/unparseable. {@code params}
     * is a flat key,value,key,value array of query parameters.
     */
    private JsonNode call(String path, String... params) {
        gate.lock();
        try {
            throttle();
            try {
                return execute(path, params);
            } catch (HttpClientErrorException.TooManyRequests e) {
                log.warn("CF API 429 on {}, retrying once after {}ms", path, RETRY_BACKOFF_MS);
                sleep(RETRY_BACKOFF_MS);
                lastCallTimestamp = System.currentTimeMillis();
                try {
                    return execute(path, params);
                } catch (Exception retryEx) {
                    log.warn("CF API retry failed on {}: {}", path, retryEx.toString());
                    return null;
                }
            } catch (Exception e) {
                log.warn("CF API call failed on {}: {}", path, e.toString());
                return null;
            }
        } finally {
            lastCallTimestamp = System.currentTimeMillis();
            gate.unlock();
        }
    }

    private JsonNode execute(String path, String... params) throws Exception {
        String url = buildUrl(path, params);
        String body = restClient.get().uri(url).retrieve().body(String.class);
        if (body == null) {
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        if (!"OK".equals(root.path("status").asText())) {
            log.warn("CF API FAILED on {}: {}", path, root.path("comment").asText(""));
            return null;
        }
        return root.path("result");
    }

    /** Builds the full URL, appending an API signature when credentials exist. */
    private String buildUrl(String path, String... params) {
        String methodName = path.startsWith("/") ? path.substring(1) : path;
        // Collect params (param sorting is only required for signing).
        List<String[]> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < params.length; i += 2) {
            pairs.add(new String[] {params[i], params[i + 1]});
        }

        boolean sign = !apiKey.isEmpty() && !apiSecret.isEmpty();
        if (sign) {
            String time = String.valueOf(System.currentTimeMillis() / 1000L);
            pairs.add(new String[] {"apiKey", apiKey});
            pairs.add(new String[] {"time", time});
            String rand = randomToken();
            String sig = computeSignature(methodName, pairs, rand);
            StringBuilder sb = new StringBuilder(BASE_URL).append(path).append('?').append(encodePairs(pairs));
            sb.append("&apiSig=").append(rand).append(sig);
            return sb.toString();
        }
        if (pairs.isEmpty()) {
            return BASE_URL + path;
        }
        return BASE_URL + path + "?" + encodePairs(pairs);
    }

    private String encodePairs(List<String[]> pairs) {
        StringBuilder sb = new StringBuilder();
        for (String[] p : pairs) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(p[0])).append('=').append(urlEncode(p[1]));
        }
        return sb.toString();
    }

    /**
     * Computes the Codeforces {@code apiSig} hash per their signing scheme:
     * {@code sha512(rand + "/" + method + "?" + sortedParams + "#" + secret)}.
     */
    private String computeSignature(String methodName, List<String[]> pairs, String rand) {
        List<String[]> sorted = new ArrayList<>(pairs);
        sorted.sort((a, b) -> {
            int c = a[0].compareTo(b[0]);
            return c != 0 ? c : a[1].compareTo(b[1]);
        });
        StringBuilder query = new StringBuilder();
        for (String[] p : sorted) {
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(urlEncode(p[0])).append('=').append(urlEncode(p[1]));
        }
        String toHash = rand + "/" + methodName + "?" + query + "#" + apiSecret;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(toHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute CF API signature", e);
        }
    }

    private String randomToken() {
        StringBuilder sb = new StringBuilder(6);
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Blocks until at least {@link #MIN_INTERVAL_MS} has passed since the last call. */
    private void throttle() {
        long elapsed = System.currentTimeMillis() - lastCallTimestamp;
        long wait = MIN_INTERVAL_MS - elapsed;
        if (wait > 0) {
            sleep(wait);
        }
    }

    private void writeCache(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Failed to write cache {}: {}", key, e.toString());
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode f = node.path(field);
        return f.isMissingNode() || f.isNull() ? null : f.asText();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
