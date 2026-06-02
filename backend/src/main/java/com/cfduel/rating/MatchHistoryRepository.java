package com.cfduel.rating;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, UUID> {

    /** Most-recent-first paginated match history for the profile table. */
    Page<MatchHistory> findByUserIdOrderByPlayedAtDesc(UUID userId, Pageable pageable);
}
