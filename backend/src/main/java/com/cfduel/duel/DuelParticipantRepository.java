package com.cfduel.duel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DuelParticipantRepository extends JpaRepository<DuelParticipant, UUID> {

    List<DuelParticipant> findByRoomId(UUID roomId);

    Optional<DuelParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);
}
