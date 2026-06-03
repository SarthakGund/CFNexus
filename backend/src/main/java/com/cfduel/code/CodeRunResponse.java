package com.cfduel.code;

/**
 * Result of a code run (spec §12).
 *
 * @param stdout          program standard output
 * @param stderr          program standard error, with any compile output prepended
 * @param exitCode        process exit code, or null if unavailable
 * @param executionTimeMs wall/cpu time in milliseconds, or null if unavailable
 */
public record CodeRunResponse(
        String stdout,
        String stderr,
        Integer exitCode,
        Long executionTimeMs) {
}
