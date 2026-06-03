"use client";

import { useEffect, useRef } from "react";
import type { IMessage } from "@stomp/stompjs";
import { useWebSocket } from "@/hooks/useWebSocket";

/** Mirrors the backend FriendNotification record. */
export interface FriendNotification {
  type: "FRIEND_REQUEST" | "FRIEND_REQUEST_ACCEPTED" | string;
  fromHandle: string;
  fromAvatarUrl: string | null;
  message: string;
}

/**
 * Subscribes to the per-user notification queue (`/user/queue/user`) and invokes
 * {@code onNotification} for each friend event. No toast system exists in the
 * project, so consumers typically use the callback to refetch their lists.
 *
 * The callback is held in a ref so the STOMP subscription is established once
 * and never torn down on every render.
 */
export function useFriendNotifications(
  onNotification: (n: FriendNotification) => void,
): { connected: boolean } {
  const { connected, subscribe } = useWebSocket();
  const cbRef = useRef(onNotification);
  cbRef.current = onNotification;

  useEffect(() => {
    const unsubscribe = subscribe("/user/queue/user", (message: IMessage) => {
      try {
        const payload = JSON.parse(message.body) as FriendNotification;
        if (
          payload?.type === "FRIEND_REQUEST" ||
          payload?.type === "FRIEND_REQUEST_ACCEPTED"
        ) {
          cbRef.current(payload);
        }
      } catch {
        // Ignore malformed payloads.
      }
    });
    return unsubscribe;
  }, [subscribe]);

  return { connected };
}
