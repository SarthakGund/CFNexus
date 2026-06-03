package com.cfduel.friend;

import com.cfduel.friend.dto.FriendDto;
import com.cfduel.friend.dto.FriendNotification;
import com.cfduel.friend.dto.IncomingRequestDto;
import com.cfduel.user.User;
import com.cfduel.user.UserRepository;
import com.cfduel.ws.PresenceService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Friends system business logic (spec §14): send / accept / remove requests and
 * list accepted friends (with live presence) and incoming pending requests.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;
    private final FriendNotifier friendNotifier;

    /**
     * Send a PENDING friend request from {@code currentUserId} to the user with
     * {@code targetHandle}. Rejects self-requests (400) and any pre-existing
     * relationship in either direction (409). Notifies the target.
     */
    public void sendRequest(UUID currentUserId, String targetHandle) {
        User current = requireUser(currentUserId);
        User target = userRepository.findByCfHandleIgnoreCase(targetHandle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (target.getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot friend yourself");
        }

        friendRepository.findBetween(currentUserId, target.getId()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Friend request already exists");
        });

        Friend friend = Friend.builder()
                .requesterId(currentUserId)
                .addresseeId(target.getId())
                .status(Friend.STATUS_PENDING)
                .build();
        friendRepository.save(friend);

        friendNotifier.notifyUser(
                target.getId(),
                FriendNotification.request(current.getCfHandle(), current.getAvatarUrl()));
    }

    /**
     * Accept the PENDING request where the current user is the addressee and the
     * requester is the user with {@code requesterHandle}. Notifies the requester.
     */
    public void acceptRequest(UUID currentUserId, String requesterHandle) {
        User current = requireUser(currentUserId);
        User requester = userRepository.findByCfHandleIgnoreCase(requesterHandle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Friend pending = friendRepository
                .findByRequesterIdAndAddresseeIdAndStatus(
                        requester.getId(), currentUserId, Friend.STATUS_PENDING)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No pending request from " + requesterHandle));

        pending.setStatus(Friend.STATUS_ACCEPTED);
        friendRepository.save(pending);

        friendNotifier.notifyUser(
                requester.getId(),
                FriendNotification.accepted(current.getCfHandle(), current.getAvatarUrl()));
    }

    /** Remove a friendship (or reject a request) in either direction. */
    public void removeFriend(UUID currentUserId, String otherHandle) {
        User other = userRepository.findByCfHandleIgnoreCase(otherHandle)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        friendRepository.findBetween(currentUserId, other.getId())
                .ifPresent(friendRepository::delete);
    }

    /** Accepted friends of the current user, with live presence + ratings. */
    @Transactional(readOnly = true)
    public List<FriendDto> listFriends(UUID currentUserId) {
        return friendRepository.findAcceptedInvolving(currentUserId).stream()
                .map(f -> otherUserId(f, currentUserId))
                .map(otherId -> userRepository.findById(otherId).orElse(null))
                .filter(u -> u != null)
                .map(u -> FriendDto.of(u, presenceService.isOnline(u.getId().toString())))
                .toList();
    }

    /** Pending incoming requests addressed to the current user. */
    @Transactional(readOnly = true)
    public List<IncomingRequestDto> listIncoming(UUID currentUserId) {
        return friendRepository.findIncomingPending(currentUserId).stream()
                .map(f -> userRepository.findById(f.getRequesterId())
                        .map(u -> IncomingRequestDto.of(u, f.getCreatedAt()))
                        .orElse(null))
                .filter(dto -> dto != null)
                .toList();
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private static UUID otherUserId(Friend f, UUID self) {
        return f.getRequesterId().equals(self) ? f.getAddresseeId() : f.getRequesterId();
    }
}
