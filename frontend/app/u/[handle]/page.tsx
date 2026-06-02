import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card, CardContent } from "@/components/ui/card";
import { AchievementsGrid } from "@/components/profile/AchievementsGrid";
import { MatchHistory } from "@/components/profile/MatchHistory";
import { RatingGraphs } from "@/components/profile/RatingGraphs";
import { rankTier } from "@/lib/rank";
import { fetchProfile, formatDuration, type ProfileDto } from "@/lib/profile";

interface ProfileParams {
  params: Promise<{ handle: string }>;
}

export async function generateMetadata({ params }: ProfileParams): Promise<Metadata> {
  const { handle } = await params;
  const profile = await fetchProfile(handle);
  const display = profile?.cfHandle ?? handle;
  const title = `${display} — CFNexus profile`;
  const description = profile
    ? `${display}: duel rating ${profile.duelRating} (${rankTier(profile.duelRating).name}), ` +
      `${profile.duelWins}W/${profile.duelLosses}L/${profile.duelDraws}D on CFNexus.`
    : `Duel rating, match history, and achievements for ${display} on CFNexus.`;

  return {
    title,
    description,
    alternates: { canonical: `/u/${display}` },
    openGraph: {
      title,
      description,
      url: `/u/${display}`,
      type: "profile",
      images: profile?.avatarUrl ? [{ url: profile.avatarUrl }] : undefined,
    },
    twitter: { card: "summary", title, description },
  };
}

function StatItem({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex flex-col">
      <span className="text-xl font-bold">{value}</span>
      <span className="text-xs uppercase tracking-wide text-muted-foreground">{label}</span>
    </div>
  );
}

function ProfileHeader({ profile }: { profile: ProfileDto }) {
  const tier = rankTier(profile.duelRating);
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={profile.avatarUrl ?? "/avatar-placeholder.svg"}
        alt={`${profile.cfHandle} avatar`}
        width={96}
        height={96}
        className="h-24 w-24 rounded-full border bg-muted object-cover"
      />
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-bold">{profile.cfHandle}</h1>
          {profile.cfRank && (
            <span className="rounded-full border px-2 py-0.5 text-xs text-muted-foreground">
              CF: {profile.cfRank} ({profile.cfRating})
            </span>
          )}
          <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${tier.colorClass}`}>
            {tier.name} · {profile.duelRating}
          </span>
        </div>
        {profile.bio && <p className="max-w-prose text-sm text-muted-foreground">{profile.bio}</p>}
        {profile.favoriteLang && (
          <p className="text-xs text-muted-foreground">Favorite language: {profile.favoriteLang}</p>
        )}
      </div>
    </div>
  );
}

export default async function ProfilePage({ params }: ProfileParams) {
  const { handle } = await params;
  const profile = await fetchProfile(handle);
  if (!profile) {
    notFound();
  }

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "ProfilePage",
    mainEntity: {
      "@type": "Person",
      name: profile.cfHandle,
      identifier: profile.cfHandle,
      image: profile.avatarUrl ?? undefined,
      url: `/u/${profile.cfHandle}`,
    },
  };

  return (
    <main className="container space-y-8 py-10">
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />

      <ProfileHeader profile={profile} />

      <Card>
        <CardContent className="grid grid-cols-3 gap-4 py-6 sm:grid-cols-6">
          <StatItem label="Wins" value={profile.duelWins} />
          <StatItem label="Losses" value={profile.duelLosses} />
          <StatItem label="Draws" value={profile.duelDraws} />
          <StatItem label="Streak" value={profile.currentStreak} />
          <StatItem label="Unrated Wins" value={profile.unratedWins} />
          <StatItem label="Fastest Solve" value={formatDuration(profile.fastestSolveMs)} />
        </CardContent>
      </Card>

      <RatingGraphs handle={profile.cfHandle} duelRating={profile.duelRating} />

      <MatchHistory handle={profile.cfHandle} />

      <AchievementsGrid handle={profile.cfHandle} />
    </main>
  );
}
