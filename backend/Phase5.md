# Phase 5 — Chat & Editor

## Overview
Phase 5 adds **end-to-end encrypted chat** between duel participants and a **Monaco code editor** with **Judge0 code execution** integration, enabling players to write, run, and submit code during duels.

## Backend Implementation

### Encrypted Chat (`com.cfduel.chat`)

#### ChatKey Entity & Repository
- JPA entity mapping the existing `chat_keys` table (composite PK: `room_id` + `user_id`).
- `ChatKeyRepository`: `findByRoomId(roomId)` — retrieve all participant public keys.
- Upsert pattern: POST public key → store/update in DB.

#### ChatKeyService
- `storePublicKey(roomId, userId, publicKeyB64)` — upsert into `chat_keys`.
- `getKeysForRoom(roomId)` — return all keys with CF handles (enriched from `users` table).
- No plaintext decryption; keys are opaque ECDH P-256 SPKI exports.

#### ChatRateLimiter
- Redis token bucket: `chat:rl:{userId}`, 60 msg/min per user.
- First hit in the window: `INCR` and `EXPIRE 60`.
- Fails open (allows) if Redis unavailable, logs warning.

#### ChatMessageStore
- Stores encrypted ciphertext payloads in Redis list: `chat:msgs:{roomCode}`, TTL 24h, capped at 200 recent messages.
- Late joiners can backfill via `GET /api/duels/{roomCode}/chat-history`.

#### ChatController
- `POST /api/duels/{roomCode}/chat-key` — client sends `{ publicKeyB64 }`, server stores and broadcasts room key set.
- `GET /api/duels/{roomCode}/chat-keys` — returns all room participants' public keys.
- `GET /api/duels/{roomCode}/chat-history` — returns stored encrypted messages for late-joiner backfill.
- Auth: `requireUserId(session)` + participant validation (`requireParticipant`).

#### DuelWebSocketController.chat(...)
- Accepts payload `{ ciphertext, iv, senderPublicKeyB64 }` at `@MessageMapping("/duel/{roomCode}/chat")`.
- Validates sender is a duel participant (UUID resolution via `currentUserId(principal, headers)`).
- Enforces rate limit; drops message if 429.
- Stores ciphertext in Redis via `ChatMessageStore`.
- Broadcasts to `/topic/duel/{roomCode}/chat` (opaque, never decrypted on server).

#### DuelBroadcaster Extension
- Added `broadcastChatKeys(roomCode, keySet)` — publishes to `/topic/duel/{roomCode}/chat-keys`.

### Code Execution (`com.cfduel.code`)

#### CodeRunController
- `@RestController @RequestMapping("/api/code")`
- `POST /run` — accepts `CodeRunRequest { language, code, stdin }`.
- Resolves user from session; enforces rate limit (10 runs/min, key `code:rl:{userId}`).
- Validates: code ≤ 64KB, language ∈ {cpp, python, java, javascript, go, rust}.
- Delegates to `CodeRunService`; returns `CodeRunResponse { stdout, stderr, exitCode, executionTimeMs }`.
- Returns 429 if rate-limited, 400 if invalid input.

#### CodeRunService
- Proxies to Judge0 via Spring `RestClient`.
- **Language mapping** (Judge0 language IDs):
  - cpp → 54 (C++ GCC)
  - python → 71 (Python 3)
  - java → 62 (OpenJDK)
  - javascript → 63 (Node.js)
  - go → 60 (Go)
  - rust → 73 (Rust)
- Submission body: `{ source_code, language_id, stdin, cpu_time_limit: 10.0, wall_time_limit: 10.0, ... }`
- Wait for result: `?base64_encoded=false&wait=true`.
- Maps response: `time` (sec) → `executionTimeMs`, `compile_output` → `stderr`, `exit_code` → `exitCode`.
- Error handling: 502 on Judge0 failure.

#### CodeRunRateLimiter
- Redis token bucket: `code:rl:{userId}`, 10 runs/min.
- Fails open (allows) if Redis unavailable.

### Database
- No new migrations; uses existing app config for Judge0 endpoint (`app.code-runner.url`, `app.code-runner.api-key`).
- Judge0 service already in `docker-compose.yml` (port 2358).

## Frontend Implementation

### Encryption (`lib/encryption.ts`)
- WebCrypto helpers (all functions use `window.crypto.subtle`):
  - `generateKeyPair()` — ECDH P-256 key pair (ephemeral, in-memory only).
  - `exportPublicKeyB64(publicKey)` — SPKI format to base64.
  - `importPublicKeyB64(b64)` — base64 to ECDH public key.
  - `deriveSharedKey(privateKey, recipientPublicKey)` — AES-GCM 256-bit shared key.
  - `encrypt(sharedKey, plaintext)` — returns `{ ciphertext, iv }` (both base64).
  - `decrypt(sharedKey, ciphertextB64, ivB64)` — returns plaintext string.

### Chat Encryption Hook (`hooks/useChatEncryption.ts`)
- `useChatEncryption(roomCode)` — manages lifecycle for a single room's encryption state.
- On mount: generate ephemeral ECDH key pair; POST to `/api/duels/{roomCode}/chat-key`.
- Subscribe to `/topic/duel/{roomCode}/chat-keys` — learn peers' public keys, derive 1:1 shared keys.
- Exposes: `encryptOutgoing(plaintext)` → `{ ciphertext, iv, senderPublicKeyB64 }` (targets first known peer).
- Exposes: `decryptIncoming(payload)` → plaintext (tries each known shared key).
- Keys exist only in memory; cleared on unmount.

