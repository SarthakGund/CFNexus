package com.cfduel.config.ratelimit;

import com.cfduel.auth.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the global per-user API rate limit (spec §11) on authenticated
 * requests. The current user's UUID is resolved from the HTTP session attribute
 * set by {@link OAuth2SuccessHandler}, matching how the REST controllers read it.
 *
 * <p>Anonymous requests (no {@code userId} in session) are <em>not</em> limited
 * here — public reads stay open and unauthenticated traffic is bounded by
 * Spring Security instead. When an authenticated user exceeds the limit the
 * request is rejected with HTTP 429, a {@code Retry-After} header, and a small
 * JSON body.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ApiRateLimitInterceptor implements HandlerInterceptor {

    private final ApiRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        UUID userId = currentUserId(request);
        if (userId == null) {
            // Unauthenticated request — nothing to limit per-user; let it through.
            return true;
        }
        if (rateLimiter.allow(userId)) {
            return true;
        }

        log.debug("api rate limit exceeded for user {}", userId);
        long retryAfter = rateLimiter.windowSeconds();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"rate_limit_exceeded\","
                        + "\"message\":\"Too many requests. Limit is " + rateLimiter.limit()
                        + " requests per " + retryAfter + " seconds.\","
                        + "\"retryAfterSeconds\":" + retryAfter + "}");
        return false;
    }

    /** Resolves the session {@code userId} the same way the REST controllers do. */
    private static UUID currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object id = session == null ? null : session.getAttribute(OAuth2SuccessHandler.SESSION_USER_ID);
        return (id instanceof UUID uuid) ? uuid : null;
    }
}
