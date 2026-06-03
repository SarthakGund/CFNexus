/**
 * WebCrypto helpers for E2E-encrypted duel chat (spec §11).
 *
 * Flow: each client generates an ephemeral ECDH P-256 key pair, exports its
 * public key as base64 SPKI, and exchanges it via the server (a relay only).
 * A shared AES-GCM 256 key is derived from one party's private key + the
 * other's public key. Messages use a random 12-byte IV.
 *
 * Keys live only in memory; nothing here touches localStorage.
 */

const EC_PARAMS: EcKeyGenParams = { name: "ECDH", namedCurve: "P-256" };

/** Generate an ephemeral ECDH P-256 key pair (private key is non-extractable). */
export async function generateKeyPair(): Promise<CryptoKeyPair> {
  return window.crypto.subtle.generateKey(EC_PARAMS, false, ["deriveKey"]);
}

/** Export a public key as base64-encoded SPKI for transport. */
export async function exportPublicKeyB64(key: CryptoKey): Promise<string> {
  const spki = await window.crypto.subtle.exportKey("spki", key);
  return arrayBufferToB64(spki);
}

/** Import a peer's base64 SPKI public key back into a CryptoKey. */
export async function importPublicKeyB64(b64: string): Promise<CryptoKey> {
  const spki = b64ToArrayBuffer(b64);
  return window.crypto.subtle.importKey("spki", spki, EC_PARAMS, true, []);
}

/**
 * Derive a shared AES-GCM 256 key from our private key and the peer's public
 * key. ECDH is symmetric, so both sides derive the same key.
 */
export async function deriveSharedKey(
  privateKey: CryptoKey,
  recipientPublicKey: CryptoKey,
): Promise<CryptoKey> {
  return window.crypto.subtle.deriveKey(
    { name: "ECDH", public: recipientPublicKey },
    privateKey,
    { name: "AES-GCM", length: 256 },
    false,
    ["encrypt", "decrypt"],
  );
}

export interface EncryptedPayload {
  /** base64 AES-GCM ciphertext (includes the auth tag). */
  ciphertext: string;
  /** base64 12-byte random IV. */
  iv: string;
}

/** Encrypt UTF-8 plaintext with a shared AES-GCM key and a fresh random IV. */
export async function encrypt(
  sharedKey: CryptoKey,
  plaintext: string,
): Promise<EncryptedPayload> {
  const iv = window.crypto.getRandomValues(new Uint8Array(12));
  const data = new TextEncoder().encode(plaintext);
  const ct = await window.crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    sharedKey,
    data,
  );
  return { ciphertext: arrayBufferToB64(ct), iv: uint8ToB64(iv) };
}

/** Decrypt a base64 ciphertext+IV pair back to UTF-8 plaintext. */
export async function decrypt(
  sharedKey: CryptoKey,
  ciphertextB64: string,
  ivB64: string,
): Promise<string> {
  const ct = b64ToArrayBuffer(ciphertextB64);
  const iv = new Uint8Array(b64ToArrayBuffer(ivB64));
  const plain = await window.crypto.subtle.decrypt(
    { name: "AES-GCM", iv },
    sharedKey,
    ct,
  );
  return new TextDecoder().decode(plain);
}

// ---------------------------------------------------------------------------
// base64 <-> ArrayBuffer helpers (binary-safe; no chunking issues for chat-size
// payloads).
// ---------------------------------------------------------------------------

function arrayBufferToB64(buffer: ArrayBuffer): string {
  return uint8ToB64(new Uint8Array(buffer));
}

function uint8ToB64(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return window.btoa(binary);
}

function b64ToArrayBuffer(b64: string): ArrayBuffer {
  const binary = window.atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}
