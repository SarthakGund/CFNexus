# Phase 3 — Rating & Profiles — Deliverables

## Backend (Rating System)

### Entities & Repositories
- ✅ `RatingHistory.java` + `RatingHistoryRepository` — tracks duel-rating points over time
- ✅ `MatchHistory.java` + `MatchHistoryRepository` — per-player duel records (Postgres `uuid[]` opponent array)

### Rating Engine
- ✅ `RatingService.java` — full ELO algorithm (spec §9)
  - Dynamic K-factor: 64 (first 10 games), 32 (up to 30), 16 (30+)
  - Problem-difficulty modifier: ±20% scaling on K, clamped to [0.5, 1.5]
  - Floor: 100 (ratings never drop below this)
  - `applyDecisive(winner, loser)` — mutates both users, records history
  - `applyDraw(playerA, playerB)` — symmetric rating adjustment

### Duel Integration
- ✅ `DuelService.endDuelWithWinner()` — wired RatingService
  - For `RATED_1V1`: runs ELO engine on win/loss/resign/disconnect, populates `duel_results` rating columns
  - For unrated modes: skips rating updates (Phase 4)
  - Atomic: all updates inside the existing `@Transactional` boundary

### Profile Read Endpoints
- ✅ `ProfileController.java`
  - `GET /api/users/{handle}/rating-history` — duel-rating points (oldest first)
  - `GET /api/users/{handle}/match-history?page=0&size=20` — paginated match history (newest first)
  - `GET /api/users/{handle}/cf-rating-history` — proxies Codeforces `user.rating` API
- ✅ `SecurityConfig` — permits public GET on profile paths (SSR/SEO requirement from spec §15)

### DTOs
- ✅ `RatingPointDto` — one chart point (rating, delta, timestamp)
- ✅ `MatchHistoryDto` — one match row with opponent handle resolution
- ✅ `PagedResponse<T>` — generic pagination envelope (items, page, size, totalElements, totalPages)

## Frontend (Profile & Onboarding)

### Profile Page (SSR)
- ✅ `app/u/[handle]/page.tsx` (rebuilt)
  - Server-side: `generateMetadata()` for OG + Twitter + robots
  - JSON-LD `ProfilePage` structured data
  - Header: avatar, CF handle, CF rank badge, duel-tier badge (9 tiers from §9), bio, language
  - Stats bar: W/L/D, current streak, unrated wins, fastest solve

### Charts (Client)
- ✅ `components/profile/RatingChart.tsx` — Recharts `LineChart` (lazy-loaded, SSR:false)
- ✅ `components/profile/RatingGraphs.tsx` — dual charts (duel rating + CF rating, side-by-side)
  - Fetches `/rating-history` and `/cf-rating-history` on mount
  - Graceful degradation on error

### Match History Table
- ✅ `components/profile/MatchHistory.tsx` (client)
  - Paginated table: date, opponent (linked to profile), problem (linked to CF), result, rating delta, duration
  - Page size 10, back/next controls
  - Color-coded result (green WIN, red LOSS/RESIGN, grey DRAW)

### Achievements
- ✅ `components/profile/AchievementBadge.tsx` + `AchievementsGrid.tsx`
  - Badge grid (6 cols on desktop, 4 on tablet, 3 on mobile)
  - Earned badges full-color; unearned greyed + grayscale
  - Gracefully degrades to "coming soon" until Phase 6 endpoints land

### Onboarding (First Login)
- ✅ `app/onboarding/page.tsx` (rebuilt)
  - 3-step modal: welcome → profile setup → how it works
  - Step 2: bio (textarea, 500 chars) + favorite language (select from 7 options)
  - Persists via `PATCH /api/users/me` (existing endpoint)
  - Redirects to `/dashboard` on finish

### Helpers & Styling
- ✅ `lib/rank.ts` — `rankTier(rating)` mapping for 9 tiers (spec §9)
- ✅ `lib/profile.ts` — types (ProfileDto, RatingPoint, MatchRow, PagedResponse) + SSR fetch + duration formatter
- ✅ `public/avatar-placeholder.svg` — grey avatar fallback
- ✅ Recharts dependency added (`npm install recharts@^2.15.0`)

## Build Status
- ✅ Backend: `mvn compile` — clean
- ✅ Frontend: `next build` — 13 routes, `/u/[handle]` dynamic, no errors

## Verification Gate
Two-user scenario (after Phase 2 works):
1. User A and User B complete a rated 1v1 duel
2. A wins; backend runs ELO: A gains +X rating, B loses -Y rating
3. `rating_history` + `match_history` rows inserted for both
4. Both users visit `/u/[userhandle]` → see updated rating graph with the new point, match history showing the duel, result badge
5. Streak, win count, fastest solve meter update (persisted in User entity)