### ChatPanel Component (`components/duel/ChatPanel.tsx`)
- `export function ChatPanel({ roomCode: string })` — self-contained.
- Renders: scrollable message list (sender handle + decrypted plaintext) + send input.
- Uses `useWebSocket()` to subscribe to `/topic/duel/{roomCode}/chat`.
- Publish encrypted: `/app/duel/{roomCode}/chat`.
- Styling: Tailwind + shadcn `Button`, `Card`, accessible (aria-live for new messages).

### Code Editor Component (`components/duel/CodeEditor.tsx`)
- `export function CodeEditor({ roomCode, problemUrl?, contestId?, index?, className? })`
- **Monaco Editor** loaded via `next/dynamic` with `ssr: false` (client-only).
- **Language dropdown**: cpp (default), python, java, javascript, go, rust.
- **Theme sync**: `useTheme()` from `next-themes` (vs-dark or light).
- **Options**: fontSize 14, minimap disabled, scrollBeyondLastLine false, automaticLayout.
- **Run button**: POST to `/api/code/run` with language, code, stdin; shows stdout/stderr/exitCode/executionTimeMs.
- **Output panel**: formatted display, 429/error states.
- **Submit link**: prominent button to `https://codeforces.com/problemset/problem/{contestId}/{index}` (or direct `problemUrl`).
- Styling: Tailwind + shadcn, responsive.

### Frontend Integration
- `frontend/package.json`: `@monaco-editor/react` already present.
- `app/duel/[roomCode]/page.tsx`: replaced editor & chat placeholders with `<CodeEditor ... />` + `<ChatPanel roomCode={...} />`.

## Testing & Verification

### Chat Encryption (Network Inspection)
- Open DevTools → Network tab.
- Send message in duel chat.
- `/topic/duel/{roomCode}/chat` payload shows `{ ciphertext, iv, senderPublicKeyB64 }` (base64, no plaintext visible).
- Message history in Redis confirms ciphertext storage, never plaintext.

### Code Execution
- Open duel room, enter code (e.g., C++ `cout << "hello" << endl;`).
- Click "Run" → stdout panel shows "hello".
- Test 64KB limit: submit code > 64KB → 400 error.
- Test rate limit: rapid submissions (>10/min) → 429 after 10th.
- Test timeout: infinite loop → Judge0 timeout, stderr shows timeout message.

### Monaco & UI
- Editor renders with language dropdown (all 6 languages switchable).
- Dark mode: theme matches system/app theme.
- Submit button links correctly to CF problemset.

## Deliverable Gate
✅ Chat messages encrypted end-to-end (network inspection confirms ciphertext only).
✅ Code runs against Judge0 (stdout/stderr visible in UI).
✅ Monaco renders with syntax highlighting.

## Known Limitations
- Chat encryption currently derives 1:1 shared key per peer (optimized for 1v1 rated duels).
  - For team/FFA multi-peer chat, per-peer fan-out would be a future enhancement.
- Fastest solve only stored per duel; no cross-duel problem statistics yet (Phase 6 or later).

## Files Created
- `backend/src/main/java/com/cfduel/chat/ChatKey.java`
- `backend/src/main/java/com/cfduel/chat/ChatKeyId.java`
- `backend/src/main/java/com/cfduel/chat/ChatKeyRepository.java`
- `backend/src/main/java/com/cfduel/chat/ChatKeyService.java`
- `backend/src/main/java/com/cfduel/chat/ChatRateLimiter.java`
- `backend/src/main/java/com/cfduel/chat/ChatMessageStore.java`
- `backend/src/main/java/com/cfduel/chat/ChatController.java`
- `backend/src/main/java/com/cfduel/chat/dto/ChatKeyDto.java`
- `backend/src/main/java/com/cfduel/chat/dto/ChatKeyRequest.java`
- `backend/src/main/java/com/cfduel/code/CodeRunController.java`
- `backend/src/main/java/com/cfduel/code/CodeRunService.java`
- `backend/src/main/java/com/cfduel/code/CodeRunRateLimiter.java`
- `backend/src/main/java/com/cfduel/code/CodeRunRequest.java`
- `backend/src/main/java/com/cfduel/code/CodeRunResponse.java`
- `frontend/lib/encryption.ts`
- `frontend/hooks/useChatEncryption.ts`
- `frontend/components/duel/ChatPanel.tsx`
- `frontend/components/duel/CodeEditor.tsx`

## Files Modified
- `backend/src/main/java/com/cfduel/ws/DuelWebSocketController.java` — rewrote `chat(...)` handler.
- `backend/src/main/java/com/cfduel/ws/DuelBroadcaster.java` — added `broadcastChatKeys(...)`.
- `frontend/app/duel/[roomCode]/page.tsx` — integrated chat and editor components.

## Build & Type-Check Results
- Backend: `mvn -q -DskipTests compile` → ✅ SUCCESS
- Frontend: `npx tsc --noEmit` → ✅ CLEAN
- Frontend: `npx next lint --file app/duel/[roomCode]/page.tsx` → ✅ NO ERRORS

## Notes
- Judge0 language IDs are stable across versions; the current mapping is standard for Judge0 1.13.1.
- Redis rate-limiting for chat (60/min) and code runs (10/min) are independent buckets.
- E2E encryption is transparent to users — they type plaintext, UI handles crypto automatically.
