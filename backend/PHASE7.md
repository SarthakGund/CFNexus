# Phase 7 — Security Hardening & Polish — Deliverables

## Backend (Rate Limiting & Security)

### Global API Rate Limiting
- ✅ `config/ratelimit/ApiRateLimiter.java` — Redis fixed-window limiter (100 requests / 60 seconds per user)
  - Key format: `ratelimit:api:{userId}`, INCR + EXPIRE pattern
  - Fails open on Redis errors (transparent fallback to allow requests)
  - Matches existing `ChatRateLimiter` / `CodeRunRateLimiter` idiom exactly
- ✅ `config/ratelimit/ApiRateLimitInterceptor.java` — Spring `HandlerInterceptor` for `/api/**`
  - Resolves authenticated user from session (via `OAuth2SuccessHandler.SESSION_USER_ID`)
  - Skips unauthenticated requests (public reads permitted without rate limit)
  - Returns HTTP 429 + `Retry-After` header + JSON error body on limit exceeded
  - Registered only for authenticated paths
- ✅ `config/ratelimit/RateLimitWebConfig.java` — `WebMvcConfigurer` registering the interceptor
  - Applied to `/api/**`, excluding `/api/public/**` and `/api/auth/**`
  - Anonymous public reads (leaderboard, profile, achievements) not rate-limited

### Input Validation (CF Handle Whitelist)
- ✅ `config/ValidationExceptionHandler.java` — `@RestControllerAdvice` mapping `ConstraintViolationException` to HTTP 400
  - Narrow scope: only handles parameter-level `@Validated` violations
  - Allows Spring's default `@Valid @RequestBody` behavior (already returns 400) untouched
- ✅ Handle validation applied to all CF handle inputs via `@Pattern(regexp = "^[a-zA-Z0-9_\\-]{3,24}$")`
  - Updated controllers with class-level `@Validated` + path-variable pattern constraints:
    - `UserController.java` — `/users/{handle}` and `/users/search?q=` (search uses relaxed 1-24 chars for prefix matching)
    - `FriendController.java` — request/accept/remove handle path vars
    - `AchievementController.java` — `/users/{handle}/achievements` path var
    - `ProfileController.java` — rating-history / match-history / cf-rating-history handle vars
  - Request DTOs already had `@Pattern` (e.g., `CreateDuelRequest`, `CodeRunRequest`), left intact
  - Non-handle fields (room codes, UUIDs, numeric params) intentionally untouched

### Session & Transport Security (Pre-Existing)
- ✅ Session cookie flags: `Secure; HttpOnly; SameSite=Lax` (application.yml)
- ✅ WebSocket message size limit: 10KB (WebSocketConfig)
- ✅ CSRF protection enabled for REST, disabled only for `/ws/**` (SecurityConfig)

## Frontend (Polish, Accessibility, SEO)

### Settings Page (Complete Rewrite)
- ✅ `app/settings/page.tsx` (server component) — layout, metadata, header/footer
- ✅ `app/settings/settings-form.tsx` ('use client') — real form with:
  - Bio textarea (2000-char max with live character counter, `aria-live` region)
  - Favorite Language select (6 languages: C++, Java, Python, JavaScript, Kotlin, Go)
  - Theme preference radiogroup (Light / Dark / System) via `next-themes` `useTheme`
  - React Hook Form + Zod validation + prefill from Zustand auth store
  - Submits `PATCH /api/users/me` → `{ bio, favoriteLanguage }`
  - Success/error feedback (toast or inline message)
  - All labels tied to inputs, `aria-invalid` / `aria-describedby` on errors

### Theme Toggle
- ✅ `components/theme-toggle.tsx` — header sun/moon icon button
  - Uses `next-themes` `useTheme` with mounted-guard to prevent hydration mismatch
  - Keyboard-accessible (Button component, `aria-label`)
  - Mounted in `site-header.tsx` navigation

### UI Primitives (shadcn)
- ✅ `components/ui/input.tsx` — text input component (matches shadcn button/card pattern)
- ✅ `components/ui/textarea.tsx` — textarea component with built-in ref forwarding
- ✅ `components/ui/label.tsx` — label component tied to inputs

### Accessibility & Responsive Pass
- ✅ All icon-only buttons have `aria-label` (resign, draw, copy, join, theme-toggle, etc.)
- ✅ Focus indicators: `focus-visible:ring` classes present on interactive elements
- ✅ Keyboard navigation: all form controls Tab-able, submits on Enter
- ✅ `prefers-reduced-motion` respected:
  - Global `@media (prefers-reduced-motion: reduce)` block in `globals.css` (pre-existing)
  - Fixed ChatPanel smooth-scroll behavior to honor reduced-motion via JS guard
  - Layout.tsx already sets `disableTransitionOnChange` on ThemeProvider
- ✅ Responsive breakpoints:
  - Mobile-first stacking: `grid-cols-1 md:grid-cols-2` on duel room (problem + editor side-by-side at md breakpoint)
  - Form inputs stack on mobile, reasonable widths at md/lg
  - Settings form responsive (mobile-friendly)
- ✅ Color contrast: existing codebase maintains WCAG AA (4.5:1+)

