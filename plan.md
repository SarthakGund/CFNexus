# CFNexus — Codeforces Duel Platform Implementation Plan

## Context

Building a full-stack real-time competitive programming duel platform from the `codeforces-duel-platform-spec.md` ground up. Users log in via Codeforces OAuth, create or join duel rooms, solve a Codeforces problem live, and the first to get Accepted wins. Supports rated 1v1, unrated team (4v4), and free-for-all modes with ELO-style rating, E2E encrypted chat, Monaco editor, leaderboard, achievements, and friends.

The build uses **maximum parallelism via Claude subagents**: each self-contained module is implemented by a dedicated agent working concurrently with others in the same phase. Custom project-level skills are created upfront to eliminate repeated scaffolding work across agents.

---

## Tech Stack (Final)

### Backend
| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x (Java 17) |
| WebSocket | Spring WebSocket + STOMP |
| Security | Spring Security 6, OAuth2 Client |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Cache | Redis 7 (sessions, rate limiting, presence) |
| Build | Gradle |
| External API | Codeforces REST API v1 |
| Scheduling | Spring `@Scheduled` |
| Docs | SpringDoc OpenAPI 3 |

### Frontend
| Layer | Technology |
|---|---|
| Framework | **Next.js 15 (App Router)** + TypeScript |
| Routing | File-based App Router (`app/` directory) |
| State | Zustand |
| WebSocket Client | SockJS + STOMP.js |
| HTTP Client | Axios |
| UI Components | shadcn/ui + Tailwind CSS |
| Charts | Recharts |
| Code Editor | Monaco Editor (`@monaco-editor/react`) |
| Theme | `next-themes` |
| Forms | React Hook Form + Zod |
| Build | Next.js (Turbopack) |

---

## Pre-Implementation: Custom Skills & Subagents Explained

### Custom Skills (you create manually)

Skills are Markdown files in `.claude/commands/` — each becomes a `/skill-name` slash command usable in any Claude Code session. Create these before coding starts:

| File | Slash command | Purpose |
|---|---|---|
| `.claude/commands/migrate.md` | `/migrate` | Run `./gradlew flywayMigrate` |
| `.claude/commands/seed.md` | `/seed` | Seed achievements table and test users |
| `.claude/commands/test-backend.md` | `/test-backend` | JUnit + MockMvc with Testcontainers |
| `.claude/commands/test-frontend.md` | `/test-frontend` | Run `npm run test` (Jest/Vitest) |
| `.claude/commands/ws-smoke.md` | `/ws-smoke` | STOMP WebSocket smoke test via wscat |
| `.claude/commands/cf-api-check.md` | `/cf-api-check` | Validate CF API key + check rate limit |

### Subagents (Claude spawns automatically)

Subagents are **not configured by you** — Claude Code spawns them programmatically during implementation via its `Agent` tool. When a phase has multiple parallel modules, Claude sends a single message launching multiple agent instances simultaneously, each scoped to its own files. You just say "implement Phase 1" and Claude handles the parallelism. No manual setup needed.

---

## Subagent Strategy

Every phase below launches **parallel agents** (one per module) in a single message. Each agent receives:
- Its module's section of the spec
- The shared project structure conventions
- File paths it owns and must NOT overlap with other agents
- A checklist of deliverables to confirm before finishing

The orchestrating session (this one) only integrates outputs, resolves conflicts, and advances phases. No feature code is written in the orchestrating session.

---

## Phase 1 — Foundation (3 parallel agents)

**Agent A — Infra & Config**
- `docker-compose.yml`: PostgreSQL 16, Redis 7, Judge0 (from `judge0/judge0` image), Nginx
- `nginx/nginx.conf`: reverse proxy, SSL placeholders, security headers from §17
- `backend/build.gradle`: Spring Boot 3.x, Spring Security, Spring WebSocket, Spring Data JPA, Spring Session Redis, SpringDoc OpenAPI, Flyway, Lombok, Testcontainers (test scope)
- `backend/src/main/resources/application.yml`: all env-var bindings from §21, Redis session store config, CORS origin from `FRONTEND_ORIGIN`
- `.env.example`: all variables from §21

