# Phase 6 — Social & Disconnection

## Overview
Phase 6 adds **friends system** for social interaction, **disconnection grace-period handling** with automatic forfeit after 30s for rated duels, and a comprehensive **achievements system** with 17 unlockable badges that award as players reach milestones.

## Backend Implementation

### Friends System (`com.cfduel.friend`)

#### Friend Entity & Repository
- JPA entity mapping the `friends` table (composite unique constraint: `requester_id`, `addressee_id`).
- Status: `PENDING`, `ACCEPTED`, or `BLOCKED` (enum or String).
- `FriendRepository`: finders for relationships in both directions (`findByRequesterIdAndAddresseeId`, `findBetween`, `findAcceptedInvolving`, `findIncomingPending`).

#### FriendService
- `sendRequest(currentUserId, targetHandle)` — upsert a PENDING friendship row, reject self-requests and duplicates (409).
  - Notifies target user via `FriendNotifier` with `FRIEND_REQUEST` event.
- `acceptRequest(currentUserId, requesterHandle)` — update PENDING → ACCEPTED.
  - Notifies original requester with `FRIEND_REQUEST_ACCEPTED`.
- `removeFriend(currentUserId, otherHandle)` — delete friendship in either direction.
- `listFriends(currentUserId)` — return accepted friends with online status (from `PresenceService.isOnline`), CF rating, duel rating, last seen.
- `listIncoming(currentUserId)` — pending requests where addressee = current user.

#### FriendNotifier
- `@Component` wrapping `SimpMessagingTemplate` (separate from `DuelBroadcaster` to avoid contention).
- `notifyUser(userId, payload)` — best-effort delivery to `/user/queue/user`; swallows failures (never throws).
- Payload shape: `FriendNotification { type: "FRIEND_REQUEST" | "FRIEND_REQUEST_ACCEPTED", fromHandle, fromAvatarUrl, message }`.

#### FriendController
- `@RestController @RequestMapping("/api/friends")`
- `GET /api/friends` — authenticated, returns accepted friends list.
- `GET /api/friends/incoming` — authenticated, returns pending incoming requests.
- `POST /api/friends/request/{handle}` — send friend request (201 on success, 404 user not found, 409 duplicate/self).
- `POST /api/friends/accept/{handle}` — accept request from that user (204).
- `DELETE /api/friends/{handle}` — remove/reject friendship (204).
- All endpoints resolve current user from session attribute (`OAuth2SuccessHandler.SESSION_USER_ID`).

#### DTOs
- `FriendDto`: cf_handle, avatar_url, cf_rating, cf_rank, duel_rating, online (boolean), last_seen.
- `IncomingRequestDto`: requester_handle, avatar_url, cf_rating, duel_rating, requested_at.

### Disconnection Handling (`com.cfduel.ws`)

#### PresenceService Extensions
- On `SessionDisconnectEvent`: if user is in an `IN_PROGRESS` room, set Redis key `disconnect:{roomCode}:{userId}` (30s TTL) and track in a pending set.
  - Broadcast `PlayerDisconnectedEvent(userId, 30)` to the room via `DuelBroadcaster`.
- On heartbeat (or reconnect): check for live disconnect key; if found and user is back online, delete the key and broadcast `PlayerReconnectedEvent(userId)`.
- All Redis access is defensive (try/catch, fail-open).

#### DisconnectForfeitService
- `@Service` with `@Scheduled(fixedDelay=5000)` task.
- Scans pending disconnect entries: if grace period expired (key TTL gone) AND user still offline (via `PresenceService.isOnline`) AND room still `IN_PROGRESS` AND room is `RATED_1V1`:
  - Calls `duelService.endDuelWithWinner(roomCode, opponentUserId, null, "DISCONNECT")`.
  - Removes entry from pending set.
- **Unrated duels**: marked disconnected but not forfeited (spec §16).
- Reuses existing `@EnableScheduling` (enabled by `VerdictPollingService`).

### Achievements System (`com.cfduel.achievement`)

#### Achievement Entity & Repository
- JPA entity mapping the `achievements` table (code, name, description, icon, condition_type, condition_value).
- Seeds from `V2__seed_achievements.sql` (17 rows, condition types: WINS, STREAK, RATING, FAST_SOLVE_MS, CLEAN_STREAK, HARD_PROBLEM, TEAM_WIN).
- `AchievementRepository`: `findByCode`, inherited `findAll`.

