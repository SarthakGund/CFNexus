package com.cfduel.user;

import com.cfduel.auth.OAuth2SuccessHandler;
import com.cfduel.user.dto.UpdateProfileRequest;
import com.cfduel.user.dto.UserProfileDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth + Users REST endpoints (spec §6). Logout is handled by Spring Security
 * logout config in {@link com.cfduel.config.SecurityConfig}, not here.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** GET /api/auth/me — current user from session; 401 if not logged in. */
    @GetMapping("/auth/me")
    public ResponseEntity<UserProfileDto> me(HttpSession session) {
        UUID userId = currentUserId(session);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        User user = userService.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return ResponseEntity.ok(UserProfileDto.from(user));
    }

    /** GET /api/users/{handle} — public profile by CF handle. */
    @GetMapping("/users/{handle}")
    public ResponseEntity<UserProfileDto> getByHandle(@PathVariable String handle) {
        User user = userService.findByHandle(handle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(UserProfileDto.from(user));
    }

    /** GET /api/users/search?q= — prefix search by handle. */
    @GetMapping("/users/search")
    public List<UserProfileDto> search(@RequestParam("q") String query) {
        return userService.search(query).stream()
                .map(UserProfileDto::from)
                .toList();
    }

    /** PATCH /api/users/me — update bio + favoriteLanguage for the current user. */
    @PatchMapping("/users/me")
    public ResponseEntity<UserProfileDto> updateMe(
            HttpSession session,
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = currentUserId(session);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        User updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(UserProfileDto.from(updated));
    }

    private static UUID currentUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(OAuth2SuccessHandler.SESSION_USER_ID);
        return (id instanceof UUID uuid) ? uuid : null;
    }
}
