package com.cfduel.achievement;

import com.cfduel.user.User;
import com.cfduel.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only achievement endpoints (spec §13). Serves the full catalogue and a
 * given user's earned set. Field names match what the frontend
 * {@code AchievementsGrid}/{@code AchievementBadge} expect: catalogue items
 * carry {@code code/name/description/icon}; earned items carry {@code code} and
 * {@code earnedAt}.
 */
@RestController
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserService userService;

    /** Public achievement catalogue. */
    public record AchievementDto(String code, String name, String description, String icon) {
        static AchievementDto from(Achievement a) {
            return new AchievementDto(a.getCode(), a.getName(), a.getDescription(), a.getIcon());
        }
    }

    /** A user's earned achievement. */
    public record EarnedAchievementDto(String code, Instant earnedAt) {
    }

    /** GET /api/achievements — full catalogue. */
    @GetMapping("/api/achievements")
    public List<AchievementDto> catalogue() {
        return achievementRepository.findAll().stream()
                .map(AchievementDto::from)
                .toList();
    }

    /** GET /api/users/{handle}/achievements — the user's earned achievements. */
    @GetMapping("/api/users/{handle}/achievements")
    public List<EarnedAchievementDto> earned(@PathVariable String handle) {
        User user = userService.findByHandle(handle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserAchievement> earned = userAchievementRepository.findByUserId(user.getId());
        Map<UUID, String> codesById = achievementRepository.findAll().stream()
                .collect(Collectors.toMap(Achievement::getId, Achievement::getCode));

        return earned.stream()
                .map(ua -> new EarnedAchievementDto(
                        codesById.get(ua.getAchievementId()), ua.getEarnedAt()))
                .filter(dto -> dto.code() != null)
                .toList();
    }
}
