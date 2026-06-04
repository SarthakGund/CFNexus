package com.cfduel.friend;

import com.cfduel.auth.OAuth2SuccessHandler;
import com.cfduel.friend.dto.FriendDto;
import com.cfduel.friend.dto.IncomingRequestDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Friends REST endpoints (spec §6, §14). The current user's UUID is resolved
 * from the HTTP session attribute set by {@link OAuth2SuccessHandler}, matching
 * {@link com.cfduel.user.UserController}.
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
@Validated
public class FriendController {

    /** Whitelist for Codeforces handles (spec §11): 3–24 alphanumerics, underscore, hyphen. */
    private static final String HANDLE_REGEX = "^[a-zA-Z0-9_\\-]{3,24}$";
    private static final String HANDLE_MSG = "handle must be 3-24 chars of letters, digits, _ or -";

    private final FriendService friendService;

    /** GET /api/friends — accepted friends with online status + ratings + last seen. */
    @GetMapping
    public List<FriendDto> listFriends(HttpSession session) {
        return friendService.listFriends(requireUserId(session));
    }

    /** GET /api/friends/incoming — pending requests addressed to the current user. */
    @GetMapping("/incoming")
    public List<IncomingRequestDto> listIncoming(HttpSession session) {
        return friendService.listIncoming(requireUserId(session));
    }

    /** POST /api/friends/request/{handle} — send a friend request. */
    @PostMapping("/request/{handle}")
    public ResponseEntity<Void> sendRequest(HttpSession session,
            @PathVariable @Pattern(regexp = HANDLE_REGEX, message = HANDLE_MSG) String handle) {
        friendService.sendRequest(requireUserId(session), handle);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** POST /api/friends/accept/{handle} — accept a pending request from {handle}. */
    @PostMapping("/accept/{handle}")
    public ResponseEntity<Void> acceptRequest(HttpSession session,
            @PathVariable @Pattern(regexp = HANDLE_REGEX, message = HANDLE_MSG) String handle) {
        friendService.acceptRequest(requireUserId(session), handle);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/friends/{handle} — remove a friend / reject a request. */
    @DeleteMapping("/{handle}")
    public ResponseEntity<Void> removeFriend(HttpSession session,
            @PathVariable @Pattern(regexp = HANDLE_REGEX, message = HANDLE_MSG) String handle) {
        friendService.removeFriend(requireUserId(session), handle);
        return ResponseEntity.noContent().build();
    }

    private static UUID requireUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(OAuth2SuccessHandler.SESSION_USER_ID);
        if (id instanceof UUID uuid) {
            return uuid;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
    }
}
