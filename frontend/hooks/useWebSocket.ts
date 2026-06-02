"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { SOCKJS_URL } from "@/lib/env";

export interface UseWebSocket {
  connected: boolean;
  /**
   * Subscribe to a STOMP destination. Returns an unsubscribe function.
   * Safe to call before the connection is live — the subscription is
   * (re)established automatically once connected.
   */
  subscribe: (destination: string, cb: (message: IMessage) => void) => () => void;
  /** Publish a JSON-serialisable body to a destination. */
  publish: (destination: string, body?: unknown) => void;
}

/**
 * Generic STOMP-over-SockJS connection hook.
 *
 * - Connects on mount, auto-reconnects (built-in `reconnectDelay`).
 * - Shares the Spring Session cookie via SockJS (`withCredentials`).
 * - Re-applies subscriptions across reconnects.
 * - Cleans up on unmount.
 */
export function useWebSocket(): UseWebSocket {
  const clientRef = useRef<Client | null>(null);
  const [connected, setConnected] = useState(false);

  /** Pending/active subscriptions keyed by a generated id. */
  const subsRef = useRef<
    Map<
      number,
      {
        destination: string;
        cb: (message: IMessage) => void;
        sub?: StompSubscription;
      }
    >
  >(new Map());
  const subIdRef = useRef(0);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(SOCKJS_URL) as WebSocket,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        // (Re)establish all known subscriptions.
        subsRef.current.forEach((entry) => {
          entry.sub = client.subscribe(entry.destination, entry.cb);
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      subsRef.current.clear();
      void client.deactivate();
      clientRef.current = null;
      setConnected(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const subscribe = useCallback(
    (destination: string, cb: (message: IMessage) => void) => {
      const id = subIdRef.current++;
      const entry: {
        destination: string;
        cb: (message: IMessage) => void;
        sub?: StompSubscription;
      } = { destination, cb };
      subsRef.current.set(id, entry);

      const client = clientRef.current;
      if (client?.connected) {
        entry.sub = client.subscribe(destination, cb);
      }

      return () => {
        const e = subsRef.current.get(id);
        e?.sub?.unsubscribe();
        subsRef.current.delete(id);
      };
    },
    [],
  );

  const publish = useCallback((destination: string, body?: unknown) => {
    const client = clientRef.current;
    if (!client?.connected) return;
    client.publish({
      destination,
      body: body === undefined ? "" : JSON.stringify(body),
    });
  }, []);

  return { connected, subscribe, publish };
}
