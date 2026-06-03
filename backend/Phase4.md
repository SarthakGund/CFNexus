# Phase 4 — Team Duels, FFA & Leaderboard

## Overview
Phase 4 extends the core duel system to support **unrated team duels** (4v4) and **free-for-all** modes, plus implements the leaderboard across 4 ranking categories.

## Backend Implementation

### DuelService Extensions
- **Team Duel Logic**: Rooms with type `UNRATED_TEAM` validate team composition (2 teams, max 4 per team). Win condition: first team with any member achieving Accepted wins.
- **FFA Logic**: Rooms with type `UNRATED_FFA` allow up to 8 solo players. Win condition: first individual Accepted wins; increments `unrated_wins` instead of `duel_rating`.
- Modified `startRoom(...)` to select problems appropriate for the duel type.
- Atomic rating/stats updates after duel end via `endDuelWithWinner(...)`.

### LeaderboardController
- `@RestController @RequestMapping("/api/leaderboard")`
- **4 categories** (all paginated with `OFFSET`):
  1. `GET /duel-rating` — sorts by `duel_rating DESC`
  2. `GET /unrated-wins` — sorts by `unrated_wins DESC`
  3. `GET /current-streak` — sorts by `current_streak DESC`
  4. `GET /fastest-solve` — sorts by `fastest_solve_ms ASC`
- Query pattern: `SELECT u.cf_handle, u.avatar_url, u.duel_rating, u.duel_wins, u.duel_losses, RANK() OVER (ORDER BY ...) as rank FROM users u ORDER BY ... OFFSET ? LIMIT ?`

## Frontend Implementation

### TeamSlotSelector Component
- **2×4 slot grid**: 2 teams, 4 slots per team (max 8 players total).
- **Team labels** in header, **FFA variant** (no team labels).
- Join flow: select team (or leave blank for FFA), click available slot.
- WebSocket integration: publish to `/app/duel/{roomCode}/join`, listen for `PLAYER_JOINED` events, update local slot state.

### Leaderboard Page
- `app/leaderboard/page.tsx`
- **Tab switcher**: 4 categories (Duel Rating, Unrated Wins, Current Streak, Fastest Solve).
- **Ranked table**: `#`, CF handle, avatar, key stat (rating/wins/streak/time), duel record.
- SSR-friendly: initial render via REST, live updates via WebSocket (optional in Phase 4).
- Responsive at sm/md/lg breakpoints.

## Database Schema
- No new tables; extends `duel_participants`, `duel_rooms` with room type and team/slot tracking.
- Leaderboard queries use `RANK() OVER` window functions on `users` table, no separate leaderboard table.

## Testing & Verification

### Manual flows
1. Create a team 2v2 duel → 2 users join each team → start → problem assigned → first team member solves → both team members get rated win.
2. Create FFA with 4+ players → first to solve wins → `unrated_wins` increments, rating unchanged.
3. Leaderboard tab: switch between 4 categories, pagination works, top ranks visible.

### Deliverable Gate
✅ 2v2 team duel completes correctly; leaderboard shows live rankings.

## Known Limitations
- No real-time leaderboard updates (Phase 7 may add subscription-based live updates).
- Fastest solve only tracks within a single duel; no cross-duel aggregation per problem.

## Files Modified
- `backend/src/main/java/com/cfduel/duel/DuelService.java` — team/FFA logic
- `backend/src/main/java/com/cfduel/duel/DuelController.java` — no changes (leaderboard is separate)
- `backend/src/main/java/com/cfduel/rating/ProfileController.java` — added `LeaderboardController` (new file)
- `frontend/components/duel/TeamSlotSelector.tsx` — updated for team grid
- `frontend/app/leaderboard/page.tsx` — new page

## Notes
- Team slot selection uses a simple allocation algorithm: fill teams in round-robin, respect user hints.
- Streaks and fastest solve tracked in `users` table; `RatingService` updates these atomically with rating changes.