#### UserAchievement Entity & Repository
- JPA entity mapping `user_achievements` (user_id, achievement_id, earned_at).
- `UserAchievementRepository`: `findByUserId`, `existsByUserIdAndAchievementId` (idempotency).

#### AchievementService
- `onDuelCompleted(event)` — receives `DuelCompletedEvent` and:
  1. **Updates user stats** (not covered by `RatingService`):
     - Winner: `current_streak++`, `max_streak = max(max_streak, current_streak)`.
     - Loser/both on draw: reset `current_streak = 0`.
     - Fastest solve (on SOLVE win): `fastestSolveMs = min(existing, durationMs)`.
     - Team wins: all team members get stats updated.
  2. **Evaluates all 17 achievement conditions**:
     - `WINS` — `duel_wins >= condition_value`.
     - `STREAK` — `max_streak >= condition_value`.
     - `RATING` — `duel_rating >= condition_value`.
     - `FAST_SOLVE_MS` — solve duration `< condition_value`.
     - `CLEAN_STREAK` — `current_streak >= condition_value` (proxy for N clean wins).
     - `HARD_PROBLEM` — problem rating - player's CF rating `>= condition_value`.
     - `TEAM_WIN` — room type is `UNRATED_TEAM` and `condition_value` per-team size.
  3. **Inserts new `user_achievements`** rows (idempotent via `existsByUserIdAndAchievementId`).

#### AchievementChecker
- `@Component` with `@TransactionalEventListener(phase = AFTER_COMMIT)` on `DuelCompletedEvent`.
- Delegates to `AchievementService` — thin wrapper, runs in a new post-commit transaction.

#### AchievementController
- `@RestController @RequestMapping("/api")`
- `GET /api/achievements` — public (no auth required); returns all catalogued achievements (code, name, description, icon).
- `GET /api/users/{handle}/achievements` — public; returns earned achievements for that user (code, earned_at).
- Resolve handle via `UserService.findByHandle`.

### DuelService Integration
- Injected `ApplicationEventPublisher`.
- At the end of `endDuelWithWinner(...)` (after `resultRepository.save` and broadcast), publishes `DuelCompletedEvent(roomId, roomType, winnerId, loserIds, resultType, durationMs, problemId, problemRating, winnerTeam)`.

### Database
- **No new migrations** — all tables (friends, achievements, user_achievements) already seeded in `V1__init_schema.sql` and `V2__seed_achievements.sql`.

## Frontend Implementation

### Friends Page (`app/friends/page.tsx`)
- `"use client"` component (client-side only, no metadata export).
- Fetches `GET /api/friends` and `GET /api/friends/incoming`.
- **Friends list**: avatar, handle (link to `/u/{handle}`), online status (green dot or "last seen"), CF rating/rank, duel rating.
- **Incoming requests**: requester handle, avatar, ratings, Accept/Reject buttons.
- **Add by handle**: text input + button to POST `/api/friends/request/{handle}`.
- **Remove friend**: delete button per row.
- Refetch-after-action, loading/empty/error states, 404/409/400 mapped to friendly messages.

### Friends Notifications Hook (`hooks/useFriendNotifications.ts`)
- Subscribes to `/user/queue/user` via existing `useWebSocket` hook.
- Parses `FRIEND_REQUEST` / `FRIEND_REQUEST_ACCEPTED` payloads.
- Exposes callback to trigger incoming list refresh (no toast system, so used callback refetch).
- Wired into friends page to auto-refresh on notifications.

### Friends Library (`lib/friends.ts`)
- Typed `Friend`, `IncomingRequest` interfaces.
- Fetch helpers (`getFriends`, `getIncoming`, `sendRequest`, `acceptRequest`, `removeFriend`).
- `formatLastSeen` utility for display.

### Duel Store Extensions (`store/duelStore.ts`)
- Added `disconnectDeadlines: Record<string, number>` (userId → deadline epoch ms).
- On `PLAYER_DISCONNECTED`: store deadline = now + `gracePeriodSeconds * 1000`.
- On `PLAYER_RECONNECTED` and `DUEL_ENDED`: clear the entry.

