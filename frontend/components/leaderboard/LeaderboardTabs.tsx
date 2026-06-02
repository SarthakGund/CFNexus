"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { rankTier } from "@/lib/rank";
import {
  LEADERBOARD_TABS,
  fetchLeaderboard,
  formatLeaderboardValue,
  type LeaderboardEntry,
  type LeaderboardType,
} from "@/lib/leaderboard";
import type { PagedResponse } from "@/lib/profile";

interface Props {
  initialType: LeaderboardType;
  initialData: PagedResponse<LeaderboardEntry>;
}

/**
 * Client-side tab switcher over the four leaderboard categories. The first tab
 * is hydrated from SSR data; switching tabs (or paging) fetches on demand and
 * caches each category's loaded page in memory.
 */
export function LeaderboardTabs({ initialType, initialData }: Props) {
  const [active, setActive] = useState<LeaderboardType>(initialType);
  const [cache, setCache] = useState<
    Partial<Record<LeaderboardType, PagedResponse<LeaderboardEntry>>>
  >({ [initialType]: initialData });
  const [loading, setLoading] = useState(false);

  const current = cache[active];

  const load = useCallback(
    async (type: LeaderboardType) => {
      if (cache[type]) {
        return;
      }
      setLoading(true);
      const data = await fetchLeaderboard(type);
      setCache((c) => ({ ...c, [type]: data }));
      setLoading(false);
    },
    [cache],
  );

  useEffect(() => {
    void load(active);
  }, [active, load]);

  const activeTab = LEADERBOARD_TABS.find((t) => t.type === active)!;

  return (
    <div className="mt-6 space-y-4">
      {/* Tab switcher */}
      <div
        role="tablist"
        aria-label="Leaderboard categories"
        className="flex flex-wrap gap-2"
      >
        {LEADERBOARD_TABS.map((tab) => (
          <button
            key={tab.type}
            role="tab"
            type="button"
            aria-selected={active === tab.type}
            onClick={() => setActive(tab.type)}
            className={cn(
              "rounded-full border px-4 py-1.5 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              active === tab.type
                ? "border-primary bg-primary text-primary-foreground"
                : "hover:bg-muted",
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Ranked rows */}
      <div className="overflow-x-auto rounded-xl border">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/40 text-left text-xs uppercase tracking-wide text-muted-foreground">
              <th scope="col" className="w-16 px-4 py-3">
                #
              </th>
              <th scope="col" className="px-4 py-3">
                Player
              </th>
              <th scope="col" className="px-4 py-3 text-right">
                {activeTab.valueLabel}
              </th>
              <th scope="col" className="hidden px-4 py-3 text-right sm:table-cell">
                W / L / D
              </th>
            </tr>
          </thead>
          <tbody>
            {loading && !current ? (
              <tr>
                <td colSpan={4} className="px-4 py-10 text-center">
                  <Loader2 className="mx-auto size-5 animate-spin text-muted-foreground" />
                </td>
              </tr>
            ) : current && current.items.length > 0 ? (
              current.items.map((entry) => {
                const tier = rankTier(entry.duelRating);
                return (
                  <tr
                    key={entry.handle}
                    className="border-b last:border-0 hover:bg-muted/30"
                  >
                    <td className="px-4 py-3 font-mono text-muted-foreground">
                      {entry.rank}
                    </td>
                    <td className="px-4 py-3">
                      <Link
                        href={`/u/${entry.handle}`}
                        className="flex items-center gap-2 hover:underline"
                      >
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={
                            entry.avatarUrl ??
                            `https://avatars.dicebear.com/api/identicon/${entry.handle}.svg`
                          }
                          alt=""
                          className="h-7 w-7 rounded-full bg-muted object-cover"
                        />
                        <span
                          className={cn("font-mono font-semibold", tier.colorClass)}
                        >
                          {entry.handle}
                        </span>
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-right font-mono font-semibold">
                      {formatLeaderboardValue(active, entry)}
                    </td>
                    <td className="hidden px-4 py-3 text-right font-mono text-muted-foreground sm:table-cell">
                      {entry.duelWins} / {entry.duelLosses} / {entry.duelDraws}
                    </td>
                  </tr>
                );
              })
            ) : (
              <tr>
                <td
                  colSpan={4}
                  className="px-4 py-10 text-center text-muted-foreground"
                >
                  No ranked players yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default LeaderboardTabs;
