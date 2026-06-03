package com.cfduel.code;

import com.cfduel.auth.OAuth2SuccessHandler;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Code-run proxy endpoint (spec §12). Authenticated; rate-limited to 10 runs/min
 * per user. Validation (language enum, ≤64KB code) is enforced by jakarta
 * constraints on {@link CodeRunRequest} (400 on violation).
 */
@RestController
@RequestMapping("/api/code")
@RequiredArgsConstructor
public class CodeRunController {

    private final CodeRunService codeRunService;
    private final CodeRunRateLimiter rateLimiter;

    /** POST /api/code/run — run code via the sandboxed Judge0 instance. */
    @PostMapping("/run")
    public ResponseEntity<CodeRunResponse> run(
            HttpSession session,
            @Valid @RequestBody CodeRunRequest request) {
        UUID userId = requireUserId(session);
        if (!rateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Run limit reached (10/min). Try again shortly.");
        }
        return ResponseEntity.ok(codeRunService.run(request));
    }

    private static UUID requireUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(OAuth2SuccessHandler.SESSION_USER_ID);
        if (id instanceof UUID uuid) {
            return uuid;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
}
