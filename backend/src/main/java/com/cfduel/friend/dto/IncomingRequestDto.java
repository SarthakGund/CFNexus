package com.cfduel.friend.dto;

import com.cfduel.user.User;
import java.time.Instant;

/**
 * A pending incoming friend request, projected for {@code GET /api/friends/incoming}.
 * Describes the user who sent the request to the current user.
 */
public record IncomingRequestDto(
        String cfHandle,
        String avatarUrl,
        Integer cfRating,
        String cfRank,
        Integer duelRating,
        Instant requestedAt) {

    public static IncomingRequestDto of(User requester, Instant requestedAt) {
        return new IncomingRequestDto(
                requester.getCfHandle(),
                requester.getAvatarUrl(),
                requester.getCfRating(),
                requester.getCfRank(),
                requester.getDuelRating(),
                requestedAt);
    }
}
