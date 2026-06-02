"use client";

import { Swords } from "lucide-react";
import { Button } from "@/components/ui/button";
import { OAUTH_LOGIN_URL } from "@/lib/env";

/**
 * Kicks off the OAuth flow by sending the browser to the backend's
 * Spring Security entry point: ${BACKEND_URL}/oauth2/authorization/codeforces.
 * The backend handles the Codeforces handshake and redirects back to
 * /dashboard with a session cookie set.
 */
export function LoginButton() {
  return (
    <Button
      size="lg"
      className="w-full"
      onClick={() => {
        window.location.href = OAUTH_LOGIN_URL;
      }}
    >
      <Swords className="mr-2 h-5 w-5" aria-hidden="true" />
      Login with Codeforces
    </Button>
  );
}
