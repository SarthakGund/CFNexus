package com.cfduel.friend.dto;

/**
 * Real-time friend notification pushed over the user destination
 * {@code /user/queue/user} (received client-side on {@code /user/queue/user}).
 *
 * <p>{@code type} is one of {@code "FRIEND_REQUEST"} (a new incoming request) or
 * {@code "FRIEND_REQUEST_ACCEPTED"} (a request the user sent was accepted). The
 * remaining fields describe the user that triggered the notification.
 */
public record FriendNotification(
        String type,
        String fromHandle,
        String fromAvatarUrl,
        String message) {

    public static final String TYPE_REQUEST = "FRIEND_REQUEST";
    public static final String TYPE_ACCEPTED = "FRIEND_REQUEST_ACCEPTED";

    public static FriendNotification request(String fromHandle, String fromAvatarUrl) {
        return new FriendNotification(
                TYPE_REQUEST,
                fromHandle,
                fromAvatarUrl,
                fromHandle + " sent you a friend request");
    }

    public static FriendNotification accepted(String fromHandle, String fromAvatarUrl) {
        return new FriendNotification(
                TYPE_ACCEPTED,
                fromHandle,
                fromAvatarUrl,
                fromHandle + " accepted your friend request");
    }
}
