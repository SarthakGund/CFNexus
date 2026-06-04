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
        <p className="mt-2 text-sm text-muted-foreground">
          Last updated: June 4, 2026
        </p>

        <div className="mt-8 space-y-8 text-sm leading-relaxed text-muted-foreground">
          <p className="text-foreground">
            This placeholder policy explains what data CFNexus collects and how
            it is used. CFNexus is an independent project and is not affiliated
            with Codeforces.
          </p>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              1. Information we store
            </h2>
            <p>
              We store your public Codeforces handle and profile data (such as
              rating and rank), the bio and favorite language you set, your duel
              history, and your duel ratings. We also keep basic session
              information needed to keep you signed in.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              2. End-to-end encrypted chat
            </h2>
            <p>
              Duel chat messages are end-to-end encrypted in your browser. The
              server relays encrypted payloads and cannot read their contents.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              3. How we use your data
            </h2>
            <p>
              We use your data to operate the Service: to authenticate you,
              match you into duels, compute ratings and leaderboards, and
              display public profiles. We do not sell your data or use it for
              third-party advertising.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              4. Cookies
            </h2>
            <p>
              We use a session cookie strictly to keep you signed in. It is not
              used for cross-site tracking.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              5. Your choices
            </h2>
            <p>
              You can edit or clear your bio and favorite language at any time
              from the settings page. Some information, such as your public
              Codeforces handle, is required to use the Service.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              6. Contact
            </h2>
            <p>
              Questions about this policy can be raised through the project
              repository linked in the footer.
            </p>
          </section>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
