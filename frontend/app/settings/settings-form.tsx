"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTheme } from "next-themes";
import { Loader2 } from "lucide-react";

import api from "@/lib/api";
import { useAuthStore } from "@/store/authStore";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

const BIO_MAX = 2000;
const LANG_MAX = 20;

/** Languages the duel supports (task spec). Stored verbatim as favoriteLang. */
const LANGUAGES = [
  "C++",
  "Java",
  "Python",
  "JavaScript",
  "Kotlin",
  "Go",
] as const;

const settingsSchema = z.object({
  bio: z
    .string()
    .max(BIO_MAX, `Bio must be ${BIO_MAX} characters or fewer.`),
  favoriteLang: z
    .string()
    .max(LANG_MAX, `Favorite language must be ${LANG_MAX} characters or fewer.`),
});

type SettingsValues = z.infer<typeof settingsSchema>;

type SaveState =
  | { kind: "idle" }
  | { kind: "saving" }
  | { kind: "success" }
  | { kind: "error"; message: string };

export function SettingsForm() {
  const user = useAuthStore((s) => s.user);
  const isLoading = useAuthStore((s) => s.isLoading);
  const setUser = useAuthStore((s) => s.setUser);

  const [saveState, setSaveState] = useState<SaveState>({ kind: "idle" });

  const {
    register,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isDirty },
  } = useForm<SettingsValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: { bio: "", favoriteLang: "" },
  });

  // Prefill from the current user once auth has hydrated.
  useEffect(() => {
    if (user) {
      reset({
        bio: user.bio ?? "",
        favoriteLang: user.favoriteLang ?? "",
      });
    }
  }, [user, reset]);

  const bioValue = watch("bio") ?? "";

  async function onSubmit(values: SettingsValues) {
    setSaveState({ kind: "saving" });
    try {
      const { data } = await api.patch("/users/me", {
        bio: values.bio,
        favoriteLanguage: values.favoriteLang,
      });
      // Keep the auth store in sync so the change is reflected app-wide.
      if (user) {
        setUser({
          ...user,
          bio: values.bio || null,
          favoriteLang: values.favoriteLang || null,
          ...(typeof data === "object" && data !== null ? data : {}),
        });
      }
      reset(values);
      setSaveState({ kind: "success" });
    } catch {
      setSaveState({
        kind: "error",
        message: "Could not save your settings. Please try again.",
      });
    }
  }

  if (isLoading) {
    return (
      <div className="flex min-h-[30vh] items-center justify-center">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
        <span className="sr-only">Loading your settings…</span>
      </div>
    );
  }

  if (!user) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Sign in required</CardTitle>
          <CardDescription>
            You need to be signed in to edit your settings.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild>
            <a href="/login">Go to login</a>
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
          <CardDescription>
            Update your public bio and preferred language.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={handleSubmit(onSubmit)}
            className="space-y-6"
            noValidate
          >
            {/* Bio */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="bio">Bio</Label>
                <span
                  className="text-xs text-muted-foreground"
                  aria-live="polite"
                >
                  {bioValue.length}/{BIO_MAX}
                </span>
              </div>
              <Textarea
                id="bio"
                rows={5}
                maxLength={BIO_MAX}
                placeholder="Tell other duelists a bit about yourself…"
                aria-invalid={errors.bio ? "true" : undefined}
                aria-describedby={errors.bio ? "bio-error" : undefined}
                {...register("bio")}
              />
              {errors.bio && (
                <p id="bio-error" role="alert" className="text-sm text-destructive">
                  {errors.bio.message}
                </p>
              )}
            </div>

            {/* Favorite language */}
            <div className="space-y-2">
              <Label htmlFor="favoriteLang">Favorite language</Label>
              <select
                id="favoriteLang"
                aria-invalid={errors.favoriteLang ? "true" : undefined}
                aria-describedby={
                  errors.favoriteLang ? "favoriteLang-error" : undefined
                }
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                {...register("favoriteLang")}
              >
                <option value="">No preference</option>
                {LANGUAGES.map((lang) => (
                  <option key={lang} value={lang}>
                    {lang}
                  </option>
                ))}
              </select>
              {errors.favoriteLang && (
                <p
                  id="favoriteLang-error"
                  role="alert"
                  className="text-sm text-destructive"
                >
                  {errors.favoriteLang.message}
                </p>
              )}
            </div>

            <div className="flex items-center gap-3">
              <Button
                type="submit"
                disabled={saveState.kind === "saving" || !isDirty}
              >
                {saveState.kind === "saving" && (
                  <Loader2 className="animate-spin" />
                )}
                Save changes
              </Button>
              <p aria-live="polite" className="text-sm">
                {saveState.kind === "success" && (
                  <span className="text-green-600">Settings saved.</span>
                )}
                {saveState.kind === "error" && (
                  <span role="alert" className="text-destructive">
                    {saveState.message}
                  </span>
                )}
              </p>
            </div>
          </form>
        </CardContent>
      </Card>

      <ThemePreference />
    </div>
  );
}

/** Light / Dark / System radio group backed by next-themes. */
function ThemePreference() {
  const [mounted, setMounted] = useState(false);
  const { theme, setTheme } = useTheme();

  useEffect(() => setMounted(true), []);

  const options = [
    { value: "light", label: "Light" },
    { value: "dark", label: "Dark" },
    { value: "system", label: "System" },
  ] as const;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Appearance</CardTitle>
        <CardDescription>Choose how CFNexus looks.</CardDescription>
      </CardHeader>
      <CardContent>
        <fieldset>
          <legend className="sr-only">Theme preference</legend>
          <div
            role="radiogroup"
            aria-label="Theme preference"
            className="flex flex-wrap gap-2"
          >
            {options.map((opt) => {
              const checked = mounted && theme === opt.value;
              return (
                <button
                  key={opt.value}
                  type="button"
                  role="radio"
                  aria-checked={checked}
                  onClick={() => setTheme(opt.value)}
                  className={
                    "inline-flex items-center justify-center rounded-md border px-4 py-2 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring " +
                    (checked
                      ? "border-primary bg-primary text-primary-foreground"
                      : "border-input bg-transparent hover:bg-accent hover:text-accent-foreground")
                  }
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        </fieldset>
      </CardContent>
    </Card>
  );
}
