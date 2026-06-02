/**
 * Centralised, typed access to public environment variables.
 *
 * NEXT_PUBLIC_API_BASE_URL  — REST base, includes the trailing /api (e.g. http://localhost:8080/api)
 * NEXT_PUBLIC_BACKEND_URL   — backend origin without /api (used for OAuth redirects)
 * NEXT_PUBLIC_WS_URL        — STOMP/WebSocket endpoint
 */

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

export const WS_URL =
  process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws";

/**
 * SockJS endpoint. SockJS requires an HTTP(S) URL rather than the raw ws(s)
 * scheme, so we derive it from WS_URL by swapping the protocol.
 */
export const SOCKJS_URL = WS_URL.replace(/^ws:\/\//, "http://").replace(
  /^wss:\/\//,
  "https://",
);

/**
 * Backend origin used for the OAuth handshake. Prefer the explicit
 * NEXT_PUBLIC_BACKEND_URL; otherwise derive it from the API base by
 * stripping a trailing "/api" segment.
 */
export const BACKEND_URL = (
  process.env.NEXT_PUBLIC_BACKEND_URL ?? API_BASE_URL.replace(/\/api\/?$/, "")
).replace(/\/$/, "");

/** Full URL that initiates the Codeforces OAuth flow on the backend. */
export const OAUTH_LOGIN_URL = `${BACKEND_URL}/oauth2/authorization/codeforces`;
