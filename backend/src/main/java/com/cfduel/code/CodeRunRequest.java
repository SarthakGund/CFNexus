package com.cfduel.code;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/code/run} (spec §12).
 *
 * @param language one of {cpp, python, java, javascript, go, rust}
 * @param code     source code, max 64KB (65536 chars)
 * @param stdin    optional standard input fed to the program
 */
public record CodeRunRequest(
        @NotBlank
        @Pattern(regexp = "cpp|python|java|javascript|go|rust",
                message = "language must be one of cpp, python, java, javascript, go, rust")
        String language,

        @NotBlank
        @Size(max = 65536, message = "code must not exceed 64KB")
        String code,

        @Size(max = 65536, message = "stdin must not exceed 64KB")
        String stdin) {
}
