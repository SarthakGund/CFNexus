import type { Metadata } from "next";

export const metadata: Metadata = { title: "Welcome to CFNexus" };

export default function OnboardingPage() {
  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">Welcome to CFNexus</h1>
      <p className="mt-2 text-muted-foreground">
        The 3-step first-login walkthrough (welcome, profile setup, how it
        works) arrives in Phase 3.
      </p>
    </main>
  );
}
