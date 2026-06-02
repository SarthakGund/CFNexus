package com.cfduel.leaderboard;

import com.cfduel.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only leaderboard projections over the {@code users} table (spec §13).
 * Each category is a native {@code LIMIT ... OFFSET ...} query so pagination
 * maps directly onto the page/size request parameters. {@code cf_handle} is a
 * deterministic tie-breaker so paging is stable.
 */
public interface LeaderboardRepository extends Repository<User, UUID> {

    @Query(value = "SELECT * FROM users ORDER BY duel_rating DESC NULLS LAST, cf_handle ASC "
            + "LIMIT :size OFFSET :offset", nativeQuery = true)
    List<User> topByDuelRating(@Param("size") int size, @Param("offset") int offset);

    @Query(value = "SELECT * FROM users ORDER BY unrated_wins DESC NULLS LAST, cf_handle ASC "
            + "LIMIT :size OFFSET :offset", nativeQuery = true)
    List<User> topByUnratedWins(@Param("size") int size, @Param("offset") int offset);

    @Query(value = "SELECT * FROM users ORDER BY current_streak DESC NULLS LAST, cf_handle ASC "
            + "LIMIT :size OFFSET :offset", nativeQuery = true)
    List<User> topByCurrentStreak(@Param("size") int size, @Param("offset") int offset);

    @Query(value = "SELECT * FROM users WHERE fastest_solve_ms IS NOT NULL "
            + "ORDER BY fastest_solve_ms ASC, cf_handle ASC "
            + "LIMIT :size OFFSET :offset", nativeQuery = true)
    List<User> topByFastestSolve(@Param("size") int size, @Param("offset") int offset);

    @Query(value = "SELECT count(*) FROM users", nativeQuery = true)
    long countAll();

    @Query(value = "SELECT count(*) FROM users WHERE fastest_solve_ms IS NOT NULL", nativeQuery = true)
    long countWithFastestSolve();
}
