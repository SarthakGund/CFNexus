"use client";

import { useAuth } from "@/hooks/useAuth";

/**
 * Mounted once in the root layout. It calls useAuth purely for its side
 * effect: fetching /api/auth/me and hydrating the Zustand auth store so the
 * rest of the app can read user state. Renders nothing.
 */
export function AuthBootstrap() {
  useAuth();
  return null;
}
