package com.cfduel.chat;

import com.cfduel.chat.dto.ChatKeyDto;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores and retrieves participants' ephemeral ECDH public keys for a room
 * (spec §11). The server only ever handles public keys — it is a relay and
 * never sees plaintext or private keys.
 */
@Service
@RequiredArgsConstructor
public class ChatKeyService {

    private final ChatKeyRepository chatKeyRepository;
    private final UserRepository userRepository;

    /**
     * Upsert a user's public key for the room. A user re-joining (new ephemeral
     * key pair) simply overwrites their previous entry via the composite PK.
     */
    @Transactional
    public void storeKey(UUID roomId, UUID userId, String publicKeyB64) {
        ChatKey key = chatKeyRepository.findById(new ChatKeyId(roomId, userId))
                .orElseGet(() -> ChatKey.builder().roomId(roomId).userId(userId).build());
        key.setPublicKeyB64(publicKeyB64);
        chatKeyRepository.save(key);
    }

    /** All known public keys for the room, enriched with the owner's CF handle. */
    @Transactional(readOnly = true)
    public List<ChatKeyDto> getKeys(UUID roomId) {
        List<ChatKey> keys = chatKeyRepository.findByRoomId(roomId);
        List<UUID> ids = keys.stream().map(ChatKey::getUserId).toList();
        Map<UUID, User> usersById = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return keys.stream()
                .map(k -> {
                    User u = usersById.get(k.getUserId());
                    return new ChatKeyDto(
                            k.getUserId(),
                            u == null ? null : u.getCfHandle(),
                            k.getPublicKeyB64());
                })
                .toList();
    }
}
