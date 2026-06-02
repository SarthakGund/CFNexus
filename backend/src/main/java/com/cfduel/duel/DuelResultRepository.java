package com.cfduel.duel;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DuelResultRepository extends JpaRepository<DuelResult, UUID> {

    List<DuelResult> findByRoomId(UUID roomId);
}
