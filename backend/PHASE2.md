# CFNexus Phase 2 — Core Duel (4 parallel agents)

## Overview
Phase 2 implements the core duel mechanics: REST API for room management, WebSocket infrastructure for real-time communication, Codeforces problem selection and verdict polling, and the interactive duel room frontend. This phase enables two users to create, join, and compete in a real-time 1v1 duel with live problem data and automatic completion detection.

---

## Agent A — Duel REST API

### Entities & Repositories
- `DuelRoom.java`: roomCode (8-char unique), roomType (RATED_1V1, UNRATED_TEAM, UNRATED_FFA), status (WAITING, ACTIVE, COMPLETED), createdAt, startedAt, endedAt, hostId (FK)
- `DuelParticipant.java`: userId (FK), roomId (FK), team (for team duels), status (ACTIVE, READY, RESIGNED), verdictTime (null until submission)
- `DuelResult.java`: roomId (FK), winnerId (FK), ratingDelta (for participants)
- Repositories: `DuelRoomRepository.java`, `DuelParticipantRepository.java`, `DuelResultRepository.java`

### Service Layer
- `DuelService.java`:
  - `createRoom(userId, roomType, problemRating)`: generate random 8-char alphanumeric roomCode, create DuelRoom with status=WAITING, add host as participant
  - `joinRoom(roomCode, userId)`: validate room exists and isn't full, create DuelParticipant, return room state
  - `startRoom(roomCode, userId)`: verify caller is host, validate ≥2 participants, set status=ACTIVE, trigger problem selection

### REST Controller
- `DuelController.java`:
  - `POST /api/duels`: create room (body: `{roomType, problemRating}`) → returns roomCode
  - `GET /api/duels/{roomCode}`: get room details + participants list
  - `POST /api/duels/{roomCode}/join`: join room (auth required) → returns room state
  - `POST /api/duels/{roomCode}/start`: start duel (host only) → triggers problem selection, returns problem data
  - `GET /api/duels/{roomCode}/problem`: get current problem for the room

### Deliverables
- [ ] All entity classes with correct annotations and relationships
- [ ] Repositories support findByRoomCode, findByRoomIdAndStatus queries
- [ ] DuelService implements full room lifecycle logic
- [ ] DuelController endpoints return correct JSON schemas per §6
- [ ] POST /api/duels creates unique 8-char room codes (no collision on 10 rapid requests)
- [ ] Auth guard via `@AuthenticationPrincipal` on all endpoints

---

## Agent B — WebSocket Infrastructure

### Configuration
- `WebSocketConfig.java`:
  - Enable STOMP broker relay with `/topic` and `/queue` prefixes
  - SockJS endpoint: `/ws` (fallback support)
  - Auth interceptor: validate `JSESSIONID` cookie on WebSocket handshake
  - Message size limits: 10KB per message

### WebSocket Controllers & Models
- `DuelWebSocketController.java` with `@MessageMapping` for:
  - `/duel/{roomCode}/ready`: participant marks self as READY
  - `/duel/{roomCode}/resign`: participant forfeits
  - `/duel/{roomCode}/offer-draw`: initiate draw offer
  - `/duel/{roomCode}/respond-draw`: accept/reject draw
  - `/duel/{roomCode}/chat`: send chat message (plaintext; encryption handled frontend)
  - `/duel/{roomCode}/heartbeat`: periodic keep-alive
- Sealed interface `DuelEvent` with subtypes:
  - `PLAYER_READY`, `PLAYER_RESIGNED`, `DUEL_STARTED`, `DUEL_ENDED`, `DRAW_OFFERED`, `DRAW_ACCEPTED`, `DRAW_REJECTED`, `CHAT_MESSAGE`, `PLAYER_DISCONNECTED`, `PLAYER_RECONNECTED`, `ROOM_CLOSED`, `PROBLEM_LOADED`

### Presence Service
- `PresenceService.java`:
  - Track active sessions: Redis key `presence:{handle}` with user metadata (roomCode, timestamp)
  - Heartbeat update on WebSocket message (refresh TTL)
  - Listener on `SessionDisconnectEvent`: update disconnect key `disconnect:{roomCode}:{userId}` with 30s grace TTL
  - Endpoint to fetch online users (used by friends list)

### Deliverables
- [ ] `/ws` SockJS endpoint connects and upgrades to WebSocket
- [ ] All `@MessageMapping` handlers broadcast to `/topic/duel/{roomCode}` (multicast) or `/queue/user/{userId}` (unicast)
- [ ] Session disconnect logged to Redis within 1s
- [ ] All DuelEvent types serializable to JSON
- [ ] Message broker routes correctly: `/app/duel/{roomCode}/*` → handler, response → broker
- [ ] Heartbeat prevents session timeout (TTL refresh every 30s)

---

## Agent C — CF API & Problem Selection

### Codeforces API Client
- `CfApiClient.java`:
  - Endpoints: `user.status`, `problemset.problems`, `user.rating`, `user.info`
  - Rate limit aware: enforce 1100ms minimum interval between API calls (CF limit: ~1 request/s per IP)
  - Retry logic: exponential backoff (1s, 2s, 4s) on 429 (Too Many Requests)
  - Timeout: 10s per request
  - Exception handling: `CfApiException` on HTTP errors

### Problem Selection Service
- `ProblemSelectionService.java`:
  - Full algorithm from spec §10:
    1. Fetch both players' solved problems (cache: `cf:solved:{handle}`, 5min TTL)
    2. Fetch all problems at `requestedRating` (cache: `cf:problems:{rating}`, 1h TTL)
    3. Filter: unsolved ∩ unupsolvedByBoth ∩ accepted_ratio ≥ 75%
    4. Shuffle and select first; if none, retry ±50, ±100 rating (fallback)
    5. Return: `{contestId, index, name, rating, timeLimit, memoryLimit}`
  - Redis cache with appropriate TTLs and invalidation on stale data