**Agent B — Database & Auth Backend**
- Flyway migration `V1__init_schema.sql`: all tables from §5 in dependency order
- `V2__seed_achievements.sql`: all 17 achievement rows from §13
- `SecurityConfig.java`: OAuth2 client registration, permit/auth rules, CSRF config, session cookie flags
- `OAuth2SuccessHandler.java` + `CustomOAuth2UserService.java`: upsert User on login, redirect to `/dashboard`
- `User.java` entity + `UserRepository.java` + `UserService.java` (findByHandle, upsert from OAuth)
- `UserController.java`: `/api/auth/me`, `/api/auth/logout`, `/api/users/{handle}`, `PATCH /api/users/me`, search

**Agent C — Next.js 15 Frontend Scaffold**
- `create-next-app` with TypeScript, Tailwind CSS, App Router (`app/` directory)
- shadcn/ui init, `next-themes` ThemeProvider in root `app/layout.tsx`
- App Router file structure:
  ```
  app/
  ├── layout.tsx               (root: ThemeProvider, auth context)
  ├── page.tsx                 (Landing — SSR + generateMetadata for OG)
  ├── login/page.tsx
  ├── dashboard/page.tsx
  ├── duel/create/page.tsx
  ├── duel/[roomCode]/page.tsx ('use client' — WebSocket + Monaco)
  ├── u/[handle]/page.tsx      (SSR + generateMetadata for profile SEO)
  ├── leaderboard/page.tsx
  ├── friends/page.tsx
  ├── settings/page.tsx
  ├── onboarding/page.tsx
  ├── terms/page.tsx
  └── privacy/page.tsx
  ```
- `middleware.ts`: Next.js middleware protects `/dashboard`, `/duel/*`, `/friends`, `/settings`, `/onboarding` — redirects to `/login` if no session cookie
- Zustand `authStore.ts`: user state, login/logout actions
- Axios `lib/api.ts`: base URL from `NEXT_PUBLIC_API_BASE_URL`, `withCredentials: true`
- `useAuth.ts` hook: fetches `/api/auth/me` on mount, populates store
- Landing `app/page.tsx` with 5 sections from §19 + Codeforces OAuth button
- `app/login/page.tsx` with OAuth redirect button

Deliverable gate: `docker compose up` starts all services; landing page loads; `/oauth2/authorization/codeforces` redirect works.

---

## Phase 2 — Core Duel (4 parallel agents)

**Agent A — Duel REST API**
- `DuelRoom.java`, `DuelParticipant.java`, `DuelResult.java` entities
- `DuelRoomRepository.java`, `DuelParticipantRepository.java`
- `DuelService.java`: createRoom (generate 8-char roomCode), joinRoom, startRoom (validates host)
- `DuelController.java`: POST create, GET/POST join, POST start, GET problem — all from §6

**Agent B — WebSocket Infrastructure**
- `WebSocketConfig.java`: STOMP broker relay, `/ws` SockJS endpoint, auth interceptor validating session
- `DuelWebSocketController.java`: `@MessageMapping` for ready, resign, offer-draw, respond-draw, chat, heartbeat
- `PresenceService.java`: Redis keys `presence:{handle}`, heartbeat updates, `SessionDisconnectEvent` handler
- WS event model: sealed interface `DuelEvent` with all `eventType` subtypes from §7

**Agent C — CF API & Problem Selection**
- `CfApiClient.java`: `user.status`, `problemset.problems`, `user.rating`, `user.info` — sequential executor with 1100ms minimum interval between calls, retry on 429
- `ProblemSelectionService.java`: full algorithm from §10 including fallback (±100 rating range)
- Redis caching: `cf:problems:{rating}` (1h TTL), `cf:solved:{handle}` (5min TTL)
- `VerdictPollingService.java`: `@Scheduled(fixedDelay=5000)`, per-room sequential poll, broadcasts `DUEL_ENDED` on Accepted

**Agent D — Duel Room Frontend (Next.js)**
- `app/duel/[roomCode]/page.tsx`: `'use client'` — full layout from §18 (problem panel + editor + team bar + chat)
- `useWebSocket.ts`: SockJS + STOMP connection, subscribe to `/topic/duel/{roomCode}` and `/queue/user`
- `useDuel.ts`: Zustand-backed duel state, handles all `eventType` dispatches
- `duelStore.ts`: room state, participants, problem, timer
- `components/duel/DuelTimer.tsx` (counts up from 0), `TeamSlotSelector.tsx` (join screen layout from §18)
- `app/duel/create/page.tsx`: form with type selector and problem rating slider (800–3500)

