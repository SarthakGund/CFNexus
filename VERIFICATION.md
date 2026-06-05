# CFNexus — Prototype Verification (plan-v2)

Runtime light-up of the full stack on Windows 11 + Docker Desktop (WSL2),
JDK 17, profile `local`. Each stage below records its checks as
**PASS / FAIL / DEFERRED** with the exact commands used.

Date run: 2026-06-05.

---

## Summary

| Stage | Area | Result |
|-------|------|--------|
| 0 | Infra (Postgres + Redis) | **PASS** |
| 1 | Backend boots clean | **PASS** |
| 2 | Frontend runs + wired | **PASS** |
| 3 | Auth (dev-login path) | **PASS** |
| 4 | Core duel loop + persistence | **PASS** |
| 5a | Chat E2E (ciphertext relay) | **PASS** |
| 5b | Monaco editor | **PASS** (wired/serves) |
| 5c | Code-run (Judge0) | **DEFERRED** (host sandbox limit) |

The core duel loop — the heart of the prototype — completes end-to-end and
persists ratings. Code execution via Judge0 is wired correctly but blocked by a
Docker-Desktop/Windows sandbox limitation (details in Stage 5c).

---

## Stage 0 — Infrastructure — **PASS**

```powershell
Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"   # if engine down
docker compose up -d postgres redis
docker compose ps
```
- `cfnexus-postgres` → Up (healthy)
- `cfnexus-redis` → Up (healthy)

## Stage 1 — Backend boots clean — **PASS**

```powershell
cd backend
mvn -q "-Djava.version=17" "-DskipTests" package            # compiles (JDK 17 override)
mvn "-Djava.version=17" spring-boot:run "-Dspring-boot.run.profiles=local"
```
- `GET /actuator/health` → `{"status":"UP"}`
- `GET /api/leaderboard?type=duel_rating&size=5` → `200` (Flyway V1–V3 applied; data present)
- `GET /swagger-ui.html` → `200`

> Note (PowerShell): the `-D` args must be **quoted** (`"-Djava.version=17"`),
> otherwise PowerShell mangles them into an invalid Maven lifecycle phase.

## Stage 2 — Frontend runs + talks to backend — **PASS**

```powershell
cd frontend
npm run dev        # node_modules + .env.local already present
```
- `GET http://localhost:3000/` → `200`
- `GET http://localhost:3000/leaderboard` → `200`
- `GET http://localhost:8080/api/auth/me` (logged out) → `401` (expected)
- `.env.local` points at `:8080` (API/WS).

## Stage 3 — Auth — **PASS** (via dev-login)

Real Codeforces OAuth creds exist in `application-local.yml`. For single-machine,
two-player testing the **`local`-only dev-login** is used:

```
POST /api/dev/login?handle=alice   → 200, establishes session + Spring Security context
```
- Implemented by `DevAuthController` (`@Profile("local")`), guarded so it cannot
  ship to prod. Sets both the `userId` session attribute **and** the
  `SPRING_SECURITY_CONTEXT` (endpoints behind `.authenticated()` check the latter).

## Stage 4 — Core duel loop — **PASS**

Headless driver: `frontend/duel-driver.js` (two dev users, full rated 1v1 → resign).

```powershell
cd frontend
node duel-driver.js
```
Observed:
```
login alice: 200 duelRating=1200
login bob:   200 duelRating=1200
create: 200 roomCode=...           (RATED_1V1, problemRating 800)
join:   200 status=WAITING
start:  200
problem: 200  2205-A  (real CF problem fetched)
  -> sent resign (STOMP /app/duel/{room}/resign)
AFTER: alice duelRating=1226 wins=1 | bob duelRating=1174 losses=1
```
Persistence verified in Postgres:
```
duel_results   = 1 row
match_history  = 2 rows  (one per participant)
rating_history = 2 rows  (alice +26 → 1226, bob -26 → 1174)
```

WebSocket auth: STOMP CONNECT is authenticated from the shared HTTP session via
`HttpSessionHandshakeInterceptor` (copies `userId` into WS session attributes) +
a CONNECT `ChannelInterceptor` that attaches a `StompPrincipal`. Raw STOMP over
`/ws/websocket` works with the session cookie.

## Stage 5 — Feature smoke

### 5a. Chat E2E (ciphertext relay) — **PASS**
Headless test: `frontend/chat-smoke.js`.
```powershell
node chat-smoke.js
```
- Alice SENDs an opaque `{ciphertext, iv, senderPublicKeyB64}` to
  `/app/duel/{room}/chat`; Bob receives the **identical** payload on
  `/topic/duel/{room}/chat` (server is a pure relay — payload matches byte-for-byte).
- `GET /api/duels/{room}/chat-history` → `200`, backfills the ciphertext.
- Confirms spec §11: the server never sees or needs plaintext.

### 5b. Monaco editor — **PASS** (wired / serves)
- `components/duel/CodeEditor.tsx`: Monaco via SSR-disabled `next/dynamic`,
  6 languages with starter snippets, language switch, theme-aware, `Run` →
  `POST /api/code/run`, result panel (stdout/stderr/exit/time).
- `@monaco-editor/react` installed; duel page serves. (Visual render is
  browser-only and not captured headlessly, but the component is complete.)

### 5c. Code-run (Judge0) — **DEFERRED**
Judge0 stack brought up (`judge0`, `judge0-workers`, `judge0-db`, `judge0-redis`).
Backend → Judge0 wiring is **correct and verified**:
- `POST /api/code/run` (authed) → backend proxies to Judge0 `localhost:2358`
  with `X-Auth-Token`, `wait=true`; submission is accepted and the verdict is
  normalized back to the client (`200` with a `CodeRunResponse`).

Execution itself fails inside the sandbox. Direct submission returns:
```
status: 13 Internal Error
message: "No such file or directory @ rb_sysopen - /box/main.cpp"
```
`isolate --init` succeeds, but the worker cannot materialize the source file in
the isolate box — the well-known Judge0 limitation under Docker Desktop / WSL2
(**cgroup v2**; Judge0/isolate expect cgroup v1). This is a **host environment**
issue, not a code defect, and matches plan-v2's anticipated "Judge0 is heavy
locally → defer" path. Fixing it needs a host-level cgroup-v1 change, out of
scope for first light-up.

Test scripts: `frontend/coderun-smoke.js`.

---

## Known issues / limits

1. **Code-run execution deferred** — wiring proven; Judge0 isolate sandbox needs
   host cgroup-v1 (Docker Desktop/WSL2 provides v2). See Stage 5c.
2. **Committed real secrets** — `application-local.yml` contains real CF OAuth
   client secret + API secret. Before any public push: move to an untracked
   local file and **rotate**. (Housekeeping, flagged in plan-v2.)
3. **Live-update polish still pending** (plan-v2 "Live-update polish", §7/§19):
   - `/topic/leaderboard` live broadcast + frontend subscription.
   - Landing page top-5 from real API instead of hardcoded mock.
   - `/topic/presence/{handle}` live friend online status.
4. **Auth verified via dev-login, not a live CF OAuth round-trip** this run
   (creds are present; dev-login is the deterministic two-player path).

## Tooling / repro notes

- Build with **JDK 17** (`-Djava.version=17`); backend targets 21.
- Run profile `local` (disables secure cookies, supplies creds).
- PowerShell: quote `-D...` Maven args; native-exe `2>&1` wraps stderr — avoid.
- Helper scripts live in `frontend/`: `duel-driver.js`, `chat-smoke.js`,
  `coderun-smoke.js` (and exploratory `probe-ws.js` / `probe2.js`).
