/**
 * Duel rank tiers shown on the profile (spec §9). Pure helpers — safe to import
 * from both server and client components.
 */

export interface RankTier {
  name: string;
  /** Tailwind text-color class used for the rating badge. */
  colorClass: string;
}

const TIERS: Array<{ min: number; tier: RankTier }> = [
  { min: 2300, tier: { name: "Grandmaster", colorClass: "text-red-500" } },
  { min: 2100, tier: { name: "International Master", colorClass: "text-orange-500" } },
  { min: 1900, tier: { name: "Master", colorClass: "text-amber-500" } },
  { min: 1600, tier: { name: "Candidate Master", colorClass: "text-violet-500" } },
  { min: 1400, tier: { name: "Expert", colorClass: "text-blue-500" } },
  { min: 1200, tier: { name: "Specialist", colorClass: "text-cyan-500" } },
  { min: 1000, tier: { name: "Pupil", colorClass: "text-green-500" } },
  { min: 800, tier: { name: "Apprentice", colorClass: "text-lime-500" } },
  { min: 0, tier: { name: "Newbie", colorClass: "text-muted-foreground" } },
];

export function rankTier(rating: number | null | undefined): RankTier {
  const r = rating ?? 0;
  for (const { min, tier } of TIERS) {
    if (r >= min) {
      return tier;
    }
  }
  return TIERS[TIERS.length - 1].tier;
}
