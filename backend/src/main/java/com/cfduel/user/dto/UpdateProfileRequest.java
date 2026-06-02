package com.cfduel.user.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/users/me}. Both fields are optional; a
 * null field leaves the existing value untouched.
 */
public record UpdateProfileRequest(
        @Size(max = 2000, message = "bio must be at most 2000 characters")
        String bio,

        @Size(max = 20, message = "favoriteLanguage must be at most 20 characters")
        String favoriteLanguage) {
}
