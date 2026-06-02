package com.cfduel.leaderboard;

import com.cfduel.leaderboard.dto.LeaderboardEntryDto;
import com.cfduel.rating.dto.PagedResponse;
import com.cfduel.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Leaderboard endpoint (spec §6, §13): ranks all users across four categories
 * with OFFSET-based pagination. Public — no authentication required.
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final LeaderboardRepository leaderboardRepository;

    /** GET /api/leaderboard?type=duel_rating|unrated_wins|streak|fastest_solve&page=0&size=50 */
    @GetMapping
    public PagedResponse<LeaderboardEntryDto> leaderboard(
            @RequestParam(defaultValue = "duel_rating") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        if (safeSize == 0) {
            safeSize = DEFAULT_PAGE_SIZE;
        }
        int safePage = Math.max(page, 0);
        int offset = safePage * safeSize;

        List<User> users;
        ToLongFunction<User> metric;
        long total;

        switch (type) {
            case "duel_rating" -> {
                users = leaderboardRepository.topByDuelRating(safeSize, offset);
                metric = u -> u.getDuelRating() == null ? 0 : u.getDuelRating();
                total = leaderboardRepository.countAll();
            }
            case "unrated_wins" -> {
                users = leaderboardRepository.topByUnratedWins(safeSize, offset);
                metric = u -> u.getUnratedWins() == null ? 0 : u.getUnratedWins();
                total = leaderboardRepository.countAll();
            }
            case "streak" -> {
                users = leaderboardRepository.topByCurrentStreak(safeSize, offset);
                metric = u -> u.getCurrentStreak() == null ? 0 : u.getCurrentStreak();
                total = leaderboardRepository.countAll();
            }
            case "fastest_solve" -> {
                users = leaderboardRepository.topByFastestSolve(safeSize, offset);
                metric = u -> u.getFastestSolveMs() == null ? 0 : u.getFastestSolveMs();
                total = leaderboardRepository.countWithFastestSolve();
            }
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unknown leaderboard type: " + type);
        }

        List<LeaderboardEntryDto> items = new ArrayList<>(users.size());
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            items.add(LeaderboardEntryDto.from(u, offset + i + 1, metric.applyAsLong(u)));
        }

        int totalPages = (int) Math.ceil((double) total / safeSize);
        return new PagedResponse<>(items, safePage, safeSize, total, totalPages);
    }
}
