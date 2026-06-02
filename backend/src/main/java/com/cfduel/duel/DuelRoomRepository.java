package com.cfduel.duel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DuelRoomRepository extends JpaRepository<DuelRoom, UUID> {

    Optional<DuelRoom> findByRoomCode(String roomCode);

    List<DuelRoom> findByStatus(String status);
}