### Verdict Polling Service
- `VerdictPollingService.java`:
  - `@Scheduled(fixedDelay=5000ms)`: poll per-room sequentially
  - For each active room: query CF `user.status` for both participants
  - On Accepted verdict detected: broadcast `DUEL_ENDED` event, set room status=COMPLETED, trigger rating updates
  - Handle parsing: detect verdictTime, map CF verdict to DuelEvent

### Deliverables
- [ ] CfApiClient enforces 1100ms minimum interval (measure via mocked clock or real API)
- [ ] ProblemSelectionService returns valid problem from ≥75% accepted-ratio pool
- [ ] Rating range fallback tested: if rating 1500 returns no problems, tries 1450 and 1550 before giving up
- [ ] VerdictPollingService detects Accepted within 10s of CF verdict (integration test)
- [ ] Redis cache keys follow pattern `cf:problems:{rating}` and `cf:solved:{handle}`
- [ ] All CF API exceptions caught and logged; fallback to cached problems if available

---

## Agent D — Duel Room Frontend (Next.js)

### Duel Room Page
- `app/duel/[roomCode]/page.tsx`: `'use client'` component
  - Layout from spec §18:
    - **Left panel (40%)**: problem statement (title, rating, time limit, memory, description)
    - **Center (40%)**: Monaco editor with language selector
    - **Right panel (20%)**: team/participant bar, timer (counts up from 0:00), ready/resign buttons, draw offer controls
  - Render problem markdown with proper formatting
  - Timer updates every 100ms via `setInterval`
  - Ready button toggles `useWebSocket().send('/duel/{roomCode}/ready')`

### Duel State Management
- `hooks/useDuel.ts`: Zustand-backed duel state + WebSocket integration
  - Actions: `setRoom(data)`, `setParticipants(list)`, `setProblem(data)`, `setTimer(ms)`, `setEventLog(event)`
  - Subscribes to room state updates and broadcasts
- `store/duelStore.ts`: centralized state
  - `room` (roomCode, status, type, createdAt, startedAt)
  - `participants` (array of {userId, handle, team, status, rating, verdictTime})
  - `problem` (contestId, index, name, rating, timeLimit, memoryLimit, description)
  - `timer` (elapsed ms)
  - `eventLog` (array of DuelEvent for history)

### WebSocket Integration
- `hooks/useWebSocket.ts`:
  - Connects to `/ws` SockJS endpoint with session cookies
  - Subscribes to `/topic/duel/{roomCode}` (room updates)
  - Subscribes to `/queue/user` (personal notifications: disconnection warnings, draw offers)
  - Handles all DuelEvent types, dispatches to `useDuel`
  - Heartbeat: send keepalive message every 30s

### Components
- `components/duel/DuelTimer.tsx`: displays `MM:SS` (counts up), color change at 5min if problem difficulty high
- `components/duel/TeamSlotSelector.tsx`: join screen (for Phase 2 shows 1v1 layout):
  - Host slot (filled)
  - Waiting for opponent slot
  - "Ready" button (disabled until 2+ participants)
- `components/duel/ProblemPanel.tsx`: markdown renderer for problem statement
- `components/duel/ChatPanel.tsx`: placeholder (fully implemented in Phase 5)

### Create Duel Page
- `app/duel/create/page.tsx`:
  - Form: room type selector (dropdown: RATED_1V1)
  - Problem rating slider (800–3500, default 1200)
  - "Create Duel" button → POST `/api/duels` → navigate to `/duel/[roomCode]`
  - Validation: show error if rating out of range

### Deliverables
- [ ] `/duel/create` form submits to backend, redirects to `/duel/[roomCode]`
- [ ] `/duel/[roomCode]` page loads room state and problem within 2s
- [ ] WebSocket connects, subscribes to `/topic/duel/{roomCode}` and `/queue/user`
- [ ] Timer display increments in real-time (every 100ms)
- [ ] Ready button sends WebSocket message and disables until response
- [ ] Problem statement renders with correct HTML formatting (use `next/dynamic` for markdown parser if heavy)
- [ ] Participant list updates on join/ready events
- [ ] Monaco editor is visible and responsive

---

## Integration Gate: Phase 2 Complete

**Acceptance Criteria:**
1. Two users in separate browser sessions can independently create duel rooms
2. User 2 can join User 1's room via room code
3. Both see the same participant list (User 1 as host, User 2 as participant)
4. User 1 (host) clicks "Start" → problem loads for both users within 5s
5. Both see identical problem statement, rating, time limit
6. Both see live timer counting up from 0:00
7. User 2 submits Accepted on Codeforces for the problem
8. VerdictPollingService detects it within 10s
9. Both see `DUEL_ENDED` event; room status changes to COMPLETED
10. **Redis presence updated**: both users show in `presence:{handle}` during duel
11. **No disconnection**: normal completion path shows no errors in server logs

### Manual Testing Checklist
- [ ] Create new room: verify 8-char unique room codes
- [ ] Join room twice from same handle: verify error (can't join own room)
- [ ] Start room with 1 participant: verify error (need ≥2)
- [ ] Problem selection works for all rating ranges (800, 1200, 2500, 3500)
- [ ] Timer does not reset or jump on WebSocket reconnect
- [ ] Chat input renders (no send yet; encryption in Phase 5)

---

## Estimated Effort: 16–20 hours (4 agents × 4–5 hours each)
