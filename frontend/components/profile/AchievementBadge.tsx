"use client";

import { cn } from "@/lib/utils";

export interface Achievement {
  code: string;
  name: string;
  description: string;
  icon: string | null;
}

interface AchievementBadgeProps {
  achievement: Achievement;
  earned: boolean;
}

/**
 * A single achievement badge. Earned badges are full-color; unearned badges are
 * greyed out. The full name + description shows on hover via the native title
 * tooltip (and an accessible aria-label).
 */
export function AchievementBadge({ achievement, earned }: AchievementBadgeProps) {
  const label = `${achievement.name}${earned ? "" : " (locked)"} — ${achievement.description}`;
  return (
    <div
      title={label}
      aria-label={label}
      className={cn(
        "flex flex-col items-center gap-1 rounded-lg border p-3 text-center transition-colors",
        earned ? "bg-card" : "bg-muted/40 opacity-50 grayscale",
      )}
    >
      <span className="text-2xl" aria-hidden>
        {achievement.icon ?? "🏅"}
      </span>
      <span className="text-xs font-medium leading-tight">{achievement.name}</span>
    </div>
  );
}
