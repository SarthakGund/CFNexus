import { API_BASE_URL } from "@/lib/env";

/** Mirrors the backend UserProfileDto (spec §5/§6). */
export interface ProfileDto {
  id: string;
  cfHandle: string;
  cfUserId: number;
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
  fastestSolveMs: number | null;
  bio: string | null;
  favoriteLang: string | null;
  isOnline: boolean;
  lastSeen: string | null;
  createdAt: string;
}

export interface RatingPoint {
  duelId: string | null;
  rating: number;
  delta: number;
  recordedAt: string;
}

export interface CfRatingPoint {
  contestId: number | null;
  ratingUpdateTimeSeconds: number | null;
  newRating: number | null;
  oldRating: number | null;
}

export interface MatchRow {
  roomId: string | null;
  result: string;
  problemId: string | null;
  problemRating: number | null;
  problemUrl: string | null;
  duelType: string | null;
  durationMs: number | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
  ratingDelta: number | null;
  opponentHandles: string[];
  playedAt: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Server-side fetch of a public profile (used by the SSR profile page for
 * metadata + JSON-LD). Returns null on 404 / network error.
 */
export async function fetchProfile(handle: string): Promise<ProfileDto | null> {
  try {
    const res = await fetch(`${API_BASE_URL}/users/${encodeURIComponent(handle)}`, {
      cache: "no-store",
    });
    if (!res.ok) {
      return null;
    }
    return (await res.json()) as ProfileDto;
  } catch {
    return null;
  }
}

export function formatDuration(ms: number | null | undefined): string {
  if (ms == null) {
    return "—";
  }
  const totalSeconds = Math.round(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds.toString().padStart(2, "0")}s`;
}
