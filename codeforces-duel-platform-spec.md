# Codeforces Duel Platform — Claude Code Specification

> **Document Purpose:** Comprehensive specification for Claude Code to implement a full-stack competitive programming duel platform built on Spring Boot, WebSocket, and Codeforces OAuth.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Authentication — Codeforces OAuth](#4-authentication--codeforces-oauth)
5. [Database Schema](#5-database-schema)
6. [API Design](#6-api-design)
7. [WebSocket Design](#7-websocket-design)
8. [Duel Logic](#8-duel-logic)
9. [Rating System](#9-rating-system)
10. [Problem Selection Engine](#10-problem-selection-engine)
11. [Chat System with Encryption](#11-chat-system-with-encryption)
12. [Code Editor Integration](#12-code-editor-integration)
13. [Leaderboard & Achievements](#13-leaderboard--achievements)
14. [Friends System](#14-friends-system)
15. [Profile & Public Pages](#15-profile--public-pages)
16. [Disconnection Handling](#16-disconnection-handling)
17. [Security Requirements](#17-security-requirements)
18. [Frontend — Pages & UI](#18-frontend--pages--ui)
19. [Non-Functional Requirements](#19-non-functional-requirements)
20. [Project Structure](#20-project-structure)
21. [Environment Variables](#21-environment-variables)
22. [Implementation Order](#22-implementation-order)

---

## 1. Project Overview

A web platform where Codeforces users can compete in real-time programming duels. Users log in via Codeforces OAuth, are matched or invite opponents, and solve a Codeforces problem. The first to get an **Accepted** verdict on Codeforces wins. Supports:

- **Rated 1v1 duels** — affects a custom duel rating (ELO-style)
- **Unrated team duels** — up to 4v4 with flexible team composition
- **Unrated free-for-all rooms** — up to 4 individual players
- Real-time problem solving with live chat, draw offers, and resign options
- Rich profiles, leaderboards, achievements, and friends

---

## 2. Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x (Java 17) |
| WebSocket | Spring WebSocket + STOMP |
| Security | Spring Security 6, OAuth2 Client |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Cache | Redis 7 (sessions, rate limiting, presence) |
| Build | Maven or Gradle (Gradle preferred) |
| External API | Codeforces REST API v1 |
| Scheduling | Spring `@Scheduled` for verdict polling |
| Validation | Jakarta Bean Validation |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |

### Frontend
| Layer | Technology |
|---|---|
| Framework | React 18 + TypeScript |
| Routing | React Router v6 |
| State | Zustand |
| WebSocket Client | SockJS + STOMP.js |
| HTTP Client | Axios |
| UI Components | shadcn/ui + Tailwind CSS |
| Charts | Recharts |
| Code Editor | Monaco Editor (VS Code engine) |
| Theme | next-themes (light/dark toggle) |
| Forms | React Hook Form + Zod |
| Build | Vite |

### Infrastructure
| Concern | Tool |
|---|---|
| Containerization | Docker + Docker Compose |
| Reverse Proxy | Nginx |
| SSL/TLS | Let's Encrypt (Certbot) |
| Monitoring | Spring Actuator + Micrometer |

---

## 3. Architecture Overview

```
Browser (React SPA)
    │
    ├── REST (HTTPS) ──────────────────────────────► Spring Boot App
    │                                                      │
    └── WebSocket (WSS/STOMP) ─────────────────────►      │
                                                           ├── PostgreSQL (persistent data)
                                                           ├── Redis (sessions, WS state, rate limits)
                                                           └── Codeforces API (OAuth + problem/verdict data)
```

### Key Design Decisions
- Spring Security manages session via Redis-backed `HttpSession` (not JWT, to allow server-side invalidation)
- WebSocket connections are authenticated via the same session cookie
- Verdict polling uses a `@Scheduled` task that checks Codeforces submission status every 5 seconds for active duels
- All sensitive chat messages use end-to-end encryption: ECDH key exchange between clients (server never sees plaintext)

---

## 4. Authentication — Codeforces OAuth

### OAuth 2.0 Flow

Codeforces uses OAuth 2.0 Authorization Code flow.

**Endpoints:**
- Authorization: `https://codeforces.com/oauth/authorize`
- Token: `https://codeforces.com/oauth/token`
- User info (via API key): `https://codeforces.com/api/user.info?handles={handle}`

**Spring Security Configuration:**
```
application.yml:
  spring.security.oauth2.client.registration.codeforces:
    client-id: ${CF_CLIENT_ID}
    client-secret: ${CF_CLIENT_SECRET}
    authorization-grant-type: authorization_code
    redirect-uri: "{baseUrl}/login/oauth2/code/codeforces"
    scope: "user:info"
  spring.security.oauth2.client.provider.codeforces:
    authorization-uri: https://codeforces.com/oauth/authorize
    token-uri: https://codeforces.com/oauth/token
    user-info-uri: https://codeforces.com/api/user.info
    user-name-attribute: handle
```

**Post-Login Flow:**
1. OAuth success → `OAuth2AuthenticationSuccessHandler`
2. Extract `handle` from Codeforces user info
3. Upsert `User` record in DB (create if new, update `lastLogin` if existing)
4. Fetch/refresh Codeforces profile data (rating, rank, avatar, etc.)
5. Store `userId` in session
6. Redirect to `/dashboard`

**Security Config Rules:**
- Permit: `/`, `/login`, `/oauth2/**`, `/api/public/**`, `/ws/**`
- Authenticated: all other routes
- CSRF: disabled for WebSocket endpoints only; enabled for REST
- CORS: configured for frontend origin only

---

## 5. Database Schema

### users
```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cf_handle       VARCHAR(50) UNIQUE NOT NULL,
    cf_user_id      BIGINT UNIQUE NOT NULL,
    cf_rating       INT DEFAULT 0,
    cf_max_rating   INT DEFAULT 0,
    cf_rank         VARCHAR(30),
    cf_max_rank     VARCHAR(30),
    avatar_url      TEXT,
    duel_rating     INT DEFAULT 1200,
    duel_wins       INT DEFAULT 0,
    duel_losses     INT DEFAULT 0,
    duel_draws      INT DEFAULT 0,
    unrated_wins    INT DEFAULT 0,
    current_streak  INT DEFAULT 0,
    max_streak      INT DEFAULT 0,
    fastest_solve_ms BIGINT,
    bio             TEXT,
    favorite_lang   VARCHAR(20),
    is_online       BOOLEAN DEFAULT FALSE,
    last_seen       TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
```

### duel_rooms
```sql
CREATE TABLE duel_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_code       VARCHAR(12) UNIQUE NOT NULL,  -- shareable link token
    room_type       VARCHAR(20) NOT NULL,          -- RATED_1V1 | UNRATED_TEAM | UNRATED_FFA
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING',  -- WAITING | IN_PROGRESS | COMPLETED | CANCELLED
    host_id         UUID REFERENCES users(id),
    problem_rating  INT NOT NULL,
    problem_id      VARCHAR(30),                   -- CF contestId/index, set at start
    problem_url     TEXT,
    winner_team     INT,                           -- 1 or 2 for team duels, winner user_id for FFA
    started_at      TIMESTAMP,
    ended_at        TIMESTAMP,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

### duel_participants
```sql
CREATE TABLE duel_participants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id     UUID REFERENCES duel_rooms(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id),
    team        INT,                -- 1 or 2 for team duels; NULL for FFA
    slot        INT,                -- 1-4 slot position in UI
    status      VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE | RESIGNED | DISCONNECTED
    joined_at   TIMESTAMP DEFAULT NOW(),
    UNIQUE(room_id, user_id)
);
```

### duel_results
```sql
CREATE TABLE duel_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID REFERENCES duel_rooms(id),
    winner_id           UUID REFERENCES users(id),    -- NULL for draw
    loser_id            UUID REFERENCES users(id),    -- NULL for draw/FFA
    result_type         VARCHAR(20) NOT NULL,          -- WIN | DRAW | RESIGN | DISCONNECT | TIMEOUT
    winner_rating_before INT,
    winner_rating_after  INT,
    loser_rating_before  INT,
    loser_rating_after   INT,
    rating_delta        INT,
    solve_duration_ms   BIGINT,
    problem_id          VARCHAR(30),
    created_at          TIMESTAMP DEFAULT NOW()
);
```

### match_history
```sql
CREATE TABLE match_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id         UUID REFERENCES duel_rooms(id),
    user_id         UUID REFERENCES users(id),
    opponent_ids    UUID[],                -- array of opponent user IDs
    result          VARCHAR(20),           -- WIN | LOSS | DRAW | RESIGN
    problem_id      VARCHAR(30),
    problem_rating  INT,
    problem_url     TEXT,
    duel_type       VARCHAR(20),
    duration_ms     BIGINT,
    rating_before   INT,
    rating_after    INT,
    played_at       TIMESTAMP DEFAULT NOW()
);
```

### friends
```sql
CREATE TABLE friends (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID REFERENCES users(id),
    addressee_id UUID REFERENCES users(id),
    status      VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | ACCEPTED | BLOCKED
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW(),
    UNIQUE(requester_id, addressee_id)
);
```

### achievements
```sql
CREATE TABLE achievements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50) UNIQUE NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    icon        VARCHAR(50),
    condition_type VARCHAR(30),
    condition_value INT
);

CREATE TABLE user_achievements (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),
    achievement_id  UUID REFERENCES achievements(id),
    earned_at       TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, achievement_id)
);
```

### rating_history
```sql
CREATE TABLE rating_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    duel_id     UUID REFERENCES duel_rooms(id),
    rating      INT NOT NULL,
    delta       INT NOT NULL,
    recorded_at TIMESTAMP DEFAULT NOW()
);
```

### chat_keys (for E2E encryption)
```sql
CREATE TABLE chat_keys (
    room_id         UUID REFERENCES duel_rooms(id) ON DELETE CASCADE,
    user_id         UUID REFERENCES users(id),
    public_key_b64  TEXT NOT NULL,          -- ECDH P-256 public key, base64
    created_at      TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY(room_id, user_id)
);
```

---

## 6. API Design

All REST endpoints are prefixed `/api`. Authenticated endpoints require a valid session cookie.

### Auth
```
GET  /api/auth/me              → current user profile
GET  /api/auth/logout          → invalidate session
GET  /oauth2/authorization/codeforces  → initiate OAuth
GET  /login/oauth2/code/codeforces     → OAuth callback (handled by Spring)
```

### Users
```
GET  /api/users/{handle}           → public profile by CF handle
GET  /api/users/{handle}/rating-history  → duel rating history array
GET  /api/users/{handle}/match-history   → paginated match history
GET  /api/users/{handle}/achievements    → earned achievements
GET  /api/users/search?q={query}    → search users by handle prefix
PATCH /api/users/me                → update bio, favoriteLanguage
```

### Duels
```
POST /api/duels/create             → create duel room, returns { roomCode, inviteUrl }
  Body: { type: "RATED_1V1"|"UNRATED_TEAM"|"UNRATED_FFA", problemRating: 800-3500 }

GET  /api/duels/{roomCode}         → get room state (used to render join page)
POST /api/duels/{roomCode}/join    → join a room (authenticated)
  Body: { team?: 1|2, slot?: 1-4 }
POST /api/duels/{roomCode}/start   → host starts the duel (triggers problem selection)
GET  /api/duels/{roomCode}/problem → get problem details for current duel
```

### Friends
```
GET  /api/friends                  → list accepted friends
GET  /api/friends/requests         → incoming pending requests
POST /api/friends/request/{handle} → send friend request
POST /api/friends/accept/{handle}  → accept request
DELETE /api/friends/{handle}       → remove friend / reject
```

### Leaderboard
```
GET  /api/leaderboard?type=duel_rating|unrated_wins|streak|fastest_solve&page=0&size=50
```

### Achievements
```
GET  /api/achievements             → list all possible achievements
```

---

## 7. WebSocket Design

Use STOMP over SockJS. All WS connections require an authenticated session.

### Connection Endpoint
```
ws://{host}/ws   →  SockJS endpoint
```

### Topics (subscribe)

| Topic | Description |
|---|---|
| `/topic/duel/{roomCode}` | Duel state broadcasts (start, end, draw offer, etc.) |
| `/topic/duel/{roomCode}/chat` | Encrypted chat messages |
| `/queue/user` | Private user notifications (friend requests, duel invites) |
| `/topic/leaderboard` | Live leaderboard updates (top 50) |
| `/topic/presence/{handle}` | Online/offline presence for a specific user |

### Application Destinations (send)

| Destination | Payload | Description |
|---|---|---|
| `/app/duel/{roomCode}/ready` | `{}` | Player signals ready |
| `/app/duel/{roomCode}/resign` | `{}` | Player resigns |
| `/app/duel/{roomCode}/offer-draw` | `{}` | Offer a draw |
| `/app/duel/{roomCode}/respond-draw` | `{ accept: boolean }` | Accept/decline draw |
| `/app/duel/{roomCode}/chat` | `{ ciphertext, iv, senderPublicKey }` | Send encrypted message |
| `/app/duel/{roomCode}/heartbeat` | `{}` | Presence keepalive (every 15s) |

### Server-Pushed Events (to `/topic/duel/{roomCode}`)

Each event has an `eventType` discriminator field:

| eventType | Payload fields | Trigger |
|---|---|---|
| `ROOM_STATE` | Full room state snapshot | On join |
| `PLAYER_JOINED` | userId, handle, team, slot | Someone joins |
| `PLAYER_LEFT` | userId | Someone disconnects |
| `DUEL_STARTED` | problem (id, name, url, rating), startedAt | Host starts duel |
| `DRAW_OFFERED` | byUserId | Player offers draw |
| `DRAW_DECLINED` | byUserId | Player declines draw |
| `DUEL_ENDED` | result (winnerId, losers, type, newRatings) | Verdict detected or resign/draw |
| `PLAYER_DISCONNECTED` | userId, gracePeriodSeconds | Player lost connection |
| `PLAYER_RECONNECTED` | userId | Disconnected player is back |
| `TIMER_UPDATE` | elapsedMs | Sent every second during duel |

---

## 8. Duel Logic

### Room Lifecycle

```
WAITING → IN_PROGRESS → COMPLETED
             ↓
          CANCELLED (if host leaves before start)
```

### 8.1 Rated 1v1 Duel

1. Creator calls `POST /api/duels/create` with `type=RATED_1V1` and `problemRating`
2. Backend creates `duel_rooms` record, generates `roomCode` (8-char alphanumeric)
3. Creator shares invite link `/duel/{roomCode}` — opponent must be logged in to join
4. Opponent opens link → `POST /api/duels/{roomCode}/join`
5. Creator calls `POST /api/duels/{roomCode}/start`
6. Backend selects unsolved problem (see §10)
7. WS broadcast `DUEL_STARTED` with problem info
8. Verdict polling begins (see §8.4)
9. First player with Accepted → `DUEL_ENDED` broadcast → ratings updated

### 8.2 Unrated Team Duel (up to 4v4)

- Any combination is valid: 1v1, 1v2, 1v3, 2v2, 2v3, 2v4, 3v3, 3v4, 4v4, 1v4 (asymmetric allowed)
- UI shows two banks of 4 slot buttons (Team 1 | Team 2)
- Each joining user clicks a slot to claim it
- Minimum 1 player per team to start
- Win condition: first team to have any member get Accepted
- No rating changes

### 8.3 Unrated Free-For-All Room (up to 4 individuals)

- No teams; `team` column is NULL
- First person to get Accepted wins
- `unrated_wins` incremented for winner
- All others get `unrated_loss`

### 8.4 Verdict Polling

```
Spring @Scheduled(fixedDelay = 5000)
For each room in IN_PROGRESS status:
  For each participant:
    Call CF API: https://codeforces.com/api/user.status?handle={handle}&from=1&count=10
    Filter submissions: contestId and index match current problem, submitted after duel start time
    If any submission has verdict == "OK":
      → Mark that participant as winner
      → Broadcast DUEL_ENDED
      → Update ratings if rated
      → Insert match_history records
      → Mark room as COMPLETED
      Break all loops for this room
```

**Rate limit handling:** Codeforces API allows ~1 req/s per IP. Use a queue/executor service that processes polling requests sequentially with a minimum 1100ms interval between API calls.

### 8.5 Draw Offer Flow

1. Player A sends `/app/duel/{roomCode}/offer-draw`
2. Server broadcasts `DRAW_OFFERED` to room
3. Player B sees draw offer UI prompt (10-second auto-decline timer)
4. If B accepts → `DUEL_ENDED` with type=`DRAW`
5. For rated duels, draws result in no rating change (K-factor×0)

### 8.6 Resign

- Player sends `/app/duel/{roomCode}/resign`
- Immediate `DUEL_ENDED` broadcast, resigning player loses, other player wins
- For rated duels, treated as a loss with full delta

---

## 9. Rating System

### Algorithm: ELO with dynamic K-factor

```java
// Expected score for player A against player B
double expectedA = 1.0 / (1.0 + Math.pow(10, (ratingB - ratingA) / 400.0));

// Actual score: 1.0 = win, 0.5 = draw, 0.0 = loss
double actualA = switch(result) {
    case WIN  -> 1.0;
    case DRAW -> 0.5;
    case LOSS -> 0.0;
};

// K-factor based on total rated games played
int k = totalGames < 10 ? 64 : totalGames < 30 ? 32 : 16;

// New rating
int newRatingA = (int) Math.round(ratingA + k * (actualA - expectedA));
int newRatingB = (int) Math.round(ratingB + k * ((1 - actualA) - (1 - expectedA)));

// Floor: rating never drops below 100
newRatingA = Math.max(100, newRatingA);
newRatingB = Math.max(100, newRatingB);
```

### Problem Rating Bonus
If the problem rating is significantly higher than both players' duel ratings, apply a modifier:
```java
double problemDifficulty = (problemRating - avgDuelRating) / 400.0;
// Clamp to [-1, +1], scale K slightly
double kModifier = Math.max(0.5, Math.min(1.5, 1.0 + problemDifficulty * 0.2));
```

### Rank Tiers (displayed on profile)
| Rating | Tier |
|---|---|
| < 800 | Newbie |
| 800–999 | Apprentice |
| 1000–1199 | Pupil |
| 1200–1399 | Specialist |
| 1400–1599 | Expert |
| 1600–1899 | Candidate Master |
| 1900–2099 | Master |
| 2100–2299 | International Master |
| 2300+ | Grandmaster |

---

## 10. Problem Selection Engine

### Algorithm

```java
public CfProblem selectProblem(List<String> participantHandles, int targetRating) {
    // 1. Fetch solved problems for all participants from CF API
    //    GET /api/user.status?handle={h}&from=1&count=1000  (for each player)
    Set<String> solvedProblems = new HashSet<>();
    for (String handle : participantHandles) {
        List<CfSubmission> subs = cfApiClient.getUserStatus(handle, 1000);
        subs.stream()
            .filter(s -> "OK".equals(s.getVerdict()))
            .forEach(s -> solvedProblems.add(s.getProblemKey())); // contestId+index
    }

    // 2. Fetch all problems of target rating from CF problemset
    //    GET /api/problemset.problems?tags=
    List<CfProblem> candidates = cfApiClient.getProblems(targetRating);

    // 3. Filter out solved, filter out educational rounds (optional config)
    List<CfProblem> eligible = candidates.stream()
        .filter(p -> !solvedProblems.contains(p.getKey()))
        .filter(p -> p.getSolvedCount() > 100)  // ensure problem is tested
        .collect(Collectors.toList());

    // 4. Shuffle and return first
    Collections.shuffle(eligible);
    if (eligible.isEmpty()) {
        // Fallback: try ±100 rating range
        return selectProblemFallback(participantHandles, targetRating);
    }
    return eligible.get(0);
}
```

### Caching
- Cache CF problemset response for 1 hour in Redis
- Cache individual user solved problems for 5 minutes in Redis (stale is acceptable)
- Key pattern: `cf:problems:{rating}`, `cf:solved:{handle}`

---

## 11. Chat System with Encryption

### Design: End-to-End Encryption using ECDH + AES-GCM

The server acts as a relay only — it never sees plaintext messages.

### Key Exchange (Client-Side, on duel join)

```typescript
// Each client generates an ephemeral ECDH key pair
const keyPair = await window.crypto.subtle.generateKey(
  { name: "ECDH", namedCurve: "P-256" },
  true,
  ["deriveKey"]
);

// Export public key as base64 and POST to /api/duels/{roomCode}/chat-key
const pubKeyBuffer = await window.crypto.subtle.exportKey("spki", keyPair.publicKey);
const pubKeyB64 = btoa(String.fromCharCode(...new Uint8Array(pubKeyBuffer)));

// Server stores it in chat_keys table and broadcasts all public keys to room participants
```

### Encrypting a Message

```typescript
// Derive shared secret with recipient's public key
const sharedKey = await window.crypto.subtle.deriveKey(
  { name: "ECDH", public: recipientPublicKey },
  myPrivateKey,
  { name: "AES-GCM", length: 256 },
  false,
  ["encrypt", "decrypt"]
);

// Encrypt
const iv = window.crypto.getRandomValues(new Uint8Array(12));
const encoded = new TextEncoder().encode(plaintext);
const ciphertext = await window.crypto.subtle.encrypt(
  { name: "AES-GCM", iv },
  sharedKey,
  encoded
);

// Send via WS
stompClient.send(`/app/duel/${roomCode}/chat`, {}, JSON.stringify({
  ciphertext: btoa(String.fromCharCode(...new Uint8Array(ciphertext))),
  iv: btoa(String.fromCharCode(...iv)),
  senderPublicKeyB64: myPublicKeyB64
}));
```

### Server-Side Handling
- Server receives encrypted payload
- Validates sender is a participant of the room
- Rate-limits: max 60 messages/minute per user (Redis token bucket)
- Stores encrypted ciphertext in Redis (TTL = 24h) for late-joining participants
- Broadcasts to `/topic/duel/{roomCode}/chat` — never logs or stores plaintext

### Server-Side Additional Security
- TLS termination at Nginx layer (WSS)
- WebSocket frames validated against STOMP protocol
- Message size limit: 10KB per message
- Input sanitization on all non-encrypted fields (username, room metadata)

---

## 12. Code Editor Integration

Use **Monaco Editor** embedded in the duel room page.

### Setup
```typescript
import Editor from "@monaco-editor/react";

<Editor
  height="400px"
  defaultLanguage="cpp"
  theme={isDark ? "vs-dark" : "light"}
  options={{
    fontSize: 14,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
  }}
  value={code}
  onChange={(val) => setCode(val ?? "")}
/>
```

### Supported Languages (dropdown selector)
- C++ (default) — `cpp`
- Python — `python`
- Java — `java`
- JavaScript — `javascript`
- Go — `go`
- Rust — `rust`

### Run Code (via OneCompiler API or Judge0)
```
POST /api/code/run
Body: { language: "cpp", code: "...", stdin: "..." }
Response: { stdout, stderr, exitCode, executionTimeMs }
```

- Backend proxies to a sandboxed execution service (self-hosted Judge0 or OneCompiler API)
- Rate limit: 10 runs/minute per user
- Timeout: 10 seconds per execution
- Max code size: 64KB

### Submit to Codeforces
- Display a prominent **"Submit on Codeforces"** button linking to the problem's submission page
- Note: Automated submission via CF API requires additional permissions; guide user to submit manually and the verdict poller will detect it

---

## 13. Leaderboard & Achievements

### Leaderboard Categories
| Category | Metric | Sort |
|---|---|---|
| Duel Rating | `duel_rating` | DESC |
| Unrated Wins | `unrated_wins` | DESC |
| Current Streak | `current_streak` | DESC |
| Fastest Solve | `fastest_solve_ms` | ASC |

Query pattern:
```sql
SELECT u.cf_handle, u.avatar_url, u.duel_rating, u.duel_wins, u.duel_losses,
       RANK() OVER (ORDER BY u.duel_rating DESC) as rank
FROM users u
ORDER BY u.duel_rating DESC
LIMIT 50 OFFSET ?;
```

### Achievement Definitions

| Code | Name | Condition |
|---|---|---|
| `FIRST_WIN` | First Blood | Win your first duel |
| `STREAK_5` | On Fire | 5-win streak |
| `STREAK_10` | Unstoppable | 10-win streak |
| `STREAK_20` | Legendary | 20-win streak |
| `RANK_UP_1` | Rising Star | Reach 1400 duel rating |
| `RANK_UP_2` | Expert Duelist | Reach 1600 duel rating |
| `RANK_UP_3` | Master Duelist | Reach 1900 duel rating |
| `GRANDMASTER` | Grandmaster | Reach 2300 duel rating |
| `FAST_10` | Speed Demon | Solve a problem in under 10 minutes |
| `FAST_5` | Lightning | Solve a problem in under 5 minutes |
| `FAST_1` | One-Minute Wonder | Solve a problem in under 1 minute |
| `WINS_10` | Battle Tested | Win 10 rated duels |
| `WINS_50` | Veteran | Win 50 rated duels |
| `WINS_100` | Century | Win 100 rated duels |
| `HARD_PROBLEM` | Slayer | Solve a problem rated 200+ above your CF rating |
| `CLEAN_WIN` | Flawless | Win 10 duels in a row without a draw or resign |
| `TEAM_WIN_4V4` | Squad Goals | Win a 4v4 team duel |

Achievement checker runs as a Spring `ApplicationEventListener` after each duel result is saved.

---

## 14. Friends System

### Endpoints (detail)

**Send request:** `POST /api/friends/request/{handle}`
- Inserts `friends` row with `status=PENDING`
- Sends WS notification to target user: `/queue/user` with event `FRIEND_REQUEST`

**Accept:** `POST /api/friends/accept/{handle}`
- Updates row to `status=ACCEPTED`
- Sends WS notification to requester: event `FRIEND_REQUEST_ACCEPTED`

**List friends:** `GET /api/friends`
- Returns accepted friends with their online status (from Redis presence keys)
- Includes CF rating, duel rating, and last seen

**Friend activity feed (optional v2):** Show when friends are in a duel

---

## 15. Profile & Public Pages

### Own Profile (`/profile` or `/u/{handle}`)

Sections:
1. **Header** — Avatar, CF handle, CF rank badge, duel rating badge, bio, favorite language
2. **Stats Bar** — W/L/D, current streak, unrated wins, fastest solve
3. **Duel Rating Graph** — Recharts `LineChart`, X-axis = date, Y-axis = duel rating; data from `rating_history`
4. **Codeforces Rating Graph** — Fetched live from CF API `user.rating` endpoint; same chart style
5. **Match History Table** — Columns: Date, Opponent, Problem (linked), Rating, Result, Duration
6. **Achievements Grid** — Badge icons with tooltips, greyed out if unearned
7. **Friends List** (own profile only) — List with online indicators

### Meta / SEO
- Dynamic `<title>` and `<meta description>` per profile
- Open Graph tags for social sharing
- `robots.txt` allowing all public profile pages

---

## 16. Disconnection Handling

### Detection
- WebSocket session closed event caught by `SessionDisconnectEvent` in Spring
- Also: client sends heartbeat every 15s; if 3 missed → server marks as disconnected

### Grace Period
- On disconnect: set Redis key `disconnect:{roomCode}:{userId}` with 30s TTL
- Broadcast `PLAYER_DISCONNECTED` event with `gracePeriodSeconds=30`
- Grace period visible as countdown timer on opponents' screens

### Reconnect
- If user reconnects before TTL expires: delete Redis key, broadcast `PLAYER_RECONNECTED`
- Room continues normally

### Forfeit on Disconnect (Rated Duels Only)
- If grace period expires and duel is still `IN_PROGRESS`:
  - Spring `@Scheduled` task checks for expired disconnect keys
  - Disconnected player loses (treated as resign for rating purposes)
  - Broadcast `DUEL_ENDED` with `result=DISCONNECT`

### Unrated Duels
- Disconnected players are removed from the room
- If a team loses all members due to disconnection, opposing team wins
- FFA rooms continue with remaining players; disconnected player is simply removed

---

## 17. Security Requirements

### Authentication & Authorization
- All WebSocket subscriptions validate session server-side
- User can only send messages to rooms they're a participant of
- Host-only actions (start duel, kick player) verified server-side
- Rate limiting via Redis: 100 API requests/minute per user

### Input Validation
- All request bodies validated with Jakarta Bean Validation `@Valid`
- SQL injection impossible via JPA parameterized queries
- XSS prevention: React escapes by default; server sets `Content-Security-Policy` header
- CF handle input: whitelist `[a-zA-Z0-9_\-]{3,24}` regex

### Transport Security
- HTTPS everywhere (enforced by Nginx redirect)
- WebSocket over WSS only
- `Strict-Transport-Security` header
- Secure, HttpOnly, SameSite=Lax session cookie

### Chat Security
- End-to-end encrypted (see §11)
- Server never stores decryption keys
- Encrypted payloads stored with TTL, not permanently

### Additional Headers (Nginx)
```nginx
add_header X-Frame-Options "DENY";
add_header X-Content-Type-Options "nosniff";
add_header Referrer-Policy "strict-origin-when-cross-origin";
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()";
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' cdn.jsdelivr.net; ...";
```

### Secrets Management
- All secrets via environment variables, never hardcoded
- Rotate CF OAuth secret via env var update + redeploy
- Redis AUTH password required

---

## 18. Frontend — Pages & UI

### Pages

| Path | Component | Auth Required |
|---|---|---|
| `/` | `LandingPage` | No |
| `/login` | `LoginPage` | No |
| `/dashboard` | `Dashboard` | Yes |
| `/duel/create` | `CreateDuelPage` | Yes |
| `/duel/:roomCode` | `DuelRoomPage` | Yes |
| `/u/:handle` | `ProfilePage` | No (own profile requires auth) |
| `/leaderboard` | `LeaderboardPage` | No |
| `/friends` | `FriendsPage` | Yes |
| `/settings` | `SettingsPage` | Yes |
| `/onboarding` | `OnboardingPage` | Yes (first login) |
| `/terms` | `TermsPage` | No |
| `/privacy` | `PrivacyPage` | No |

### Key UI Components

**DuelRoomPage layout:**
```
┌──────────────────────────────────────────────────────────────┐
│  [Problem Title]  Rating: 1400   ⏱ 12:34   [Resign] [Draw]  │
├────────────────────────────┬─────────────────────────────────┤
│                            │                                  │
│   Problem Statement        │   Monaco Code Editor             │
│   (CF iframe or           │   Language Selector              │
│    parsed HTML)            │   [Run Code]  [Submit on CF]    │
│                            │                                  │
├────────────────────────────┴─────────────────────────────────┤
│  Team 1: [PlayerA ✓]  [PlayerB …]  vs  Team 2: [PlayerC]   │
├──────────────────────────────────────────────────────────────┤
│  💬 Chat: [encrypted messages]                [Send]         │
└──────────────────────────────────────────────────────────────┘
```

**Team Slot Selector (join screen):**
```
        TEAM 1              TEAM 2
    ┌──────────┐        ┌──────────┐
    │  Slot 1  │        │  Slot 1  │
    │ [handle] │        │  [open]  │
    ├──────────┤        ├──────────┤
    │  Slot 2  │        │  Slot 2  │
    │  [open]  │        │  [open]  │
    ├──────────┤        ├──────────┤
    │  Slot 3  │        │  Slot 3  │
    │  [open]  │        │  [open]  │
    ├──────────┤        ├──────────┤
    │  Slot 4  │        │  Slot 4  │
    │  [open]  │        │  [open]  │
    └──────────┘        └──────────┘
         [Join Team 1]  [Join Team 2]
```

### Theming
- Tailwind CSS with `dark:` variant classes
- Theme toggle stored in `localStorage` and synced via `next-themes`
- CSS variables for brand colors — consistent between light/dark

### Responsive Breakpoints
- Mobile-first design
- `sm: 640px` — stack columns
- `md: 768px` — side-by-side problem + editor
- `lg: 1024px` — full duel room layout

### Accessibility
- All interactive elements have ARIA labels
- Keyboard navigable (Tab, Enter, Space)
- Focus indicators visible
- Color contrast ratio ≥ 4.5:1 (WCAG AA)
- `prefers-reduced-motion` respected for animations

---

## 19. Non-Functional Requirements

### Onboarding Flow
After first login, show 3-step modal:
1. Welcome — explain platform, show sample duel GIF
2. Profile setup — prompt for bio, favorite language
3. How it works — quick explainer of rated vs unrated

### Landing Page Sections
1. Hero — tagline, "Login with Codeforces" CTA, animated demo screenshot
2. Features — rated duels, team battles, E2E chat, code editor
3. Leaderboard preview — top 5 players (public, no auth)
4. How it works — 3-step visual (Create → Invite → Solve)
5. Footer — links to Terms, Privacy, GitHub

### Performance
- Lighthouse score ≥ 90 across all categories
- API responses ≤ 200ms (p95) for cached endpoints
- WebSocket message latency ≤ 100ms on LAN
- Frontend bundle split by route (React lazy + Suspense)
- Images optimized (WebP, lazy loaded)

### SEO
- SSR or pre-rendering for public pages (landing, profiles) via meta tags
- `sitemap.xml` for public routes
- Structured data (JSON-LD) on profile pages

### Code Quality
- Backend: checkstyle, Lombok for boilerplate reduction
- Frontend: ESLint + Prettier enforced in CI
- Unit tests: JUnit 5 for service layer; Vitest for React components
- Integration tests: Testcontainers (PostgreSQL + Redis) for repository layer
- API contract tests: Spring MockMvc

---

## 20. Project Structure

```
codeforces-duel/
├── backend/
│   ├── src/main/java/com/cfduel/
│   │   ├── CfDuelApplication.java
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── WebSocketConfig.java
│   │   │   ├── RedisConfig.java
│   │   │   └── CorsConfig.java
│   │   ├── auth/
│   │   │   ├── OAuth2SuccessHandler.java
│   │   │   └── CustomOAuth2UserService.java
│   │   ├── user/
│   │   │   ├── User.java              (entity)
│   │   │   ├── UserRepository.java
│   │   │   ├── UserService.java
│   │   │   └── UserController.java
│   │   ├── duel/
│   │   │   ├── DuelRoom.java
│   │   │   ├── DuelParticipant.java
│   │   │   ├── DuelResult.java
│   │   │   ├── DuelRoomRepository.java
│   │   │   ├── DuelService.java
│   │   │   ├── DuelController.java    (REST)
│   │   │   └── DuelWebSocketController.java (STOMP)
│   │   ├── rating/
│   │   │   ├── RatingService.java
│   │   │   └── RatingHistory.java
│   │   ├── problem/
│   │   │   ├── ProblemSelectionService.java
│   │   │   └── CfApiClient.java
│   │   ├── verdict/
│   │   │   └── VerdictPollingService.java (@Scheduled)
│   │   ├── chat/
│   │   │   ├── ChatMessage.java
│   │   │   ├── ChatKeyService.java
│   │   │   └── ChatKey.java
│   │   ├── achievement/
│   │   │   ├── Achievement.java
│   │   │   ├── AchievementService.java
│   │   │   └── AchievementChecker.java
│   │   ├── leaderboard/
│   │   │   └── LeaderboardController.java
│   │   ├── friends/
│   │   │   ├── Friend.java
│   │   │   ├── FriendRepository.java
│   │   │   └── FriendController.java
│   │   ├── presence/
│   │   │   └── PresenceService.java
│   │   └── code/
│   │       └── CodeRunController.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/              (Flyway scripts)
│   └── build.gradle
│
├── frontend/
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── pages/
│   │   │   ├── LandingPage.tsx
│   │   │   ├── Dashboard.tsx
│   │   │   ├── DuelRoomPage.tsx
│   │   │   ├── ProfilePage.tsx
│   │   │   ├── LeaderboardPage.tsx
│   │   │   ├── FriendsPage.tsx
│   │   │   ├── CreateDuelPage.tsx
│   │   │   ├── OnboardingPage.tsx
│   │   │   ├── SettingsPage.tsx
│   │   │   ├── TermsPage.tsx
│   │   │   └── PrivacyPage.tsx
│   │   ├── components/
│   │   │   ├── duel/
│   │   │   │   ├── DuelTimer.tsx
│   │   │   │   ├── TeamSlotSelector.tsx
│   │   │   │   ├── DrawOfferModal.tsx
│   │   │   │   ├── ProblemPanel.tsx
│   │   │   │   └── DuelEndModal.tsx
│   │   │   ├── chat/
│   │   │   │   ├── ChatPanel.tsx
│   │   │   │   └── useChatEncryption.ts
│   │   │   ├── editor/
│   │   │   │   └── CodeEditor.tsx
│   │   │   ├── profile/
│   │   │   │   ├── RatingGraph.tsx
│   │   │   │   ├── MatchHistory.tsx
│   │   │   │   └── AchievementBadge.tsx
│   │   │   └── ui/                    (shadcn components)
│   │   ├── hooks/
│   │   │   ├── useWebSocket.ts
│   │   │   ├── useAuth.ts
│   │   │   └── useDuel.ts
│   │   ├── store/
│   │   │   ├── authStore.ts
│   │   │   └── duelStore.ts
│   │   ├── api/
│   │   │   └── client.ts              (Axios instance)
│   │   └── lib/
│   │       ├── encryption.ts
│   │       └── rating.ts
│   ├── index.html
│   ├── vite.config.ts
│   └── package.json
│
├── docker-compose.yml
├── nginx/
│   └── nginx.conf
└── README.md
```

---

## 21. Environment Variables

```env
# Backend (application.yml or .env)
CF_CLIENT_ID=your_cf_oauth_client_id
CF_CLIENT_SECRET=your_cf_oauth_client_secret
CF_API_KEY=your_cf_api_key              # for signed CF API calls
CF_API_SECRET=your_cf_api_secret

DB_URL=jdbc:postgresql://localhost:5432/cfduel
DB_USERNAME=cfduel
DB_PASSWORD=secret

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redissecret

SESSION_SECRET=32-char-random-secret
FRONTEND_ORIGIN=https://yourdomain.com

CODE_RUNNER_URL=http://judge0:2358      # Judge0 instance
CODE_RUNNER_API_KEY=judge0-api-key

# Frontend (.env)
VITE_API_BASE_URL=https://yourdomain.com/api
VITE_WS_URL=wss://yourdomain.com/ws
```

---

## 22. Implementation Order

Claude Code should implement in this order to always have a working system at each phase:

### Phase 1 — Foundation
1. Spring Boot project setup (Gradle, dependencies, Docker Compose for Postgres + Redis)
2. Database schema + Flyway migrations
3. Codeforces OAuth integration (login/logout)
4. Basic user model + `/api/auth/me` endpoint
5. React app scaffold (Vite, Tailwind, routing, auth store)
6. Landing page + Login page (Codeforces OAuth button)

### Phase 2 — Core Duel
7. Duel room creation and join REST APIs
8. WebSocket config + STOMP broker setup
9. Duel WebSocket controller (join, start, resign, draw)
10. CF API client + problem selection service
11. Verdict polling scheduler
12. Basic duel room page (problem display, timer, resign/draw buttons)

### Phase 3 — Rating & Results
13. ELO rating service
14. Match history persistence
15. Rating history table + rating graph on profile
16. Profile page (basic stats, graphs)

### Phase 4 — Team & FFA Duels
17. Team slot selection UI
18. Team duel room type support
19. FFA room support
20. Leaderboard endpoints + page

### Phase 5 — Chat & Editor
21. E2E encrypted chat (key exchange + WS relay)
22. Monaco editor integration
23. Code run proxy (Judge0)

### Phase 6 — Social & Polish
24. Friends system
25. Achievements system + badge display
26. Disconnection handling (grace period + forfeit)
27. Onboarding flow
28. Full responsive UI pass
29. Dark/light theme
30. SEO meta tags, sitemap

### Phase 7 — Security Hardening & NFRs
31. Rate limiting (Redis token bucket)
32. Security headers (Nginx config)
33. Input validation sweep
34. Performance optimization (caching, lazy loading)
35. Accessibility audit
36. Terms & Privacy pages

---

*End of specification. Claude Code should read this document fully before beginning implementation and refer back to relevant sections when implementing each feature.*
