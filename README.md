<div align="center">

# ⚔️ CFNexus

**Real-time competitive programming duels for Codeforces users.**

Log in with Codeforces, challenge an opponent, and race to the first **Accepted** verdict —
live, with an ELO duel rating, end-to-end encrypted chat, an in-browser code editor, leaderboards, achievements, and friends.

[Tech Stack](#-tech-stack) · [Quick Start](#-quick-start) · [Architecture](#-architecture) · [Configuration](#-configuration) · [Project Layout](#-project-layout)

</div>

---

## ✨ Features

- **Rated 1v1 duels** — ELO-style duel rating with a dynamic K-factor, rating history, and rank tiers (Newbie → Grandmaster).
- **Unrated team duels** — asymmetric team play up to 4v4 (1v2, 2v3, 4v4, …).
- **Unrated free-for-all** — up to 4 individual players, first to solve wins.
- **Live verdict detection** — a scheduled poller watches the Codeforces API and ends the duel the moment someone gets `OK`.
- **Smart problem selection** — picks an unsolved, well-tested problem at the target rating for all participants (CF problemset, cached in Redis).
- **End-to-end encrypted chat** — ECDH (P-256) key exchange + AES-GCM in the browser; the server is a pure ciphertext relay and never sees plaintext.
- **In-browser code editor** — Monaco with 6 languages, theme-aware, with a sandboxed *Run* proxy (Piston).
- **Draw offers, resign, and disconnect handling** — grace period with reconnect, forfeit-on-disconnect for rated games.
- **Profiles, leaderboards & achievements** — duel + CF rating graphs, match history, badge grid, and multiple leaderboard categories.
- **Friends system** — requests, accepts, and live online presence.

---

## 🧱 Tech Stack

| Layer | Backend | Frontend |
|---|---|---|
| **Language** | Java 21 (builds on JDK 17) | TypeScript |
| **Framework** | Spring Boot 3.3 | Next.js 15 (App Router) · React 19 |
| **Realtime** | Spring WebSocket + STOMP | SockJS + `@stomp/stompjs` |
| **Auth/Security** | Spring Security 6 · OAuth2 Client (Codeforces) | session cookie via middleware |
| **Data** | Spring Data JPA · PostgreSQL 16 · Flyway | Zustand (state) |
| **Cache / Sessions** | Redis 7 (Spring Session, rate limits, presence, chat relay) | — |
| **UI** | — | Tailwind CSS · shadcn/ui · Recharts · Monaco |
| **Forms / Validation** | Jakarta Bean Validation | React Hook Form + Zod |
| **Build** | Maven | npm |
| **Docs** | SpringDoc OpenAPI (Swagger UI) | — |

**Infrastructure:** Docker Compose · Nginx (reverse proxy / TLS) · [Piston](https://github.com/engineer-man/piston) for sandboxed code execution.

> ℹ️ The original [spec](codeforces-duel-platform-spec.md) proposed React + Vite / Gradle / Judge0. The shipped implementation uses **Next.js + Maven + Piston** instead (Piston runs on cgroup v2, so it works under Docker Desktop / WSL2 where Judge0's isolate sandbox does not).

---

## 🏗️ Architecture

```
                Browser (Next.js SPA)
                      │
      REST (HTTPS) ───┤───── WebSocket (WSS / STOMP)
                      │
                      ▼
            Spring Boot  (com.cfduel)
              │      │        │
   PostgreSQL │  Redis │  Codeforces API
   (durable)     (sessions,   (OAuth + problem
                  presence,    & verdict data)
                  rate limit,
                  chat relay)
                      │
                   Piston  (sandboxed code run)
```

- **Sessions, not JWT** — Redis-backed `HttpSession` so sessions can be invalidated server-side; WebSocket connections authenticate from the same session cookie.
- **Verdict polling** — a `@Scheduled` task queries `user.status` for active duels (rate-limited to respect CF's ~1 req/s), ending the room on the first `OK`.
- **E2E chat** — keys are exchanged client-side; the server stores and relays only opaque ciphertext (TTL'd in Redis).

---

## 🚀 Quick Start

### Prerequisites

- **Docker Desktop** (for Postgres, Redis, Piston, Nginx)
- **JDK 17** (the backend targets 21 but compiles with a `-Djava.version=17` override)
- **Maven** and **Node.js 20+**

### 1. Configure environment

```bash
cp .env.example .env   # then fill in real values (see Configuration below)
```

> Sensible localhost defaults are baked into `application.yml` via `${VAR:default}`, so the stack boots without a `.env` for everything except real Codeforces OAuth credentials.

### 2. Start infrastructure

```bash
docker compose up -d postgres redis
# optional, heavier:  docker compose up -d piston nginx
```

### 3. Run the backend

```bash
cd backend
mvn flyway:migrate                  # apply DB migrations
mvn spring-boot:run                 # serves http://localhost:8080
```

<details>
<summary><b>Windows / JDK-17-only note</b></summary>

If only JDK 17 is installed, override the target and **quote the `-D` args** (PowerShell otherwise mangles them):

```powershell
mvn "-Djava.version=17" spring-boot:run "-Dspring-boot.run.profiles=local"
```
</details>

- Swagger UI → http://localhost:8080/swagger-ui.html
- Health → http://localhost:8080/actuator/health

### 4. Run the frontend

```bash
cd frontend
npm install
npm run dev                         # serves http://localhost:3000
```

`frontend/.env.local` should point the API/WS URLs at `:8080`.

### 5. (Local) Dev login

For single-machine, two-player testing there's a `local`-profile-only dev login that bypasses the full OAuth round-trip:

```
POST /api/dev/login?handle=alice
```

It establishes both the session and the Spring Security context. Guarded by `@Profile("local")`, so it can't ship to production.

---

## ⚙️ Configuration

All secrets are supplied via environment variables — never hardcoded. Copy `.env.example` → `.env` and fill in:

```env
# Codeforces OAuth + signed API calls
CF_CLIENT_ID=your_cf_oauth_client_id
CF_CLIENT_SECRET=your_cf_oauth_client_secret
CF_API_KEY=your_cf_api_key
CF_API_SECRET=your_cf_api_secret

# Database
DB_URL=jdbc:postgresql://localhost:5432/cfduel
DB_USERNAME=cfduel
DB_PASSWORD=secret

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=redissecret

# App
SESSION_SECRET=32-char-random-secret
FRONTEND_ORIGIN=http://localhost:3000
CODE_RUNNER_URL=http://localhost:2000      # Piston

# Frontend (frontend/.env.local)
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
```

---

## 🔌 API Overview

REST is prefixed `/api`; authenticated routes require a valid session cookie.

| Area | Endpoints |
|---|---|
| **Auth** | `GET /api/auth/me`, `GET /api/auth/logout`, `GET /oauth2/authorization/codeforces` |
| **Users** | `GET /api/users/{handle}`, `…/rating-history`, `…/match-history`, `…/achievements`, `GET /api/users/search?q=`, `PATCH /api/users/me` |
| **Duels** | `POST /api/duels/create`, `GET /api/duels/{roomCode}`, `POST …/join`, `POST …/start`, `GET …/problem` |
| **Friends** | `GET /api/friends`, `GET /api/friends/requests`, `POST /api/friends/request/{handle}`, `POST …/accept/{handle}`, `DELETE …/{handle}` |
| **Leaderboard** | `GET /api/leaderboard?type=duel_rating\|unrated_wins\|streak\|fastest_solve` |
| **Code** | `POST /api/code/run` |

**WebSocket** (STOMP over SockJS at `/ws`):
subscribe to `/topic/duel/{roomCode}` (state), `/topic/duel/{roomCode}/chat` (encrypted), `/queue/user` (notifications);
send to `/app/duel/{roomCode}/{ready,resign,offer-draw,respond-draw,chat,heartbeat}`.

Full details live in the [specification](codeforces-duel-platform-spec.md).

---

## 📁 Project Layout

```
CFNexus/
├── backend/                     # Spring Boot (Maven) — com.cfduel
│   ├── src/main/java/com/cfduel/
│   │   ├── auth/   cf/   chat/   code/   config/
│   │   ├── duel/   friend/  leaderboard/  rating/
│   │   ├── user/   ws/   achievement/  health/
│   │   └── CfDuelApplication.java
│   └── src/main/resources/      # application.yml + Flyway migrations
├── frontend/                    # Next.js 15 (App Router) + React 19
│   ├── app/                     # routes (dashboard, duel, u/[handle], …)
│   ├── components/              # duel, profile, leaderboard, ui (shadcn)
│   ├── hooks/  lib/  store/     # WS/auth/duel hooks, api client, Zustand
│   └── *.js                     # headless smoke scripts (duel/chat/coderun)
├── nginx/                       # reverse proxy + security headers
├── docker-compose.yml
├── codeforces-duel-platform-spec.md   # full design spec
└── VERIFICATION.md              # end-to-end light-up results
```

---

## ✅ Verification

The core duel loop is verified end-to-end — see [VERIFICATION.md](VERIFICATION.md). Headless smoke scripts in `frontend/` drive the real stack:

```bash
cd frontend
node duel-driver.js     # full rated 1v1 loop (create → join → start → resign), asserts rating deltas
node chat-smoke.js      # E2E chat: ciphertext relayed byte-for-byte
node coderun-smoke.js   # /api/code/run proxy
```

> **Known limit:** under Docker Desktop / WSL2 (cgroup v2), Judge0's isolate sandbox can't materialize source files. The project uses **Piston** instead, which supports cgroup v2. See VERIFICATION.md §5c for the history.

---

## 🔒 Security

- Session cookies are `Secure`, `HttpOnly`, `SameSite=Lax`; HTTPS/WSS enforced via Nginx in production.
- CSRF enabled for REST, with security headers (HSTS, CSP, `X-Frame-Options`, …) at the Nginx layer.
- Per-user Redis rate limiting (API, chat, code-run); all request bodies validated with Jakarta Bean Validation.
- Chat is end-to-end encrypted — the server never holds decryption keys.

> ⚠️ Before any public deployment, ensure no real secrets are committed (move `application-local.yml` secrets to an untracked file and **rotate** any that were ever committed).

---

<div align="center">
<sub>Built on the Codeforces API · See the <a href="codeforces-duel-platform-spec.md">full specification</a> for design rationale.</sub>
</div>
