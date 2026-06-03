"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { useAuth } from "@/hooks/useAuth";
import { useWebSocket } from "@/hooks/useWebSocket";
import {
  useChatEncryption,
  type ChatWirePayload,
} from "@/hooks/useChatEncryption";

interface ChatMessage {
  id: string;
  handle: string;
  text: string;
  mine: boolean;
}

/**
 * End-to-end encrypted chat for a duel room (spec §11).
 *
 * Plaintext never leaves this component: outgoing messages are encrypted with a
 * per-peer ECDH/AES-GCM shared key before publishing to
 * {@code /app/duel/{roomCode}/chat}; incoming messages from
 * {@code /topic/duel/{roomCode}/chat} are decrypted locally. The server relays
 * opaque ciphertext only.
 *
 * Self-contained: the duel page just renders {@code <ChatPanel roomCode={..} />}.
 */
export function ChatPanel({ roomCode }: { roomCode: string }) {
  const { user } = useAuth();
  const { connected, subscribe, publish } = useWebSocket();
  const { ready, encryptOutgoing, decryptIncoming, handleForKey, myPublicKeyB64 } =
    useChatEncryption(roomCode);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);

  const listEndRef = useRef<HTMLDivElement | null>(null);
  const msgSeq = useRef(0);

  const appendMessage = useCallback((msg: ChatMessage) => {
    setMessages((prev) => [...prev, msg]);
  }, []);

  // Subscribe to incoming encrypted chat and decrypt locally.
  useEffect(() => {
    const unsub = subscribe(`/topic/duel/${roomCode}/chat`, (frame) => {
      let payload: ChatWirePayload;
      try {
        payload = JSON.parse(frame.body) as ChatWirePayload;
      } catch {
        return;
      }
      if (!payload?.senderPublicKeyB64) return;
      // Skip our own echoed message — we render it optimistically on send.
      if (payload.senderPublicKeyB64 === myPublicKeyB64) return;

      void (async () => {
        const text = await decryptIncoming(payload);
        if (text == null) return; // no shared key yet / undecryptable
        appendMessage({
          id: `in-${msgSeq.current++}`,
          handle: handleForKey(payload.senderPublicKeyB64) ?? "peer",
          text,
          mine: false,
        });
      })();
    });
    return unsub;
  }, [
    roomCode,
    subscribe,
    decryptIncoming,
    handleForKey,
    appendMessage,
    myPublicKeyB64,
  ]);

  // Keep the newest message in view.
  useEffect(() => {
    listEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const handleSend = useCallback(async () => {
    const text = draft.trim();
    if (!text || sending) return;
    setSending(true);
    try {
      const payload = await encryptOutgoing(text);
      if (!payload) return; // no peer key yet — cannot encrypt
      publish(`/app/duel/${roomCode}/chat`, payload);
      appendMessage({
        id: `out-${msgSeq.current++}`,
        handle: user?.cfHandle ?? "you",
        text,
        mine: true,
      });
      setDraft("");
    } finally {
      setSending(false);
    }
  }, [draft, sending, encryptOutgoing, publish, roomCode, appendMessage, user?.cfHandle]);

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleSend();
    }
  };

  const canSend = connected && ready && draft.trim().length > 0 && !sending;

  return (
    <Card className="flex h-full flex-col overflow-hidden">
      <div className="border-b px-4 py-2">
        <h2 className="text-sm font-semibold">Encrypted chat</h2>
        <p className="text-xs text-muted-foreground">
          End-to-end encrypted. The server never sees your messages.
        </p>
      </div>

      <div
        className="flex-1 space-y-2 overflow-y-auto px-4 py-3"
        aria-live="polite"
        aria-label="Chat messages"
      >
        {messages.length === 0 ? (
          <p className="text-xs text-muted-foreground">
            No messages yet. Say hello to your opponent.
          </p>
        ) : (
          messages.map((m) => (
            <div
              key={m.id}
              className={cn(
                "flex flex-col text-sm",
                m.mine ? "items-end" : "items-start",
              )}
            >
              <span className="text-xs font-medium text-muted-foreground">
                {m.handle}
              </span>
              <span
                className={cn(
                  "max-w-[85%] whitespace-pre-wrap break-words rounded-lg px-3 py-1.5",
                  m.mine
                    ? "bg-primary text-primary-foreground"
                    : "bg-muted text-foreground",
                )}
              >
                {m.text}
              </span>
            </div>
          ))
        )}
        <div ref={listEndRef} />
      </div>

      <div className="flex items-center gap-2 border-t px-3 py-2">
        <label htmlFor="chat-input" className="sr-only">
          Message
        </label>
        <input
          id="chat-input"
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={ready ? "Type a message…" : "Setting up encryption…"}
          disabled={!ready || !connected}
          className={cn(
            "flex-1 rounded-md border border-input bg-background px-3 py-1.5 text-sm",
            "focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
            "disabled:cursor-not-allowed disabled:opacity-50",
          )}
          aria-label="Chat message input"
        />
        <Button
          type="button"
          size="sm"
          onClick={() => void handleSend()}
          disabled={!canSend}
        >
          Send
        </Button>
      </div>
    </Card>
  );
}

export default ChatPanel;