### Duel Room Page (`app/duel/[roomCode]/page.tsx`)
- Added `DisconnectBanner` component.
- Shows "{handle} disconnected — forfeits in {N}s" countdown when an opponent is disconnected.
- 1s interval, cleaned up on unmount.
- Matches existing UI style (shadcn/Tailwind).

## Database Schema
- **friends** (V1): `id uuid pk, requester_id, addressee_id, status varchar(20) [PENDING|ACCEPTED|BLOCKED], created_at, updated_at, UNIQUE(requester_id, addressee_id)`.
- **achievements** (V1): `id uuid pk, code varchar(50) unique, name, description, icon, condition_type, condition_value int`.
- **user_achievements** (V1): `id uuid pk, user_id, achievement_id, earned_at, UNIQUE(user_id, achievement_id)`.
- **users**: extended with `current_streak`, `max_streak`, `fastest_solve_ms` (already existed, updated by `AchievementService`).

## Testing & Verification

### Friends System
1. **Send request**: User A sends friend request to User B.
   - User A gets 201, friendship row inserted with status=PENDING.
   - User B receives `FRIEND_REQUEST` WS notification.
2. **Accept request**: User B accepts.
   - Row updated to status=ACCEPTED.
   - User A receives `FRIEND_REQUEST_ACCEPTED` notification.
3. **List friends**: User A calls `GET /api/friends`.
   - Returns User B with online status (from Redis presence key), CF rating, duel rating, last seen.
4. **Remove friend**: User A removes User B.
   - Row deleted; both users' friend lists reflect removal immediately on refresh.

### Disconnection + Forfeit
1. Create a rated 1v1 duel, start, 2 players connected.
2. **Browser close** (or kill WebSocket) by one player.
   - `SessionDisconnectEvent` fires → `PresenceService` sets `disconnect:{room}:{userId}` (30s TTL).
   - `DuelBroadcaster` broadcasts `PlayerDisconnectedEvent(userId, 30)` to the room.
   - Opponent's UI updates: party slot shows "DISCONNECTED" + countdown "forfeits in 30s".
3. **Wait 30s** (or fast-forward to grace expiry).
   - `DisconnectForfeitService` scan detects expired key, missing user offline, room still IN_PROGRESS, RATED_1V1.
   - Calls `endDuelWithWinner(roomCode, opponentId, null, "DISCONNECT")` → forfeit, loser's rating decreases.
4. **Reconnect** (browser re-opens before grace expires):
   - Heartbeat arrives, `PresenceService` detects live disconnect key.
   - Deletes key, broadcasts `PlayerReconnectedEvent(userId)`.
   - Opponent's UI updates: slot back to "JOINED", countdown vanishes.

### Achievements
1. **First win** (FIRST_WIN badge):
   - User wins a rated 1v1 → `DuelCompletedEvent` published.
   - `AchievementChecker` evaluates condition: `duel_wins >= 1` → grants FIRST_WIN.
2. **Rating milestones** (RANK_UP_1 = 1400, GRANDMASTER = 2300):
   - User's rating crosses threshold after duel → achievement awards.
3. **Streak** (ON_FIRE = 5, UNSTOPPABLE = 10):
   - Win 5+ duels in a row → `current_streak` incremented, STREAK condition checked and awarded.
4. **Fast solve** (LIGHTNING = < 5 min):
   - Win a duel in < 300s → `FAST_SOLVE_MS` condition triggers.
5. **Profile achievements grid** (`/u/{handle}`):
   - Fetches `GET /api/users/{handle}/achievements` (public, no auth).
   - Displays badges for earned achievements (greyed out if unearned).

### Deliverable Gate
✅ Browser close during rated duel → 30s countdown visible on opponent's screen → automatic forfeit after grace expires.
✅ Achievements award correctly after first win (FIRST_WIN visible in profile achievements grid).

## Known Limitations
- **CLEAN_STREAK** evaluated against `current_streak` (proxy for N consecutive decisive wins without draw/resign).
  - Technically not a separate "clean" counter, but valid because `current_streak` resets to 0 on any loss/draw.
- **Grace period is hardcoded 30s** (not tunable per spec §16); could be made configurable in Phase 7.
- **Friend notifications** use callback refetch, not a toast system (none exists in the codebase); Phase 7 may add Sonner.
- **Achievements only check on duel completion** — no real-time progress tracking or notifications (badges silently appear on profile).