### Legal Pages
- ✅ `app/terms/page.tsx` — fleshed out with sectioned placeholder legal content (Terms of Service structure)
- ✅ `app/privacy/page.tsx` — fleshed out with sectioned placeholder legal content (Privacy Policy structure)

### Code Quality
- ✅ Removed unused `roomCode` destructuring from `CodeEditor.tsx` (lint error resolution)

## Frontend (SEO & Performance)

### Sitemap & Robots
- ✅ `app/sitemap.ts` — Next.js `MetadataRoute.Sitemap`
  - Public routes only: `/`, `/login`, `/leaderboard`, `/terms`, `/privacy`
  - Excludes authenticated paths (`/dashboard`, `/duel/*`, `/friends`, `/settings`, `/onboarding`)
  - Excludes dynamic profile URLs (`/u/[handle]` — noted as future enhancement)
  - Uses `process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"` (matches layout.tsx)
  - Sensible `changeFrequency` and `priority` per route
- ✅ `app/robots.ts` — Next.js `MetadataRoute.Robots`
  - Allows: `/` (root)
  - Disallows: `/dashboard`, `/duel/`, `/friends`, `/settings`, `/onboarding`, `/api/`
  - References `/sitemap.xml`
  - Same base URL env pattern

### SEO Metadata (Audited)
- ✅ `app/page.tsx` (landing) — has `generateMetadata` with title, description, canonical, Open Graph
- ✅ `app/login/page.tsx` — has `export const metadata` (inherits OG/twitter from layout)
- ✅ `app/leaderboard/page.tsx` — has `export const metadata`
- ✅ `app/u/[handle]/page.tsx` (profile) — has `generateMetadata` with OG `type: profile`, avatar image, JSON-LD Person schema
- ✅ `app/layout.tsx` (global) — rich metadata (title template, metadataBase, OG, twitter)

### Dynamic Imports (Performance)
- ✅ `components/duel/CodeEditor.tsx` — Monaco Editor loaded via `next/dynamic` with `ssr: false` (stays off server bundle)
- ✅ `components/profile/RatingChart.tsx` + `RatingGraphs.tsx` — Recharts loaded dynamically (heavy client lib, out of initial bundle)

## Backend (Observability)

### Health & Metrics
- ✅ `spring-boot-starter-actuator` dependency (pom.xml) — present
- ✅ Application.yml config: `management.endpoints.web.exposure.include: health,metrics`
- ✅ `management.endpoint.health.show-details: when-authorized`
- ✅ `SecurityConfig` permits `/actuator/health` (public) and includes it in permitAll list

### API Documentation
- ✅ `springdoc-openapi-starter-webmvc-ui` dependency (pom.xml, version 2.6.0) — present
- ✅ Application.yml config: `springdoc.api-docs.path: /v3/api-docs`, `springdoc.swagger-ui.path: /swagger-ui.html`
- ✅ `SecurityConfig` permits `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`

## Build & Type-Check Status

- ✅ Backend: `mvn -q -DskipTests compile` — **EXIT=0, clean**. All new rate-limit classes + validation advice compile without errors.
- ✅ Frontend: `npm run build` — **✓ Compiled successfully**. All 14 routes generated, including `/robots.txt`, `/sitemap.xml`, new `/settings` form (29 kB), terms/privacy. ESLint, TypeScript type-check, static generation all pass.

## Verification Gate

**Security headers (Nginx):**
- ✅ `curl -I http://localhost/` returns:
  - `X-Frame-Options: DENY`
  - `X-Content-Type-Options: nosniff`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: geolocation=(), microphone=(), camera=()`
  - `Content-Security-Policy: ...` (full policy from nginx/nginx.conf)
  - (When HTTPS enabled: `Strict-Transport-Security: max-age=31536000; includeSubDomains; preload`)

**Rate limiting (100 req/min per user):**
- Authenticated user sends 100+ rapid API requests → receives HTTP 429 after limit with `Retry-After` header
- Anonymous user (unauthenticated) sends unlimited public reads (e.g., leaderboard fetch) → no 429 (rate limit skipped)

**Lighthouse audit (all categories ≥ 90):**
- `/` (landing): Performance, Accessibility, Best Practices, SEO each ≥ 90
- `/leaderboard`: same
- `/u/[handle]` (sample profile): same
- `/settings`: same

**Frontend accessibility & responsive:**
- Keyboard navigation: all pages Tab-able, submittable
- Mobile (320px): content legible, stacked layout, touch-friendly
- Tablet (768px): 2-col layouts appear, side-by-side where spec'd
- Desktop (1024px+): full duel room layout (problem, editor, team, chat side-by-side as spec)
- Theme toggle: light/dark/system works via header button, persists across sessions

**Settings form (end-to-end):**
- Logged-in user visits `/settings` → bio + language + theme form loads with current values
- User edits bio (types 500 chars) → counter shows "500/2000"
- User selects "Java" favorite language
- User clicks "Dark" theme → page transitions to dark mode immediately
- User clicks Save → request to `PATCH /api/users/me` succeeds
- User refreshes → values persist (bio, language, theme)
- User visits profile `/u/[handle]` → bio + language appear in profile header

