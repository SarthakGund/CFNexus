"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AchievementBadge, type Achievement } from "@/components/profile/AchievementBadge";
import api from "@/lib/api";

interface EarnedAchievement {
  code: string;
}

/**
 * Achievements grid. Fetches the catalogue (`/api/achievements`) and the user's
 * earned set (`/api/users/{handle}/achievements`). Both endpoints are delivered
 * in Phase 6 — until then this degrades gracefully to a "coming soon" note.
 */
export function AchievementsGrid({ handle }: { handle: string }) {
  const [all, setAll] = useState<Achievement[] | null>(null);
  const [earned, setEarned] = useState<Set<string>>(new Set());
  const [unavailable, setUnavailable] = useState(false);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.get<Achievement[]>("/achievements"),
      api
        .get<EarnedAchievement[]>(`/users/${encodeURIComponent(handle)}/achievements`)
        .catch(() => ({ data: [] as EarnedAchievement[] })),
    ])
      .then(([catalogue, earnedRes]) => {
        if (cancelled) return;
        setAll(catalogue.data);
        setEarned(new Set(earnedRes.data.map((a) => a.code)));
      })
      .catch(() => !cancelled && setUnavailable(true));
    return () => {
      cancelled = true;
    };
  }, [handle]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Achievements</CardTitle>
      </CardHeader>
      <CardContent>
        {unavailable ? (
          <p className="py-6 text-center text-sm text-muted-foreground">
            Achievements arrive in a later release.
          </p>
        ) : all == null ? (
          <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="h-20 animate-pulse rounded-lg bg-muted" />
            ))}
          </div>
        ) : (
          <div className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6">
            {all.map((a) => (
              <AchievementBadge key={a.code} achievement={a} earned={earned.has(a.code)} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
