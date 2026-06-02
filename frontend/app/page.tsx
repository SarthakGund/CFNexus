import type { Metadata } from "next";
import Link from "next/link";
import {
  Swords,
  Users,
  Lock,
  Code2,
  Trophy,
  PlusCircle,
  Send,
  CheckCircle2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { SiteHeader } from "@/components/site-header";
import { SiteFooter } from "@/components/site-footer";

export function generateMetadata(): Metadata {
  return {
    title: "CFNexus — Real-time Codeforces Duels",
    description:
      "Challenge friends to live 1v1 and team competitive programming duels on real Codeforces problems. First to Accepted wins.",
    alternates: { canonical: "/" },
    openGraph: {
      title: "CFNexus — Real-time Codeforces Duels",
      description:
        "Live 1v1 and team competitive programming duels on real Codeforces problems.",
      url: "/",
      type: "website",
    },
  };
}

const FEATURES = [
  {
    icon: Swords,
    title: "Rated Duels",
    description:
      "Go 1v1 on a real Codeforces problem. ELO-style rating updates after every match.",
  },
  {
    icon: Users,
    title: "Team Battles",
    description:
      "Squad up for unrated 4v4 team duels or chaotic free-for-all rooms.",
  },
  {
    icon: Lock,
    title: "E2E Encrypted Chat",
    description:
      "Trash-talk in real time. Messages are end-to-end encrypted in your browser.",
  },
  {
    icon: Code2,
    title: "In-browser Editor",
    description:
      "Monaco editor with syntax highlighting and a one-click run against the judge.",
  },
];

const STEPS = [
  {
    icon: PlusCircle,
    title: "Create",
    description: "Pick a mode and problem rating, then spin up a room.",
  },
  {
    icon: Send,
    title: "Invite",
    description: "Share the room link with a friend or your team.",
  },
  {
    icon: CheckCircle2,
    title: "Solve",
    description: "First to get Accepted on Codeforces wins the duel.",
  },
];

const LEADERBOARD_PREVIEW = [
  { rank: 1, handle: "tourist", rating: 2100 },
  { rank: 2, handle: "Petr", rating: 1980 },
  { rank: 3, handle: "Benq", rating: 1875 },
  { rank: 4, handle: "Um_nik", rating: 1810 },
  { rank: 5, handle: "ksun48", rating: 1764 },
];

export default function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteHeader />
      <main className="flex-1">
        {/* 1. Hero */}
        <section className="container flex flex-col items-center gap-6 py-24 text-center md:py-32">
          <span className="rounded-full border px-4 py-1 text-sm text-muted-foreground">
            Live competitive programming duels
          </span>
          <h1 className="max-w-3xl text-4xl font-extrabold tracking-tight sm:text-5xl md:text-6xl">
            Duel anyone on{" "}
            <span className="text-primary">real Codeforces problems</span>
          </h1>
          <p className="max-w-2xl text-lg text-muted-foreground">
            Create a room, invite a rival, and race to the first Accepted
            verdict. Rated 1v1, team battles, encrypted chat, and a built-in
            editor.
          </p>
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button asChild size="lg">
              <Link href="/login">
                <Swords className="mr-2 h-5 w-5" aria-hidden="true" />
                Login with Codeforces
              </Link>
            </Button>
            <Button asChild size="lg" variant="outline">
              <Link href="/leaderboard">View Leaderboard</Link>
            </Button>
          </div>
        </section>

        {/* 2. Features */}
        <section className="border-t bg-muted/30 py-20">
          <div className="container">
            <h2 className="mb-12 text-center text-3xl font-bold">
              Everything you need to compete
            </h2>
            <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
              {FEATURES.map((f) => (
                <Card key={f.title}>
                  <CardHeader>
                    <f.icon
                      className="mb-2 h-8 w-8 text-primary"
                      aria-hidden="true"
                    />
                    <CardTitle>{f.title}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <CardDescription>{f.description}</CardDescription>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>
        </section>

        {/* 3. Leaderboard preview */}
        <section className="py-20">
          <div className="container max-w-2xl">
            <div className="mb-8 flex items-center justify-center gap-2">
              <Trophy className="h-6 w-6 text-primary" aria-hidden="true" />
              <h2 className="text-3xl font-bold">Top duelists</h2>
            </div>
            <Card>
              <CardContent className="p-0">
                <ul className="divide-y">
                  {LEADERBOARD_PREVIEW.map((row) => (
                    <li
                      key={row.handle}
                      className="flex items-center justify-between px-6 py-4"
                    >
                      <div className="flex items-center gap-4">
                        <span className="w-6 text-center font-mono text-muted-foreground">
                          {row.rank}
                        </span>
                        <span className="font-medium">{row.handle}</span>
                      </div>
                      <span className="font-mono text-primary">
                        {row.rating}
                      </span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
            <div className="mt-6 text-center">
              <Button asChild variant="link">
                <Link href="/leaderboard">See full leaderboard →</Link>
              </Button>
            </div>
          </div>
        </section>

        {/* 4. How it works */}
        <section className="border-t bg-muted/30 py-20">
          <div className="container">
            <h2 className="mb-12 text-center text-3xl font-bold">
              How it works
            </h2>
            <div className="grid gap-8 md:grid-cols-3">
              {STEPS.map((step, i) => (
                <div
                  key={step.title}
                  className="flex flex-col items-center text-center"
                >
                  <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
                    <step.icon
                      className="h-8 w-8 text-primary"
                      aria-hidden="true"
                    />
                  </div>
                  <h3 className="mb-2 text-xl font-semibold">
                    {i + 1}. {step.title}
                  </h3>
                  <p className="text-muted-foreground">{step.description}</p>
                </div>
              ))}
            </div>
          </div>
        </section>
      </main>

      {/* 5. Footer */}
      <SiteFooter />
    </div>
  );
}
