"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import api from "@/lib/api";
import type { ChartPoint } from "@/components/profile/RatingChart";
import type { CfRatingPoint, RatingPoint } from "@/lib/profile";

// Recharts is heavy and browser-only — load it lazily, never on the server.
const RatingChart = dynamic(() => import("@/components/profile/RatingChart"), {
  ssr: false,
  loading: () => <ChartSkeleton />,
});

function ChartSkeleton() {
  return <div className="h-[240px] w-full animate-pulse rounded-md bg-muted" />;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
  });
}

interface RatingGraphsProps {
  handle: string;
  /** Current duel rating, used to anchor the start of the duel graph. */
  duelRating: number;
}

export function RatingGraphs({ handle, duelRating }: RatingGraphsProps) {
  const [duelPoints, setDuelPoints] = useState<ChartPoint[] | null>(null);
  const [cfPoints, setCfPoints] = useState<ChartPoint[] | null>(null);

  useEffect(() => {
    let cancelled = false;

    api
      .get<RatingPoint[]>(`/users/${encodeURIComponent(handle)}/rating-history`)
      .then((res) => {
        if (cancelled) return;
        const history = res.data;
        const points: ChartPoint[] = [];
        if (history.length > 0) {
          // Anchor with the pre-first-duel rating so the line shows the climb.
          points.push({ label: "Start", rating: history[0].rating - history[0].delta });
          for (const p of history) {
            points.push({ label: formatDate(p.recordedAt), rating: p.rating });
          }
        }
        setDuelPoints(points);
      })
      .catch(() => !cancelled && setDuelPoints([]));

    api
      .get<CfRatingPoint[]>(`/users/${encodeURIComponent(handle)}/cf-rating-history`)
      .then((res) => {
        if (cancelled) return;
        const points: ChartPoint[] = res.data
          .filter((p) => p.newRating != null && p.ratingUpdateTimeSeconds != null)
          .map((p) => ({
            label: formatDate(new Date(p.ratingUpdateTimeSeconds! * 1000).toISOString()),
            rating: p.newRating!,
          }));
        setCfPoints(points);
      })
      .catch(() => !cancelled && setCfPoints([]));

    return () => {
      cancelled = true;
    };
  }, [handle]);

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Duel Rating</CardTitle>
        </CardHeader>
        <CardContent>
          {duelPoints == null ? (
            <ChartSkeleton />
          ) : duelPoints.length === 0 ? (
            <EmptyState message={`No rated duels yet — starting at ${duelRating}.`} />
          ) : (
            <RatingChart data={duelPoints} color="hsl(var(--primary))" />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Codeforces Rating</CardTitle>
        </CardHeader>
        <CardContent>
          {cfPoints == null ? (
            <ChartSkeleton />
          ) : cfPoints.length === 0 ? (
            <EmptyState message="No Codeforces contest history." />
          ) : (
            <RatingChart data={cfPoints} color="#f59e0b" />
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function EmptyState({ message }: { message: string }) {
  return (
    <div className="flex h-[240px] items-center justify-center text-sm text-muted-foreground">
      {message}
    </div>
  );
}
