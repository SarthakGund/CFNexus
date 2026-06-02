import type { Metadata } from "next";

export const metadata: Metadata = { title: "Friends" };

export default function FriendsPage() {
  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">Friends</h1>
      <p className="mt-2 text-muted-foreground">
        Your friends list with online status arrives in Phase 6.
      </p>
    </main>
  );
}
