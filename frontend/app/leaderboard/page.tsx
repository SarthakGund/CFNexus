import type { Metadata } from "next";
import { LeaderboardTabs } from "@/components/leaderboard/LeaderboardTabs";
import { fetchLeaderboard, type LeaderboardType } from "@/lib/leaderboard";

export const metadata: Metadata = {
  title: "Leaderboard",
  description: "Top CFNexus duelists ranked by duel rating, unrated wins, streak, and fastest solve.",
};

// Always render fresh rankings.
export const dynamic = "force-dynamic";

export default async function LeaderboardPage() {
  const initialType: LeaderboardType = "duel_rating";
  const initialData = await fetchLeaderboard(initialType);

  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">Leaderboard</h1>
      <p className="mt-2 text-muted-foreground">
        The best duelists on CFNexus, ranked across four categories.
      </p>

      <LeaderboardTabs initialType={initialType} initialData={initialData} />
    </main>
  );
}
