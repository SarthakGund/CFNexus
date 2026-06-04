import type { Metadata } from "next";
import { SiteHeader } from "@/components/site-header";
import { SiteFooter } from "@/components/site-footer";
import { SettingsForm } from "./settings-form";

export const metadata: Metadata = {
  title: "Settings",
  description: "Manage your CFNexus profile and appearance preferences.",
};

export default function SettingsPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="container max-w-2xl flex-1 py-10">
        <h1 className="text-3xl font-bold">Settings</h1>
        <p className="mt-2 text-muted-foreground">
          Manage your profile and appearance preferences.
        </p>
        <div className="mt-8">
          <SettingsForm />
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
