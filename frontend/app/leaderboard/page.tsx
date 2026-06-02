import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Leaderboard",
  description: "Top CFNexus duelists ranked by duel rating and wins.",
};

export default function LeaderboardPage() {
  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">Leaderboard</h1>
      <p className="mt-2 text-muted-foreground">
        Ranked players across all four categories arrive in Phase 4.
      </p>
    </main>
  );
}