Deliverable gate: two users can create, join, and start a rated 1v1 duel; problem appears; submitting Accepted on CF ends the duel within 10 seconds.

---

## Phase 3 — Rating & Profiles (2 parallel agents)

**Agent A — Rating System**
- `RatingService.java`: full ELO algorithm from §9 (dynamic K, problem difficulty modifier, floor 100)
- `RatingHistory.java` entity + repository
- Wire into `DuelService.java`: update ratings atomically after `DUEL_ENDED`, insert `rating_history`, insert `match_history`
- `MatchHistory.java` entity + repository

**Agent B — Profile Frontend (Next.js)**
- `app/u/[handle]/page.tsx`: SSR with `generateMetadata()` for Open Graph + JSON-LD structured data
- `components/profile/RatingGraph.tsx`: Recharts LineChart — duel rating over time
- CF rating graph from `/api/users/{handle}/cf-rating-history` proxy endpoint
- `components/profile/MatchHistory.tsx`: paginated table (date, opponent, problem link, rating delta, result, duration)
- `components/profile/AchievementBadge.tsx`: grid of badges, greyed if unearned, tooltip on hover
- `app/onboarding/page.tsx`: 3-step modal on first login

Deliverable gate: after a rated duel, both players' profiles show updated rating graph and match history entry.

---

## Phase 4 — Team Duels, FFA & Leaderboard (2 parallel agents)

**Agent A — Team/FFA Duel Logic (Backend)**
- Extend `DuelService.java` for `UNRATED_TEAM` and `UNRATED_FFA` room types
- Team win condition: first team with any Accepted member wins
- FFA: first individual Accepted wins, `unrated_wins` incremented
- `LeaderboardController.java`: all 4 categories from §13 with pagination (OFFSET query)

**Agent B — Team/FFA Frontend + Leaderboard (Next.js)**
- `components/duel/TeamSlotSelector.tsx`: full 2×4 slot grid for team duels, FFA variant (no team labels)
- `app/leaderboard/page.tsx`: can use SSR for initial render; tab switcher for 4 categories, ranked rows

Deliverable gate: 2v2 team duel completes correctly; leaderboard shows live rankings.

---

## Phase 5 — Chat & Editor (2 parallel agents)

**Agent A — Encrypted Chat**
- `ChatKeyService.java`: store/retrieve ECDH public keys from `chat_keys` table; broadcast all room keys on join
- `POST /api/duels/{roomCode}/chat-key` REST endpoint
- WS relay: validate sender is participant, Redis rate-limit (60 msg/min, token bucket), store ciphertext in Redis (24h TTL), broadcast to `/topic/duel/{roomCode}/chat`
- `encryption.ts` (`lib/`): full ECDH key-gen, shared-key derivation, AES-GCM encrypt/decrypt from §11
- `useChatEncryption.ts`: hook managing key pair lifecycle (ephemeral, in-memory only)
- `ChatPanel.tsx`: message list with sender handles, send input; transparent to plaintext

**Agent B — Monaco Editor + Code Runner**
- `CodeEditor.tsx`: Monaco with language dropdown (6 languages from §12), dark/light theme sync
- `CodeRunController.java`: proxy to Judge0 `POST /submissions`, rate-limit 10 runs/min per user
- `POST /api/code/run` endpoint: 10s timeout, 64KB size limit, return stdout/stderr/exitCode/ms
- "Submit on Codeforces" button linking to `https://codeforces.com/problemset/problem/{contestId}/{index}`
- Judge0 service added to `docker-compose.yml` (already scaffolded in Phase 1)

Deliverable gate: chat messages are encrypted end-to-end (confirmed via network inspection); code runs against Judge0; Monaco renders with syntax highlighting.

---

## Phase 6 — Social & Disconnection (2 parallel agents)

**Agent A — Friends System**
- `Friend.java` entity + `FriendRepository.java`
- `FriendController.java`: all endpoints from §14 (request, accept, remove, list, incoming)
- WS notifications to `/queue/user`: `FRIEND_REQUEST` and `FRIEND_REQUEST_ACCEPTED`
- `app/friends/page.tsx`: friends list with online indicator (from Redis presence), CF + duel rating, last seen
- Friend online status fetched from `PresenceService` via friends list endpoint

