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
        <p className="mt-4 text-muted-foreground">
          Placeholder terms. CFNexus is an independent project and is not
          affiliated with or endorsed by Codeforces. By using this service you
          agree to compete fairly and to comply with the Codeforces terms of
          use for any linked accounts and submissions.
        </p>
      </main>
      <SiteFooter />
    </div>
  );
}
