package com.cfduel.config;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps Bean Validation failures on method parameters (e.g. a {@code @Pattern}
 * {@code @PathVariable}/{@code @RequestParam} on a {@code @Validated} controller)
 * to HTTP 400 (spec §11). Spring already returns 400 for {@code @Valid @RequestBody}
 * via {@code MethodArgumentNotValidException}, but a {@link ConstraintViolationException}
 * raised by parameter-level validation would otherwise surface as a 500 — this
 * narrow advice corrects only that case and does not catch any other exception type.
 */
@Slf4j
@RestControllerAdvice
public class ValidationExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> onConstraintViolation(ConstraintViolationException ex) {
        log.debug("request rejected by validation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "validation_failed",
                        "message", ex.getMessage()));
    }
}
