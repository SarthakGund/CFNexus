package com.cfduel.achievement;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    Optional<Achievement> findByCode(String code);
}
