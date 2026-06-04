# CFNexus — Plan v2: Get to a Working Prototype

## Goal

Stand up the whole stack locally and prove the **core duel loop works
end-to-end**, so there is a running, demoable prototype — not just code that
compiles. All 7 phases of `plan.md` are implemented; this plan is about *running*
it, finding what breaks at runtime, and fixing the minimum to get a clean demo.

**Definition of "working prototype":**
1. `docker compose up` brings Postgres + Redis (+ Judge0) healthy.
2. Backend boots, Flyway migrates, `/actuator/health` is UP.
3. Frontend builds and serves; landing + leaderboard render.
4. A user logs in via Codeforces OAuth and lands on `/dashboard`.
5. Two users create → join → start a **rated 1v1**; problem loads.
6. The duel **ends** (via resign/draw at minimum) → ratings update → profile
   graph + match history + leaderboard reflect the result.
7. Chat sends an encrypted message; Monaco renders; "Run" hits Judge0.

> Existing assets: `application-local.yml` already has real CF OAuth client
> creds + a CF API key; `frontend/.env.local` exists. So auth and CF problem
> fetch should work without new secrets.

---

## Constraints (from project memory)
- **Build with JDK 17**: only JDK 17 is installed locally; backend targets 21.
  Compile/run with the `-Djava.version=17` override (Maven, not Gradle).
- Backend run profile: `local` (`application-local.yml` disables secure cookies,
  supplies creds).

---

## Stage 0 — Stand Up Infrastructure
- Start datastores: `docker compose up -d postgres redis` (add `judge0
  judge0-db judge0-redis judge0-workers` for code-run, optional for first light-up).
- Confirm health: `docker compose ps` all healthy; `redis-cli -a … ping`.
- **Outcome:** dependencies reachable on localhost.

## Stage 1 — Backend Boots Clean
- Build: `mvn -q -Djava.version=17 -DskipTests package` from `backend/`.
- Run: `mvn -Djava.version=17 spring-boot:run -Dspring-boot.run.profiles=local`.
- Verify: Flyway applies V1–V3; `GET /actuator/health` → `UP`;
  `GET /swagger-ui.html` loads; `GET /api/leaderboard?type=duel_rating&size=5`
  returns 200 (empty list OK).
- Fix any boot-time failures (schema `validate` mismatches, bean wiring, Redis
  auth). **Record each fix.**
- **Outcome:** backend serving on :8080.

## Stage 2 — Frontend Runs & Talks to Backend
- `cd frontend && npm install && npm run dev` (copy `.env.example` →
  `.env.local` if missing).
- Verify: `/` and `/leaderboard` render; no CORS errors in console;
  `useAuth` hits `/api/auth/me` (401 when logged out is fine).
- **Outcome:** frontend on :3000 wired to backend.

## Stage 3 — Auth Works
- Click "Login with Codeforces" → OAuth round-trip → redirect to `/dashboard`
  with a populated user.
- If CF OAuth round-trip fails (creds/redirect-uri), debug using the TRACE
  logging already enabled in `application-local.yml`.
- **Risk mitigation — Dev login (optional but recommended):** add a
  `local`-profile-only endpoint `POST /api/dev/login?handle=foo` that upserts a
  fake `User` and establishes a session. This unblocks single-machine,
  two-player testing without two real CF accounts and removes the demo's hard
  dependency on CF being reachable. Guard with `@Profile("local")` so it can
  never ship to prod.
- **Outcome:** can reach authenticated pages as at least one user.

## Stage 4 — Core Duel Loop (the heart of the prototype)
- User A: `/duel/create` (RATED_1V1, pick rating) → waiting room, invite link.
- User B (2nd browser/profile, or dev-login): open invite → join.
- A starts → `DUEL_STARTED` broadcast → problem panel + Monaco render for both.
- **End the duel without needing a real CF submission:** A or B clicks
  **Resign** (or Draw) → `DUEL_ENDED` → result modal.
- Verify persistence: `duel_results` + `match_history` + `rating_history` rows
  written; both profiles show updated rating graph + match history; leaderboard
  shows the new standings (refresh OK for now).
- Fix any WS auth, broadcast, or rating-write bugs surfaced here.
- **Outcome:** full rated loop completes and persists.

## Stage 5 — Feature Smoke (chat, editor, code-run)
- Chat: send a message; confirm in the Network tab only ciphertext crosses the
  wire (validates §11 E2E).
- Editor: Monaco renders with syntax highlighting; language switch works.
- Code run: "Run" → Judge0 returns stdout/exit code (requires Stage 0 judge0
  services). If Judge0 is heavy locally, mark code-run as "deferred" and note it.
- **Outcome:** secondary features demoable or explicitly deferred.

## Stage 6 — Record Results
- Write `VERIFICATION.md`: each Stage's checks as PASS / FAIL / DEFERRED, with
  the exact commands used and any fixes applied.
- List remaining known issues so the prototype's limits are explicit.

---

## Live-update polish (only after the prototype runs — from the spec audit)
These are real spec gaps but **not blockers** for a working prototype; do them
after Stages 0–6 are green:
- `/topic/leaderboard` live broadcast + frontend subscription (§7).
- Landing page top-5 from real API instead of hardcoded mock (§19).
- `/topic/presence/{handle}` live friend online status (§7).

---

## Housekeeping (flag, don't block)
- `application-local.yml` contains **committed real secrets** (CF client secret,
  API secret). Before any public push, move these to an untracked `.env` /
  local-only file and rotate. Out of scope for first light-up, but note it in
  `VERIFICATION.md`.
- Commit the in-progress dashboard refactor (`dashboard-content.tsx`,
  `nav-actions.tsx`) before starting, so Stage work begins from a clean tree.

---

## Suggested execution
Stages 0–4 are largely sequential (each gates the next) and best driven in this
session with the `/run` and `/verify` skills. Stage 5 and the live-update polish
can fan out to parallel agents once the core loop is green.
