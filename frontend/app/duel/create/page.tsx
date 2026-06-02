"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Swords } from "lucide-react";
import api from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import type { DuelType } from "@/store/duelStore";

const TYPES: { value: DuelType; label: string; description: string }[] = [
  {
    value: "RATED_1V1",
    label: "Rated 1v1",
    description: "Head-to-head. Affects your duel rating.",
  },
  {
    value: "UNRATED_TEAM",
    label: "Team",
    description: "Up to 4 per team. No rating impact.",
  },
  {
    value: "UNRATED_FFA",
    label: "Free-for-all",
    description: "Up to 4 players, first to solve wins.",
  },
];

const MIN_RATING = 800;
const MAX_RATING = 3500;
const STEP = 100;

interface CreateResponse {
  roomCode: string;
  inviteUrl: string;
}

export default function CreateDuelPage() {
  const router = useRouter();
  const [type, setType] = useState<DuelType>("RATED_1V1");
  const [rating, setRating] = useState(1500);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const { data } = await api.post<CreateResponse>("/duels/create", {
        type,
        problemRating: rating,
      });
      router.push(`/duel/${data.roomCode}`);
    } catch {
      setError("Failed to create the duel. Please try again.");
      setSubmitting(false);
    }
  }

  return (
    <main className="container max-w-2xl py-10">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Create a Duel</CardTitle>
          <CardDescription>
            Pick a mode and problem rating, then invite an opponent.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-8">
            <fieldset className="space-y-3">
              <legend className="text-sm font-medium">Mode</legend>
              <div
                role="radiogroup"
                aria-label="Duel mode"
                className="grid grid-cols-1 gap-3 sm:grid-cols-3"
              >
                {TYPES.map((t) => {
                  const selected = type === t.value;
                  return (
                    <button
                      key={t.value}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      onClick={() => setType(t.value)}
                      className={cn(
                        "flex flex-col gap-1 rounded-lg border p-4 text-left transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
                        selected
                          ? "border-primary bg-primary/5 ring-1 ring-primary"
                          : "hover:bg-accent",
                      )}
                    >
                      <span className="text-sm font-semibold">{t.label}</span>
                      <span className="text-xs text-muted-foreground">
                        {t.description}
                      </span>
                    </button>
                  );
                })}
              </div>
            </fieldset>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <label htmlFor="rating" className="text-sm font-medium">
                  Problem rating
                </label>
                <span
                  aria-live="polite"
                  className="font-mono text-sm font-semibold"
                >
                  {rating}
                </span>
              </div>
              <input
                id="rating"
                type="range"
                min={MIN_RATING}
                max={MAX_RATING}
                step={STEP}
                value={rating}
                onChange={(e) => setRating(Number(e.target.value))}
                aria-valuemin={MIN_RATING}
                aria-valuemax={MAX_RATING}
                aria-valuenow={rating}
                className="w-full accent-primary"
              />
              <div className="flex justify-between text-xs text-muted-foreground">
                <span>{MIN_RATING}</span>
                <span>{MAX_RATING}</span>
              </div>
            </div>

            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}

            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? (
                <Loader2 className="animate-spin" />
              ) : (
                <Swords />
              )}
              Create Duel
            </Button>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
