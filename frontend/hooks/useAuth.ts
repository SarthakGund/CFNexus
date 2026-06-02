"use client";

import { useEffect } from "react";
import api from "@/lib/api";
import { useAuthStore, type User } from "@/store/authStore";

/**
 * Bootstraps and exposes auth state. On mount it fetches `/api/auth/me`
 * (sending the session cookie via withCredentials) and populates the
 * Zustand store. A 401 simply means "not logged in".
 */
export function useAuth() {
  const user = useAuthStore((s) => s.user);
  const isLoading = useAuthStore((s) => s.isLoading);
  const setUser = useAuthStore((s) => s.setUser);
  const setLoading = useAuthStore((s) => s.setLoading);
  const login = useAuthStore((s) => s.login);
  const logout = useAuthStore((s) => s.logout);

  useEffect(() => {
    let active = true;

    async function bootstrap() {
      setLoading(true);
      try {
        const { data } = await api.get<User>("/auth/me");
        if (active) setUser(data);
      } catch {
        if (active) setUser(null);
      }
    }

    void bootstrap();
    return () => {
      active = false;
    };
    // run once on mount
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { user, isLoading, login, logout };
}
