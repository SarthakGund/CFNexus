"use client";

import Link from "next/link";
import { Loader2, Swords, Users, Trophy, Settings as SettingsIcon } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { MatchHistory } from "@/components/profile/MatchHistory";
import { useAuth } from "@/hooks/useAuth";
import { rankTier } from "@/lib/rank";
import { cn } from "@/lib/utils";

/**
 * Authenticated landing page (spec §8): a snapshot of the signed-in user's
 * duel stats, quick actions, and recent match history. Reads the user from the
 * Zustand auth store hydrated by AuthBootstrap; match history is fetched live
 * from /api/users/{handle}/match-history.
 */
export function DashboardContent() {
  const { user, isLoading, login } = useAuth();

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!user) {
    return (
      <Card className="mx-auto mt-10 max-w-md p-8 text-center">
        <h2 className="text-lg font-semibold">You&apos;re not signed in</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          Log in with your Codeforces account to see your duels and stats.
        </p>
        <Button className="mt-4" onClick={() => login()}>
          Login with Codeforces
        </Button>
      </Card>
    );
  }

  const tier = rankTier(user.duelRating);
  const decided = user.duelWins + user.duelLosses + user.duelDraws;
  const winRate = decided > 0 ? Math.round((user.duelWins / decided) * 100) : null;

  return (
    <div className="space-y-8">
      {/* Greeting + rank */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={user.avatarUrl ?? "/avatar-placeholder.svg"}
          alt=""
          width={72}
          height={72}
          className="h-16 w-16 rounded-full border bg-muted object-cover"
        />
        <div className="min-w-0">
          <h1 className="text-2xl font-bold sm:text-3xl">
            Welcome back, <span className="font-mono">{user.cfHandle}</span>
          </h1>
          <p className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
            <span className={cn("font-medium", tier.colorClass)}>
              {tier.name} · {user.duelRating}
            </span>
            {user.cfRank && (
              <span className="rounded-full border px-2 py-0.5 text-xs">
                CF: {user.cfRank} ({user.cfRating})
              </span>
            )}
          </p>
        </div>
        <div className="sm:ml-auto">
          <Button asChild size="sm" variant="outline">
            <Link href={`/u/${user.cfHandle}`}>View public profile</Link>
          </Button>
        </div>
      </div>

      {/* Stats */}
      <Card>
        <CardContent className="grid grid-cols-3 gap-4 py-6 sm:grid-cols-6">
          <StatItem label="Wins" value={user.duelWins} />
          <StatItem label="Losses" value={user.duelLosses} />
          <StatItem label="Draws" value={user.duelDraws} />
          <StatItem label="Win rate" value={winRate == null ? "—" : `${winRate}%`} />
          <StatItem label="Streak" value={user.currentStreak} />
          <StatItem label="Unrated wins" value={user.unratedWins} />
        </CardContent>
      </Card>

      {/* Quick actions */}
      <section>
        <h2 className="mb-3 text-lg font-semibold">Quick actions</h2>
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <ActionCard
            href="/duel/create"
            icon={<Swords className="size-5 text-primary" />}
            title="New duel"
            description="Create a room and invite an opponent."
          />
          <ActionCard
            href="/friends"
            icon={<Users className="size-5 text-primary" />}
            title="Friends"
            description="Manage friends and see who's online."
          />
          <ActionCard
            href="/leaderboard"
            icon={<Trophy className="size-5 text-primary" />}
            title="Leaderboard"
            description="See where you rank against others."
          />
          <ActionCard
            href="/settings"
            icon={<SettingsIcon className="size-5 text-primary" />}
            title="Settings"
            description="Update your profile and preferences."
          />
        </div>
      </section>

      {/* Recent matches */}
      <section>
        <h2 className="mb-3 text-lg font-semibold">Recent matches</h2>
        <MatchHistory handle={user.cfHandle} />
      </section>
    </div>
  );
}

function StatItem({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex flex-col">
      <span className="text-xl font-bold">{value}</span>
      <span className="text-xs uppercase tracking-wide text-muted-foreground">{label}</span>
    </div>
  );
}

function ActionCard({
  href,
  icon,
  title,
  description,
}: {
  href: string;
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <Link href={href} className="group">
      <Card className="h-full p-4 transition-colors group-hover:border-primary/60">
        <CardHeader className="flex-row items-center gap-2 space-y-0 p-0">
          {icon}
          <CardTitle className="text-base">{title}</CardTitle>
        </CardHeader>
        <CardContent className="p-0 pt-2">
          <p className="text-sm text-muted-foreground">{description}</p>
        </CardContent>
      </Card>
    </Link>
  );
}
