import type { Metadata } from "next";
import "./globals.css";
import { ThemeProvider } from "@/components/theme-provider";
import { AuthBootstrap } from "@/components/auth-bootstrap";

export const metadata: Metadata = {
  metadataBase: new URL(
    process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000",
  ),
  title: {
    default: "CFNexus — Real-time Codeforces Duels",
    template: "%s · CFNexus",
  },
  description:
    "Challenge friends to live competitive programming duels on Codeforces problems. Rated 1v1, team battles, end-to-end encrypted chat, and an in-browser code editor.",
  applicationName: "CFNexus",
  keywords: [
    "codeforces",
    "competitive programming",
    "duel",
    "1v1",
    "coding battle",
    "leaderboard",
  ],
  openGraph: {
    type: "website",
    siteName: "CFNexus",
    title: "CFNexus — Real-time Codeforces Duels",
    description:
      "Challenge friends to live competitive programming duels on Codeforces problems.",
  },
  twitter: {
    card: "summary_large_image",
    title: "CFNexus — Real-time Codeforces Duels",
    description:
      "Challenge friends to live competitive programming duels on Codeforces problems.",
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="min-h-screen bg-background font-sans antialiased">
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <AuthBootstrap />
          {children}
        </ThemeProvider>
      </body>
    </html>
  );
}