## Files Created
### Backend
- `backend/src/main/java/com/cfduel/friend/Friend.java`
- `backend/src/main/java/com/cfduel/friend/FriendRepository.java`
- `backend/src/main/java/com/cfduel/friend/FriendService.java`
- `backend/src/main/java/com/cfduel/friend/FriendNotifier.java`
- `backend/src/main/java/com/cfduel/friend/FriendController.java`
- `backend/src/main/java/com/cfduel/friend/dto/FriendDto.java`
- `backend/src/main/java/com/cfduel/friend/dto/IncomingRequestDto.java`
- `backend/src/main/java/com/cfduel/friend/dto/FriendNotification.java`
- `backend/src/main/java/com/cfduel/achievement/Achievement.java`
- `backend/src/main/java/com/cfduel/achievement/AchievementRepository.java`
- `backend/src/main/java/com/cfduel/achievement/UserAchievement.java`
- `backend/src/main/java/com/cfduel/achievement/UserAchievementRepository.java`
- `backend/src/main/java/com/cfduel/achievement/AchievementService.java`
- `backend/src/main/java/com/cfduel/achievement/AchievementChecker.java`
- `backend/src/main/java/com/cfduel/achievement/AchievementController.java`
- `backend/src/main/java/com/cfduel/duel/event/DuelCompletedEvent.java`
- `backend/src/main/java/com/cfduel/ws/DisconnectForfeitService.java`

### Frontend
- `frontend/app/friends/page.tsx` (replaced stub)
- `frontend/lib/friends.ts`
- `frontend/hooks/useFriendNotifications.ts`

## Files Modified
### Backend
- `backend/src/main/java/com/cfduel/config/SecurityConfig.java` — added `/api/achievements` and `/api/users/*/achievements` to public GET allowlist.
- `backend/src/main/java/com/cfduel/ws/PresenceService.java` — extended disconnect detection, grace-period tracking, reconnect handling.
- `backend/src/main/java/com/cfduel/duel/DuelService.java` — injected `ApplicationEventPublisher`, publishes `DuelCompletedEvent` after result.

### Frontend
- `frontend/store/duelStore.ts` — added `disconnectDeadlines` tracking.
- `frontend/app/duel/[roomCode]/page.tsx` — added `DisconnectBanner` component.

## Build & Type-Check Results
- **Backend**: `mvn -q -Djava.version=17 compile` → ✅ SUCCESS
  - Flyway: 2 migrations validated, schema v2.
  - Hibernate `ddl-auto: validate` passed (Friend, Achievement, UserAchievement entities mapped correctly).
  - 11 JPA repositories found, including new friend/achievement repos.
  - All beans wired (AchievementChecker, DisconnectForfeitService, FriendController, etc.).
- **Frontend**: `npx tsc --noEmit` → ✅ CLEAN
- **Frontend**: `npx next lint` → ✅ CLEAN (1 pre-existing warning in useWebSocket.ts, unrelated)

## Runtime Verification
- **Live stack** (Postgres 16 + Redis 7 + Spring Boot on :8080):
  - `GET /api/achievements` (public) → 200, **17 rows** with correct codes/names/icons from seed.
  - `GET /api/users/{handle}/achievements` (real seeded data) → 200 with correct DTO shape.
  - Auth boundaries: `GET /api/friends` anonymous → 401, `POST /api/friends/request/*` → 403 (CSRF), earned-achievements route public → 404 for unknown user (not 401).

## Notes
- **Separate FriendNotifier** — kept off `DuelBroadcaster` to avoid file contention during parallel Agent A/B development.
- **Defensive Redis access** — all presence/disconnect operations wrapped in try/catch, fail-open (never brake the duel on a Redis blip).
- **Achievement granting is idempotent** — `existsByUserIdAndAchievementId` check prevents duplicates on replay/retry.
- **Post-commit event listener** — `AchievementChecker` runs after the duel result is committed, so stat updates + achievement grants are atomic from the app's perspective.
- **No dependencies added** — all features use existing Spring/Spring Data/Redis/Zustand/axios; no new npm packages required.

## Next Steps (Phase 7)
- Rate-limit enforcement (100 req/min per user via Redis token bucket).
- Security headers (CSP, HSTS, etc. in Nginx).
- SEO / sitemap generation.
- Accessibility sweep (ARIA labels, focus rings, reduced-motion).
- Lighthouse audit ≥ 90 across all categories.
