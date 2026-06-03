import { NextResponse, type NextRequest } from "next/server";

/**
 * Route protection. The backend's Spring Session cookie is named
 * `CFNEXUSSESSION` (server.servlet.session.cookie.name); `SESSION` /
 * `JSESSIONID` are kept as fallbacks. If none is present on a protected route
 * we bounce the user to /login.
 *
 * NOTE: this is a coarse presence check only. The backend remains the source
 * of truth and re-validates the session on every API call.
 */
const SESSION_COOKIES = ["CFNEXUSSESSION", "SESSION", "JSESSIONID"];

export function middleware(request: NextRequest) {
  const hasSession = SESSION_COOKIES.some((name) =>
    request.cookies.has(name),
  );

  if (!hasSession) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("from", request.nextUrl.pathname);
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/duel/:path*",
    "/friends/:path*",
    "/settings/:path*",
    "/onboarding/:path*",
  ],
};
