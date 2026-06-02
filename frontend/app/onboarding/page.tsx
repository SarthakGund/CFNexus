"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";
import api from "@/lib/api";
import { useAuthStore, type User } from "@/store/authStore";

const LANGUAGES = ["C++", "Python", "Java", "JavaScript", "Kotlin", "Go", "Rust"];
const TOTAL_STEPS = 3;

/**
 * 3-step first-login walkthrough (spec §19): welcome → profile setup →
 * how it works. Profile setup persists bio + favorite language via
 * PATCH /api/users/me, then redirects to the dashboard.
 */
export default function OnboardingPage() {
  const router = useRouter();
  const { user } = useAuth();
  const setUser = useAuthStore((s) => s.setUser);

  const [step, setStep] = useState(1);
  const [bio, setBio] = useState("");
  const [favoriteLanguage, setFavoriteLanguage] = useState("");
  const [saving, setSaving] = useState(false);

  function finish() {
    router.push("/dashboard");
  }

  async function saveProfileAndContinue() {
    setSaving(true);
    try {
      const { data } = await api.patch<User>("/users/me", {
        bio: bio.trim() || null,
        favoriteLanguage: favoriteLanguage || null,
      });
      setUser(data);
    } catch {
      // Non-blocking: onboarding should proceed even if the save fails.
    } finally {
      setSaving(false);
      setStep(3);
    }
  }

  return (
    <main className="container flex min-h-[70vh] items-center justify-center py-10">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <div className="mb-2 flex items-center gap-1.5" aria-hidden>
            {Array.from({ length: TOTAL_STEPS }).map((_, i) => (
              <span
                key={i}
                className={`h-1.5 flex-1 rounded-full ${i < step ? "bg-primary" : "bg-muted"}`}
              />
            ))}
          </div>
          <CardTitle>
            {step === 1 && `Welcome${user ? `, ${user.cfHandle}` : ""}!`}
            {step === 2 && "Set up your profile"}
            {step === 3 && "How it works"}
          </CardTitle>
        </CardHeader>

        <CardContent className="space-y-6">
          {step === 1 && (
            <div className="space-y-3 text-sm text-muted-foreground">
              <p>
                CFNexus is a real-time competitive programming duel platform. Challenge other
                Codeforces users, solve a problem live, and the first to get <strong>Accepted</strong>{" "}
                wins.
              </p>
              <p>Let&apos;s get you set up in a couple of quick steps.</p>
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label htmlFor="bio" className="text-sm font-medium">
                  Bio <span className="text-muted-foreground">(optional)</span>
                </label>
                <textarea
                  id="bio"
                  value={bio}
                  onChange={(e) => setBio(e.target.value)}
                  rows={3}
                  maxLength={500}
                  placeholder="Tell others a bit about yourself…"
                  className="w-full rounded-md border bg-background p-2 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="lang" className="text-sm font-medium">
                  Favorite language <span className="text-muted-foreground">(optional)</span>
                </label>
                <select
                  id="lang"
                  value={favoriteLanguage}
                  onChange={(e) => setFavoriteLanguage(e.target.value)}
                  className="w-full rounded-md border bg-background p-2 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">Select a language…</option>
                  {LANGUAGES.map((l) => (
                    <option key={l} value={l}>
                      {l}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}

          {step === 3 && (
            <div className="space-y-3 text-sm text-muted-foreground">
              <p>
                <strong className="text-foreground">Rated 1v1</strong> — your duel rating changes
                with every win and loss (ELO based). Play to climb the leaderboard.
              </p>
              <p>
                <strong className="text-foreground">Unrated team &amp; free-for-all</strong> — play
                for fun with friends; no rating at stake.
              </p>
              <p>Create a room, share the invite link, and start duelling.</p>
            </div>
          )}

          <div className="flex items-center justify-between pt-2">
            {step > 1 ? (
              <Button variant="ghost" onClick={() => setStep((s) => s - 1)} disabled={saving}>
                Back
              </Button>
            ) : (
              <span />
            )}

            {step === 1 && <Button onClick={() => setStep(2)}>Get started</Button>}
            {step === 2 && (
              <Button onClick={saveProfileAndContinue} disabled={saving}>
                {saving ? "Saving…" : "Continue"}
              </Button>
            )}
            {step === 3 && <Button onClick={finish}>Go to dashboard</Button>}
          </div>
        </CardContent>
      </Card>
    </main>
  );
}
