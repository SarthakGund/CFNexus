import type { Metadata } from "next";

export const metadata: Metadata = { title: "Settings" };

export default function SettingsPage() {
  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">Settings</h1>
      <p className="mt-2 text-muted-foreground">
        Bio, favorite language, and theme preferences arrive in Phase 7.
      </p>
    </main>
  );
}
