import { API_BASE_URL } from "@/lib/env";
import type { PagedResponse } from "@/lib/profile";

/** The four leaderboard categories (spec §13). */
export type LeaderboardType =
  | "duel_rating"
  | "unrated_wins"
  | "streak"
  | "fastest_solve";

export interface LeaderboardEntry {
  rank: number;
  handle: string;
  avatarUrl: string | null;
  duelRating: number;
  duelWins: number;
  duelLosses: number;
  duelDraws: number;
  unratedWins: number;
  currentStreak: number;
  fastestSolveMs: number | null;
  /** The metric for the requested category, already resolved by the backend. */
  value: number;
}

export const LEADERBOARD_TABS: Array<{
  type: LeaderboardType;
  label: string;
  /** Column header for the category's metric. */
  valueLabel: string;
}> = [
  { type: "duel_rating", label: "Duel Rating", valueLabel: "Rating" },
  { type: "unrated_wins", label: "Unrated Wins", valueLabel: "Wins" },
  { type: "streak", label: "Current Streak", valueLabel: "Streak" },
  { type: "fastest_solve", label: "Fastest Solve", valueLabel: "Time" },
];

export const LEADERBOARD_PAGE_SIZE = 50;

/**
 * Fetches one page of a leaderboard category. Works server-side (SSR initial
 * render) and client-side (tab switching). Returns an empty page on failure so
 * the UI degrades gracefully.
 */
export async function fetchLeaderboard(
  type: LeaderboardType,
  page = 0,
  size = LEADERBOARD_PAGE_SIZE,
): Promise<PagedResponse<LeaderboardEntry>> {
  const empty: PagedResponse<LeaderboardEntry> = {
    items: [],
    page,
    size,
    totalElements: 0,
    totalPages: 0,
  };
  try {
    const res = await fetch(
      `${API_BASE_URL}/leaderboard?type=${type}&page=${page}&size=${size}`,
      { cache: "no-store" },
    );
    if (!res.ok) {
      return empty;
    }
    return (await res.json()) as PagedResponse<LeaderboardEntry>;
  } catch {
    return empty;
  }
}

/** Renders the category metric for a row (rating/wins/streak are numbers, fastest is a time). */
export function formatLeaderboardValue(
  type: LeaderboardType,
  entry: LeaderboardEntry,
): string {
  if (type === "fastest_solve") {
    if (entry.fastestSolveMs == null) {
      return "—";
    }
    const totalSeconds = Math.round(entry.fastestSolveMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}m ${seconds.toString().padStart(2, "0")}s`;
  }
  return entry.value.toLocaleString();
}
