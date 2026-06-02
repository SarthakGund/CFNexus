package com.cfduel.rating;

import com.cfduel.cf.CfApiClient;
import com.cfduel.cf.dto.CfRatingChange;
import com.cfduel.rating.dto.MatchHistoryDto;
import com.cfduel.rating.dto.PagedResponse;
import com.cfduel.rating.dto.RatingPointDto;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import com.cfduel.user.UserService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only profile endpoints (spec §6, §15): duel rating history, paginated
 * match history, and a live proxy to the Codeforces rating history.
 */
@RestController
@RequestMapping("/api/users/{handle}")
@RequiredArgsConstructor
public class ProfileController {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserService userService;
    private final UserRepository userRepository;
    private final RatingHistoryRepository ratingHistoryRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final CfApiClient cfApiClient;

    /** GET /api/users/{handle}/rating-history — duel rating points, oldest first. */
    @GetMapping("/rating-history")
    public List<RatingPointDto> ratingHistory(@PathVariable String handle) {
        User user = requireUser(handle);
        return ratingHistoryRepository.findByUserIdOrderByRecordedAtAsc(user.getId()).stream()
                .map(RatingPointDto::from)
                .toList();
    }

    /** GET /api/users/{handle}/match-history?page=0&size=20 — paginated, newest first. */
    @GetMapping("/match-history")
    public PagedResponse<MatchHistoryDto> matchHistory(
            @PathVariable String handle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = requireUser(handle);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<MatchHistory> result = matchHistoryRepository.findByUserIdOrderByPlayedAtDesc(
                user.getId(), PageRequest.of(Math.max(page, 0), safeSize));

        Map<UUID, String> handles = resolveOpponentHandles(result.getContent());
        List<MatchHistoryDto> items = result.getContent().stream()
                .map(m -> toDto(m, handles))
                .toList();
        return PagedResponse.of(result, items);
    }

    /**
     * GET /api/users/{handle}/cf-rating-history — proxies the Codeforces
     * {@code user.rating} endpoint (cached + rate-gated in {@link CfApiClient}).
     */
    @GetMapping("/cf-rating-history")
    public List<CfRatingChange> cfRatingHistory(@PathVariable String handle) {
        // Resolve to the canonical CF handle so casing matches CF's records.
        String cfHandle = userService.findByHandle(handle).map(User::getCfHandle).orElse(handle);
        return cfApiClient.getUserRating(cfHandle);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MatchHistoryDto toDto(MatchHistory m, Map<UUID, String> handles) {
        List<String> opponentHandles = new ArrayList<>();
        if (m.getOpponentIds() != null) {
            for (UUID id : m.getOpponentIds()) {
                String h = handles.get(id);
                if (h != null) {
                    opponentHandles.add(h);
                }
            }
        }
        Integer delta = (m.getRatingBefore() != null && m.getRatingAfter() != null)
                ? m.getRatingAfter() - m.getRatingBefore()
                : null;
        return new MatchHistoryDto(
                m.getRoomId(),
                m.getResult(),
                m.getProblemId(),
                m.getProblemRating(),
                m.getProblemUrl(),
                m.getDuelType(),
                m.getDurationMs(),
                m.getRatingBefore(),
                m.getRatingAfter(),
                delta,
                opponentHandles,
                m.getPlayedAt());
    }

    /** Batch-resolves all opponent user ids in the page to CF handles. */
    private Map<UUID, String> resolveOpponentHandles(List<MatchHistory> matches) {
        Map<UUID, Boolean> ids = new LinkedHashMap<>();
        for (MatchHistory m : matches) {
            if (m.getOpponentIds() != null) {
                for (UUID id : m.getOpponentIds()) {
                    ids.put(id, Boolean.TRUE);
                }
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids.keySet()).stream()
                .collect(Collectors.toMap(User::getId, User::getCfHandle, (a, b) -> a));
    }

    private User requireUser(String handle) {
        return userService.findByHandle(handle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
