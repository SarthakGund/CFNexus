package com.cfduel.chat;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatKeyRepository extends JpaRepository<ChatKey, ChatKeyId> {

    List<ChatKey> findByRoomId(UUID roomId);
}
