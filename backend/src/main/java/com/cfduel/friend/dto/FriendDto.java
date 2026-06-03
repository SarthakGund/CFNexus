package com.cfduel.friend.dto;

import com.cfduel.user.User;
import java.time.Instant;

/**
 * An accepted friend, projected for {@code GET /api/friends}. Includes live
 * presence (from Redis) and the CF / duel rating fields needed by the UI.
 */
public record FriendDto(
        String cfHandle,
        String avatarUrl,
        Integer cfRating,
        String cfRank,
        Integer duelRating,
        boolean online,
        Instant lastSeen) {

    public static FriendDto of(User u, boolean online) {
        return new FriendDto(
                u.getCfHandle(),
                u.getAvatarUrl(),
                u.getCfRating(),
                u.getCfRank(),
                u.getDuelRating(),
                online,
                u.getLastSeen());
    }
}
