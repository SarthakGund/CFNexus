package com.cfduel.duel;

import com.cfduel.auth.OAuth2SuccessHandler;
import com.cfduel.duel.dto.CreateDuelRequest;
import com.cfduel.duel.dto.CreateRoomResponse;
import com.cfduel.duel.dto.JoinDuelRequest;
import com.cfduel.duel.dto.ProblemDto;
import com.cfduel.duel.dto.RoomStateDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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

/** Duel room REST endpoints (spec §6). */
@RestController
@RequestMapping("/api/duels")
@RequiredArgsConstructor
public class DuelController {

    private final DuelService duelService;

    /** POST /api/duels/create — create a room; caller becomes host. */
    @PostMapping("/create")
    public ResponseEntity<CreateRoomResponse> create(
            HttpSession session,
            @Valid @RequestBody CreateDuelRequest request) {
        UUID userId = requireUserId(session);
        return ResponseEntity.ok(duelService.createRoom(userId, request));
    }

    /**
     * GET /api/duels/{roomCode} — room state for the join page.
     *
     * <p>TODO: ideally readable anonymously so the invite page renders for
     * logged-out users, but {@code SecurityConfig} (owned by another agent) keeps
     * {@code /api/duels/**} authenticated. In Phase 2 the join-page user is logged
     * in, so this stays auth-required.
     */
    @GetMapping("/{roomCode}")
    public ResponseEntity<RoomStateDto> getRoom(@PathVariable String roomCode) {
        return ResponseEntity.ok(duelService.getRoomState(roomCode));
    }

    /** POST /api/duels/{roomCode}/join — join a waiting room. */
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<RoomStateDto> join(
            HttpSession session,
            @PathVariable String roomCode,
            @Valid @RequestBody JoinDuelRequest request) {
        UUID userId = requireUserId(session);
        return ResponseEntity.ok(duelService.joinRoom(roomCode, userId, request));
    }

    /** POST /api/duels/{roomCode}/start — host starts the duel. */
    @PostMapping("/{roomCode}/start")
    public ResponseEntity<Void> start(
            HttpSession session,
            @PathVariable String roomCode) {
        UUID userId = requireUserId(session);
        duelService.startRoom(roomCode, userId);
        return ResponseEntity.ok().build();
    }

    /** GET /api/duels/{roomCode}/problem — the assigned problem (404 until started). */
    @GetMapping("/{roomCode}/problem")
    public ResponseEntity<ProblemDto> problem(
            HttpSession session,
            @PathVariable String roomCode) {
        requireUserId(session);
        return ResponseEntity.ok(duelService.getProblem(roomCode));
    }

    private static UUID requireUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(OAuth2SuccessHandler.SESSION_USER_ID);
        if (id instanceof UUID uuid) {
            return uuid;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
}
