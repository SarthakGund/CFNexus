package com.cfduel.chat;

import com.cfduel.auth.OAuth2SuccessHandler;
import com.cfduel.chat.dto.ChatKeyDto;
import com.cfduel.chat.dto.ChatKeyRequest;
import com.cfduel.duel.DuelParticipantRepository;
import com.cfduel.duel.DuelRoom;
import com.cfduel.duel.DuelRoomRepository;
import com.cfduel.ws.DuelBroadcaster;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST endpoints for E2E-encrypted chat key exchange and history (spec §11).
 * The server only ever handles public keys and opaque ciphertext — never
 * plaintext. Mirrors {@code DuelController}'s auth convention.
 */
@RestController
@RequestMapping("/api/duels")
@RequiredArgsConstructor
public class ChatController {

    private final ChatKeyService chatKeyService;
    private final ChatMessageStore messageStore;
    private final DuelRoomRepository roomRepository;
    private final DuelParticipantRepository participantRepository;
    private final DuelBroadcaster broadcaster;

    /**
     * POST /api/duels/{roomCode}/chat-key — register the caller's ephemeral
     * public key for the room, then broadcast the full key set to participants.
     */
    @PostMapping("/{roomCode}/chat-key")
    public ResponseEntity<List<ChatKeyDto>> postKey(
            HttpSession session,
            @PathVariable String roomCode,
            @Valid @RequestBody ChatKeyRequest request) {
        UUID userId = requireUserId(session);
        DuelRoom room = requireParticipant(roomCode, userId);

        chatKeyService.storeKey(room.getId(), userId, request.publicKeyB64());

        List<ChatKeyDto> keys = chatKeyService.getKeys(room.getId());
        broadcaster.broadcastChatKeys(roomCode, keys);
        return ResponseEntity.ok(keys);
    }

    /** GET /api/duels/{roomCode}/chat-keys — all registered public keys for the room. */
    @GetMapping("/{roomCode}/chat-keys")
    public ResponseEntity<List<ChatKeyDto>> getKeys(
            HttpSession session,
            @PathVariable String roomCode) {
        UUID userId = requireUserId(session);
        DuelRoom room = requireParticipant(roomCode, userId);
        return ResponseEntity.ok(chatKeyService.getKeys(room.getId()));
    }

    /**
     * GET /api/duels/{roomCode}/chat-history — recent encrypted payloads (opaque
     * JSON strings) so a late joiner can backfill once shared keys are derived.
     */
    @GetMapping("/{roomCode}/chat-history")
    public ResponseEntity<List<String>> history(
            HttpSession session,
            @PathVariable String roomCode) {
        UUID userId = requireUserId(session);
        requireParticipant(roomCode, userId);
        return ResponseEntity.ok(messageStore.history(roomCode));
    }

    /** Resolve the room and assert the caller is one of its participants. */
    private DuelRoom requireParticipant(String roomCode, UUID userId) {
        DuelRoom room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        participantRepository.findByRoomIdAndUserId(room.getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Not a participant of this room"));
        return room;
    }

    private static UUID requireUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(OAuth2SuccessHandler.SESSION_USER_ID);
        if (id instanceof UUID uuid) {
            return uuid;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
}
