"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { IMessage } from "@stomp/stompjs";
import api from "@/lib/api";
import { useWebSocket } from "@/hooks/useWebSocket";
import {
  useDuelStore,
  type DuelEvent,
  type RoomState,
} from "@/store/duelStore";

const HEARTBEAT_INTERVAL_MS = 15_000;

export interface UseDuel {
  connected: boolean;
  loading: boolean;
  error: string | null;
  /** Re-fetch room state from REST (used after join/start). */
  refresh: () => Promise<void>;
  resign: () => void;
  offerDraw: () => void;
  respondDraw: (accept: boolean) => void;
  sendReady: () => void;
}

/**
 * Drives a single duel room: loads initial REST state, opens the STOMP
 * connection, funnels events into the duel store, pumps a heartbeat, and
 * exposes the player actions. The reactive state itself lives in
 * `useDuelStore` — read it there in components.
 */
export function useDuel(roomCode: string): UseDuel {
  const { connected, subscribe, publish } = useWebSocket();
  const setRoom = useDuelStore((s) => s.setRoom);
  const applyEvent = useDuelStore((s) => s.applyEvent);
  const reset = useDuelStore((s) => s.reset);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const base = `/app/duel/${roomCode}`;

  const refresh = useCallback(async () => {
    try {
      const { data } = await api.get<RoomState>(`/duels/${roomCode}`);
      setRoom(data);
      setError(null);
    } catch {
      setError("Failed to load duel room.");
    }
  }, [roomCode, setRoom]);

  // Initial load + store cleanup on unmount / room change.
  useEffect(() => {
    let active = true;
    setLoading(true);
    void (async () => {
      await refresh();
      if (active) setLoading(false);
    })();
    return () => {
      active = false;
      reset();
    };
  }, [refresh, reset]);

  // Subscribe to duel topics once connected.
  useEffect(() => {
    if (!connected) return;

    const handleEvent = (message: IMessage) => {
      try {
        const event = JSON.parse(message.body) as DuelEvent;
        if (event && event.eventType) applyEvent(event);
      } catch {
        // ignore malformed frames
      }
    };

    const unsubDuel = subscribe(`/topic/duel/${roomCode}`, handleEvent);
    // Chat wired now, rendered raw in Phase 5.
    const unsubChat = subscribe(`/topic/duel/${roomCode}/chat`, () => {});
    const unsubUser = subscribe(`/user/queue/user`, () => {});

    return () => {
      unsubDuel();
      unsubChat();
      unsubUser();
    };
  }, [connected, roomCode, subscribe, applyEvent]);

  // Heartbeat every 15s while connected.
  const hbRef = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    if (!connected) return;
    hbRef.current = setInterval(() => {
      publish(`${base}/heartbeat`);
    }, HEARTBEAT_INTERVAL_MS);
    return () => {
      if (hbRef.current) clearInterval(hbRef.current);
      hbRef.current = null;
    };
  }, [connected, base, publish]);

  const resign = useCallback(() => publish(`${base}/resign`), [base, publish]);
  const offerDraw = useCallback(
    () => publish(`${base}/offer-draw`),
    [base, publish],
  );
  const respondDraw = useCallback(
    (accept: boolean) => publish(`${base}/respond-draw`, { accept }),
    [base, publish],
  );
  const sendReady = useCallback(
    () => publish(`${base}/ready`),
    [base, publish],
  );

  return {
    connected,
    loading,
    error,
    refresh,
    resign,
    offerDraw,
    respondDraw,
    sendReady,
  };
}
