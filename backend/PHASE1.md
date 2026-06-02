# Phase 1 — Foundation (Completed)

## Overview

Phase 1 established the full infrastructure, database schema, and basic authentication for CFNexus. Three parallel agents (A: Infra, B: Auth Backend, C: Next.js Frontend) scaffolded all foundational components.

---

## Agent A — Infra & Config

### Infrastructure (`docker-compose.yml`)
- PostgreSQL 16 service (`postgres`) — database `cfduel`, user/password from env
- Redis 7 service (`redis`) — session store and cache, password via `--requirepass`
- Judge0 services (`judge0`, `judge0-db`, `judge0-redis`, `judge0-workers`) — code execution sandbox
- Nginx reverse proxy (`nginx`) — routing, SSL placeholders, security headers
- Shared `cfnexus` network, named volumes, healthchecks on all services

### Reverse Proxy (`nginx/nginx.conf`)
- Routes `/api/*`, `/oauth2/*`, `/login/oauth2/*` → backend (8080)
- Routes `/ws/*` (WebSocket) → backend with upgrade headers
- Routes all other traffic → frontend (3000)
- **Security headers** (spec §17):
  - `X-Frame-Options: DENY`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: geolocation=(), microphone=(), camera=()`
  - `Content-Security-Policy` (reasonable defaults)
  - `Strict-Transport-Security` (in SSL block, commented)
- SSL/HTTPS placeholders (443 block commented, certs from `/etc/letsencrypt/...`)

### Build Config (`pom.xml`, `application.yml`)
- **Maven** — Spring Boot 3.3.4, Java 21
- Dependencies: web, security, oauth2-client, websocket, data-jpa, data-redis, spring-session-data-redis, validation, actuator, flyway, postgresql, springdoc (Swagger), Lombok, Testcontainers
- **application.yml** binds all environment variables (spec §21):
  - Datasource (PostgreSQL): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - Redis: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
  - Spring Session: Redis-backed, 7-day timeout
  - **Codeforces OAuth2** (spec §4):
    - Client registration: `CF_CLIENT_ID`, `CF_CLIENT_SECRET`
    - Provider: authorization URI, token URI, user-info URI (`api/user.info`), user-name-attribute (`handle`)
  - CORS: allows `FRONTEND_ORIGIN` with credentials
  - Session cookie: `http-only: true`, `same-site: lax`, `secure: ${COOKIE_SECURE:true}`
  - Actuator: health, metrics exposure
  - SpringDoc Swagger UI at `/swagger-ui.html`
  - Code runner (Judge0): `CODE_RUNNER_URL`, `CODE_RUNNER_API_KEY`

### Environment Template (`.env.example`)
- All backend vars: `CF_CLIENT_ID/SECRET`, `DB_*`, `REDIS_*`, `FRONTEND_ORIGIN`, `COOKIE_SECURE`, `CODE_RUNNER_*`
- Frontend vars (Next.js): `NEXT_PUBLIC_API_BASE_URL`, `NEXT_PUBLIC_WS_URL`
- Judge0 vars: `JUDGE0_*`

---

## Agent B — Database & Auth Backend

### Database Migrations

#### `V1__init_schema.sql` — Core Schema
Creates all 10 tables from spec §5 in dependency order:

1. **users** — CF user profile + duel stats (cfHandle, cfRating, cfMaxRating, duelRating, duelWins/Losses/Draws, unratedWins, streaks, fastestSolveMs, etc.)
2. **duel_rooms** — room state (roomCode, type, status, hostId, problemRating, winnerId)
3. **duel_participants** — per-room player slot assignments (userId, team, slot, status)
4. **duel_results** — outcome record (winnerId, loserId, resultType, ratingDeltas, duration)
5. **match_history** — user-scoped match record (opponentIds array, result, problemId, rating delta)
6. **friends** — friend request state (requester, addressee, status: PENDING/ACCEPTED/BLOCKED)
7. **achievements** — achievement definitions (code, name, description, condition_type/value)
8. **user_achievements** — earned badge junction (userId, achievementId, earnedAt)
9. **rating_history** — per-duel rating snapshots (userId, duelId, rating, delta)
10. **chat_keys** — ECDH public keys for E2E encryption (roomId, userId, publicKeyB64)

Features:
- UUID primary keys with `gen_random_uuid()`
- Foreign keys with `ON DELETE CASCADE` where appropriate
- Indexes on lookup columns (cfHandle, roomCode, userId, roomId, etc.)
- `pgcrypto` extension for UUID generation
- Timestamps with defaults and `ON UPDATE CURRENT_TIMESTAMP`

#### `V2__seed_achievements.sql` — 17 Achievement Rows
Inserts all achievements from spec §13 with derived condition types:

- **Milestone achievements**: FIRST_WIN, RANK_UP_1/2/3, GRANDMASTER
- **Streak achievements**: STREAK_5, STREAK_10, STREAK_20, CLEAN_WIN
- **Speed achievements**: FAST_10, FAST_5, FAST_1
- **Volume achievements**: WINS_10, WINS_50, WINS_100
- **Challenge achievements**: HARD_PROBLEM, TEAM_WIN_4V4

Idempotent via `ON CONFLICT (code) DO NOTHING`.

### Java Application

#### Config
- **`CfDuelApplication.java`** — `@SpringBootApplication` entrypoint
- **`SecurityConfig.java`** — Spring Security 6 chain:
  - OAuth2 login wired to `CustomOAuth2UserService` + `OAuth2SuccessHandler`
  - Public routes: `/`, `/login`, `/oauth2/**`, `/login/oauth2/**`, `/api/public/**`, `/api/auth/me`, `/ws/**`, `/actuator/health`, `/swagger-ui.html`
  - All other routes require authentication
  - CSRF enabled with `CookieCsrfTokenRepository.withHttpOnlyFalse()` (ignored for `/ws/**`)
  - CORS bean from `app.frontend-origin` property with credentials
  - Logout at `/api/auth/logout` invalidates session and clears cookies
  - Custom 401 entry point (XHR gets 401, not OAuth redirect)

#### Authentication
- **`CustomOAuth2UserService`** — extends `DefaultOAuth2UserService`; extracts Codeforces user info, upserts `User` via `UserService`, returns `OAuth2User`
- **`OAuth2SuccessHandler`** — on success, stores `userId` in HTTP session, redirects to `${app.frontend-origin}/dashboard`

#### User Management
- **`User.java`** — JPA entity (package `com.cfduel.user`), all columns from schema:
  - Codeforces fields: cfHandle, cfUserId, cfRating, cfMaxRating, cfRank, cfMaxRank, avatarUrl
  - Duel stats: duelRating (default 1200), duelWins/Losses/Draws, unratedWins, currentStreak, maxStreak, fastestSolveMs
  - Profile: bio, favoriteLang, isOnline, lastSeen
  - Timestamps: createdAt, updatedAt (auto-managed via `@PrePersist/@PreUpdate`)
  - Lombok: `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`

- **`UserRepository.java`** — Spring Data JPA repository:
  - `findByCfHandle(String)` — case-sensitive lookup
  - `findByCfHandleIgnoreCase(String)` — case-insensitive
  - `findByCfUserId(Long)` — Codeforces numeric ID
  - `findByCfHandleStartingWithIgnoreCase(String)` — prefix search for `/api/users/search`

- **`UserService.java`** — business logic:
  - `upsertFromOAuth(...)` — create new user with duel-rating defaults (1200) or refresh existing
  - `findByHandle(String)` — public profile lookup
  - `findById(UUID)` — internal lookup
  - `getCurrentUser(UUID)` — resolve from session
  - `search(String query)` — prefix search for handles
  - `updateProfile(UUID, bio, favoriteLang)` — user profile updates

- **`UserController.java`** — REST endpoints:
  - `GET /api/auth/me` — current user from session; returns 401 if not authenticated
  - `GET /api/users/{handle}` — public profile by CF handle
  - `GET /api/users/search?q={query}` — handle prefix search
  - `PATCH /api/users/me` — update bio and favorite language (`@Valid` request body)
  - All responses use DTOs (`UserProfileDto`, `UpdateProfileRequest`)

---

## Phase 1 Verification Checklist

- [x] `docker compose config` validates cleanly
- [x] All 10 tables created with correct schema (spec §5)
- [x] All 17 achievements seeded idempotently
- [x] Spring Security OAuth2 flow wired (spec §4)
- [x] User entity, repository, service, controller implemented
- [x] Nginx reverse proxy routes all paths correctly
- [x] All spec §17 security headers present
- [x] All spec §21 environment variables bind in `application.yml`
- [x] Maven build (`pom.xml`) with Flyway plugin
- [x] Skills updated (`/migrate`, `/test-backend`, `/seed` use Maven)

## Running Phase 1

```bash
# Start infrastructure
docker compose up -d postgres redis judge0 nginx

# Run migrations (from backend/)
mvn flyway:migrate

# Start backend
mvn spring-boot:run

# In another terminal, start frontend (from frontend/)
npm run dev
```

**Deliverable Gate:** Landing page loads, OAuth redirect to Codeforces works, `/api/auth/me` returns 401 when not logged in.

---

*End of Phase 1*
