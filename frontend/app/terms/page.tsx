import type { Metadata } from "next";
import { SiteHeader } from "@/components/site-header";
import { SiteFooter } from "@/components/site-footer";

export const metadata: Metadata = {
  title: "Terms of Service",
  description: "CFNexus terms of service.",
};

export default function TermsPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="container max-w-3xl flex-1 py-10">
        <h1 className="text-3xl font-bold">Terms of Service</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Last updated: June 4, 2026
        </p>

        <div className="mt-8 space-y-8 text-sm leading-relaxed text-muted-foreground">
          <p className="text-foreground">
            These placeholder terms govern your use of CFNexus (the
            &ldquo;Service&rdquo;). CFNexus is an independent project and is not
            affiliated with, sponsored by, or endorsed by Codeforces. By
            accessing or using the Service you agree to these terms. If you do
            not agree, do not use the Service.
          </p>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              1. Accounts and authentication
            </h2>
            <p>
              You sign in with your Codeforces identity. You are responsible for
              activity that occurs under your account and for keeping your
              linked credentials secure. You must comply with the Codeforces
              terms of use for any linked accounts and submissions.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              2. Fair play
            </h2>
            <p>
              You agree to compete honestly. Cheating, sharing solutions during
              a live duel, impersonating other users, or otherwise manipulating
              ratings and results is prohibited and may result in suspension or
              removal from the Service.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              3. User content
            </h2>
            <p>
              You retain ownership of the bio, messages, and code you submit.
              You grant CFNexus a limited licence to store and display this
              content as needed to operate the Service. You are solely
              responsible for the content you provide and must not post unlawful
              or infringing material.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              4. Availability and changes
            </h2>
            <p>
              The Service is provided on an &ldquo;as is&rdquo; and &ldquo;as
              available&rdquo; basis without warranties of any kind. We may
              modify, suspend, or discontinue any part of the Service at any
              time. We may also update these terms; continued use after changes
              take effect constitutes acceptance.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              5. Limitation of liability
            </h2>
            <p>
              To the maximum extent permitted by law, CFNexus and its
              contributors are not liable for any indirect, incidental, or
              consequential damages arising from your use of the Service.
            </p>
          </section>

          <section className="space-y-2">
            <h2 className="text-xl font-semibold text-foreground">
              6. Contact
            </h2>
            <p>
              Questions about these terms can be raised through the project
              repository linked in the footer.
            </p>
          </section>
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
