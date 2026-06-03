package com.cfduel.user;

import com.cfduel.user.dto.UpdateProfileRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int SEARCH_LIMIT = 20;

    private final UserRepository userRepository;

    /**
     * Carrier for the Codeforces profile fields extracted during OAuth login.
     * Only {@code handle} is required; the rest (including {@code cfUserId}, which
     * Codeforces no longer exposes) may be null.
     */
    public record OAuthUserInfo(
            String handle,
            Long cfUserId,
            Integer cfRating,
            Integer cfMaxRating,
            String cfRank,
            String cfMaxRank,
            String avatarUrl) {
    }

    /**
     * Creates a new user (with spec §5 defaults) or refreshes the CF profile
     * fields of an existing one. Matched first by CF user id, then by handle.
     */
    @Transactional
    public User upsertFromOAuth(OAuthUserInfo info) {
        User user = null;
        if (info.cfUserId() != null) {
            user = userRepository.findByCfUserId(info.cfUserId()).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByCfHandleIgnoreCase(info.handle()).orElse(null);
        }

        if (user == null) {
            user = User.builder()
                    .cfHandle(info.handle())
                    .cfUserId(info.cfUserId())
                    .cfRating(info.cfRating())
                    .cfMaxRating(info.cfMaxRating())
                    .cfRank(info.cfRank())
                    .cfMaxRank(info.cfMaxRank())
                    .avatarUrl(info.avatarUrl())
                    .isOnline(true)
                    .lastSeen(java.time.Instant.now())
                    .build();
            // Remaining numeric defaults are applied in @PrePersist.
            return userRepository.save(user);
        }

        // Update mutable CF profile fields on existing user.
        user.setCfHandle(info.handle());
        if (info.cfUserId() != null) {
            user.setCfUserId(info.cfUserId());
        }
        if (info.cfRating() != null) {
            user.setCfRating(info.cfRating());
        }
        if (info.cfMaxRating() != null) {
            user.setCfMaxRating(info.cfMaxRating());
        }
        if (info.cfRank() != null) {
            user.setCfRank(info.cfRank());
        }
        if (info.cfMaxRank() != null) {
            user.setCfMaxRank(info.cfMaxRank());
        }
        if (info.avatarUrl() != null) {
            user.setAvatarUrl(info.avatarUrl());
        }
        user.setIsOnline(true);
        user.setLastSeen(java.time.Instant.now());
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByHandle(String handle) {
        return userRepository.findByCfHandleIgnoreCase(handle);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    @Transactional(readOnly = true)
    public List<User> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return userRepository.findByCfHandleStartingWithIgnoreCase(
                query.trim(), PageRequest.of(0, SEARCH_LIMIT));
    }

    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getCurrentUser(userId);
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        if (request.favoriteLanguage() != null) {
            user.setFavoriteLang(request.favoriteLanguage());
        }
        return userRepository.save(user);
    }
}
