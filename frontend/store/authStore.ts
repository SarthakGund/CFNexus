import { create } from "zustand";
import api from "@/lib/api";
import { OAUTH_LOGIN_URL } from "@/lib/env";

/**
 * Authenticated user shape, mirroring the backend `users` table (§5).
 * Only the fields the frontend cares about are typed here.
 */
export interface User {
  id: string;
  cfHandle: string;
  cfRating: number;
  cfMaxRating: number;
  cfRank: string | null;
  cfMaxRank: string | null;
  avatarUrl: string | null;
  duelRating: number;
  duelWins: number;
  duelLosses: number;
  duelDraws: number;
  unratedWins: number;
  currentStreak: number;
  maxStreak: number;
  bio: string | null;
  favoriteLang: string | null;
  createdAt?: string;
}

interface AuthState {
  user: User | null;
  isLoading: boolean;
  /** Replace the current user (null clears it). */
  setUser: (user: User | null) => void;
  setLoading: (loading: boolean) => void;
  /** Redirect the browser to the backend OAuth entry point. */
  login: () => void;
  /** Hit the backend logout endpoint then clear local state. */
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isLoading: true,
  setUser: (user) => set({ user, isLoading: false }),
  setLoading: (isLoading) => set({ isLoading }),
  login: () => {
    if (typeof window !== "undefined") {
      window.location.href = OAUTH_LOGIN_URL;
    }
  },
  logout: async () => {
    try {
      await api.post("/auth/logout");
    } catch {
      // ignore network/credential errors — clear state regardless
    } finally {
      set({ user: null, isLoading: false });
      if (typeof window !== "undefined") {
        window.location.href = "/";
      }
    }
  },
}));