**Agent B — Disconnection Handling + Achievements**
- Extend `PresenceService.java`: Redis key `disconnect:{roomCode}:{userId}` with 30s TTL on `SessionDisconnectEvent`; `@Scheduled` task checks expired disconnect keys and forfeits rated duels
- Broadcast `PLAYER_DISCONNECTED` (with gracePeriodSeconds=30) and `PLAYER_RECONNECTED` events
- `AchievementService.java` + `AchievementChecker.java`: `ApplicationEventListener` on duel result; checks all 17 conditions; inserts `user_achievements` and updates user stats (streak, fastest solve, etc.)
- `AchievementController.java`: `GET /api/achievements`
- Disconnection countdown UI on `DuelRoomPage.tsx`

Deliverable gate: browser close during rated duel triggers forfeit after 30s; achievements award correctly after first win.

---

## Phase 7 — Security Hardening & Polish (3 parallel agents)

**Agent A — Rate Limiting & Security**
- Redis token bucket middleware: 100 API req/min per user (Spring HandlerInterceptor)
- CF handle regex whitelist validation (`@Pattern`) on all relevant request bodies
- `Content-Security-Policy` + all headers from §17 in Nginx config
- Session cookie: `Secure; HttpOnly; SameSite=Lax`
- WebSocket: message size limit 10KB in `WebSocketConfig.java`

**Agent B — Frontend Polish & Accessibility (Next.js)**
- `app/settings/page.tsx`: bio + favorite language form
- Dark/light theme toggle in layout with `next-themes` (already wired in Phase 1)
- Mobile-first responsive pass: all pages at sm/md/lg breakpoints from §18
- ARIA labels, keyboard navigation, focus rings, `prefers-reduced-motion` for all animations
- `app/terms/page.tsx` + `app/privacy/page.tsx` (placeholder content)

**Agent C — SEO, Performance & Observability**
- `app/sitemap.ts`: Next.js native sitemap generation for public routes
- `app/robots.ts`: Next.js native robots.txt
- Open Graph + JSON-LD already set up in Phase 3 via `generateMetadata` — verify coverage on all public pages
- `next/image` with WebP for all images (automatic in Next.js); dynamic imports for Monaco and Recharts via `next/dynamic`
- `spring-boot-starter-actuator` + Micrometer: `/actuator/health` and `/actuator/metrics`
- SpringDoc Swagger UI at `/swagger-ui.html`

Deliverable gate: Lighthouse ≥ 90 across all categories; security headers confirmed via curl; all rate limits tested via scripts.

---

## Critical Files Index

| Area | Key files |
|---|---|
| DB schema | `backend/src/main/resources/db/migration/V1__init_schema.sql` |
| OAuth | `SecurityConfig.java`, `OAuth2SuccessHandler.java`, `CustomOAuth2UserService.java` |
| Duel core | `DuelService.java`, `DuelWebSocketController.java`, `VerdictPollingService.java` |
| Rating | `RatingService.java` (full ELO from §9) |
| Problem engine | `ProblemSelectionService.java`, `CfApiClient.java` |
| Chat encryption | `lib/encryption.ts`, `useChatEncryption.ts`, `ChatKeyService.java` |
| WS frontend | `hooks/useWebSocket.ts`, `hooks/useDuel.ts`, `store/duelStore.ts` |
| Auth guard | `middleware.ts` (Next.js route protection) |
| SSR + SEO | `app/page.tsx`, `app/u/[handle]/page.tsx` (generateMetadata) |
| Env vars | `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_WS_URL` (prefix required for client-side) |
| Infrastructure | `docker-compose.yml`, `nginx/nginx.conf`, `application.yml` |

---

## Verification Plan

1. **Phase 1**: `docker compose up` green; `/` loads; CF OAuth redirect fires
2. **Phase 2**: Two browser sessions complete a rated 1v1 duel end-to-end
3. **Phase 3**: Profile rating graph updates correctly after 3 duels
4. **Phase 4**: 2v2 team duel; leaderboard ranks update live
5. **Phase 5**: Network tab shows only ciphertext; Judge0 code run returns output
6. **Phase 6**: Browser kill → 30s countdown → forfeit; achievement triggers on first win
7. **Phase 7**: `curl -I` shows all security headers; Lighthouse audit ≥ 90
