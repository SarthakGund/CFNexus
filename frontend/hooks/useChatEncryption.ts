"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import api from "@/lib/api";
import { useAuth } from "@/hooks/useAuth";
import { useWebSocket } from "@/hooks/useWebSocket";
import {
  decrypt,
  deriveSharedKey,
  encrypt,
  exportPublicKeyB64,
  generateKeyPair,
  importPublicKeyB64,
  type EncryptedPayload,
} from "@/lib/encryption";

/** A peer's chat key as returned by the backend (spec §11). */
interface ChatKeyDto {
  userId: string;
  handle: string | null;
  publicKeyB64: string;
}

/** Shape of a relayed chat payload on the wire. */
export interface ChatWirePayload {
  ciphertext: string;
  iv: string;
  senderPublicKeyB64: string;
}

export interface UseChatEncryption {
  /** True once our key pair exists and our public key has been registered. */
  ready: boolean;
  /** Map of peer publicKeyB64 -> handle, for labelling incoming messages. */
  handleForKey: (publicKeyB64: string) => string | null;
  /** Our own exported public key (so we can tag/identify our outgoing messages). */
  myPublicKeyB64: string | null;
  /** Encrypt plaintext for the room; returns one wire payload (self-derived key). */
  encryptOutgoing: (plaintext: string) => Promise<ChatWirePayload | null>;
  /** Decrypt an incoming wire payload using the matching peer shared key. */
  decryptIncoming: (payload: ChatWirePayload) => Promise<string | null>;
}

/**
 * Manages the ephemeral in-memory ECDH key lifecycle for a duel room (spec §11):
 *
 * - On mount, generate a key pair and POST our public key to
 *   {@code /duels/{roomCode}/chat-key} (the server broadcasts the full key set).
 * - Subscribe to {@code /topic/duel/{roomCode}/chat-keys} to learn peers' keys
 *   and derive a per-peer AES-GCM shared key (cached by publicKeyB64).
 * - Expose encrypt/decrypt helpers. Decryption matches the sender by their
 *   public key. Encryption derives against the same peer key (ECDH is symmetric,
 *   so a 1:1 shared key both sides hold lets either decrypt).
 *
 * Private keys live only in this hook's memory — never persisted.
 */
export function useChatEncryption(roomCode: string): UseChatEncryption {
  const { user } = useAuth();
  const { subscribe } = useWebSocket();

  const keyPairRef = useRef<CryptoKeyPair | null>(null);
  const [myPublicKeyB64, setMyPublicKeyB64] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  /** publicKeyB64 -> derived shared key with that peer. */
  const sharedKeysRef = useRef<Map<string, CryptoKey>>(new Map());
  /** publicKeyB64 -> handle, for rendering sender labels. */
  const handlesRef = useRef<Map<string, string | null>>(new Map());

  // 1. Generate ephemeral key pair + register our public key for the room.
  useEffect(() => {
    let active = true;
    // Capture the cache maps for cleanup (refs may be reassigned by render).
    const sharedKeys = sharedKeysRef.current;
    const handles = handlesRef.current;
    async function bootstrap() {
      try {
        const pair = await generateKeyPair();
        if (!active) return;
        keyPairRef.current = pair;
        const pub = await exportPublicKeyB64(pair.publicKey);
        if (!active) return;
        setMyPublicKeyB64(pub);
        // Server stores it and broadcasts the full key set back to us.
        await api.post(`/duels/${roomCode}/chat-key`, { publicKeyB64: pub });
        if (active) setReady(true);
      } catch {
        // Chat key registration failed (auth/network); chat stays unusable
        // but the rest of the duel keeps working.
      }
    }
    void bootstrap();
    return () => {
      active = false;
      sharedKeys.clear();
      handles.clear();
      keyPairRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomCode]);

  /** Derive (and cache) a shared key for each peer key we don't yet know. */
  const ingestKeys = useCallback(
    async (keys: ChatKeyDto[]) => {
      const pair = keyPairRef.current;
      if (!pair) return;
      for (const k of keys) {
        handlesRef.current.set(k.publicKeyB64, k.handle);
        // Skip our own key and ones we've already derived.
        if (k.userId === user?.id) continue;
        if (sharedKeysRef.current.has(k.publicKeyB64)) continue;
        try {
          const peerPub = await importPublicKeyB64(k.publicKeyB64);
          const shared = await deriveSharedKey(pair.privateKey, peerPub);
          sharedKeysRef.current.set(k.publicKeyB64, shared);
        } catch {
          // Malformed peer key — ignore it.
        }
      }
    },
    [user?.id],
  );

  // 2. Learn peers' keys via the broadcast topic; also do an initial fetch so we
  //    don't miss keys registered before we subscribed.
  useEffect(() => {
    if (!ready) return;
    const unsub = subscribe(`/topic/duel/${roomCode}/chat-keys`, (msg) => {
      try {
        const keys = JSON.parse(msg.body) as ChatKeyDto[];
        void ingestKeys(keys);
      } catch {
        /* ignore malformed broadcast */
      }
    });

    let active = true;
    void (async () => {
      try {
        const { data } = await api.get<ChatKeyDto[]>(
          `/duels/${roomCode}/chat-keys`,
        );
        if (active) await ingestKeys(data);
      } catch {
        /* ignore */
      }
    })();

    return () => {
      active = false;
      unsub();
    };
  }, [ready, roomCode, subscribe, ingestKeys]);

  const handleForKey = useCallback((publicKeyB64: string) => {
    return handlesRef.current.get(publicKeyB64) ?? null;
  }, []);

  /**
   * Encrypt for the room. With per-peer shared keys, we encrypt under the first
   * known peer's shared key (1:1 duels have exactly one peer). For multi-peer
   * rooms this targets the first peer; a fuller fan-out (one payload per peer)
   * can layer on top later, but the primary spec target is 1v1.
   */
  const encryptOutgoing = useCallback(
    async (plaintext: string): Promise<ChatWirePayload | null> => {
      const myPub = myPublicKeyB64;
      const firstShared = sharedKeysRef.current.values().next();
      if (!myPub || firstShared.done) return null;
      try {
        const { ciphertext, iv }: EncryptedPayload = await encrypt(
          firstShared.value,
          plaintext,
        );
        return { ciphertext, iv, senderPublicKeyB64: myPub };
      } catch {
        return null;
      }
    },
    [myPublicKeyB64],
  );

  /** Decrypt by matching the sender's public key to a derived shared key. */
  const decryptIncoming = useCallback(
    async (payload: ChatWirePayload): Promise<string | null> => {
      const shared = sharedKeysRef.current.get(payload.senderPublicKeyB64);
      if (!shared) return null;
      try {
        return await decrypt(shared, payload.ciphertext, payload.iv);
      } catch {
        return null;
      }
    },
    [],
  );

  return {
    ready,
    handleForKey,
    myPublicKeyB64,
    encryptOutgoing,
    decryptIncoming,
  };
}
