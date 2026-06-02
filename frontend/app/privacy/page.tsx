import type { Metadata } from "next";
import { SiteHeader } from "@/components/site-header";
import { SiteFooter } from "@/components/site-footer";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "CFNexus privacy policy.",
};

export default function PrivacyPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="container max-w-3xl flex-1 py-10">
        <h1 className="text-3xl font-bold">Privacy Policy</h1>
        <p className="mt-4 text-muted-foreground">
          Placeholder privacy policy. CFNexus stores your public Codeforces
          handle and profile data, duel history, and ratings. Chat messages are
          end-to-end encrypted in your browser and are not readable by the
          server. We do not sell your data.
        </p>
      </main>
      <SiteFooter />
    </div>
  );
}
