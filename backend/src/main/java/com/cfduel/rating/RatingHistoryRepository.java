package com.cfduel.rating;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RatingHistoryRepository extends JpaRepository<RatingHistory, UUID> {

    /** Chronological rating points for a user — drives the duel-rating LineChart. */
    List<RatingHistory> findByUserIdOrderByRecordedAtAsc(UUID userId);
}
