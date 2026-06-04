"use client";

import Link from "next/link";
import { useAuthStore } from "@/store/authStore";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme-toggle";

export function NavActions() {
  const { user, isLoading, login, logout } = useAuthStore();

  return (
    <nav className="flex items-center gap-2 text-sm">
      <Button asChild variant="ghost" size="sm">
        <Link href="/leaderboard">Leaderboard</Link>
      </Button>

      {!isLoading && user && (
        <>
          <Button asChild variant="ghost" size="sm">
            <Link href="/dashboard">Dashboard</Link>
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href="/duel/create">Duel</Link>
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href="/friends">Friends</Link>
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href="/settings">Settings</Link>
          </Button>
          <span className="px-2 text-muted-foreground font-medium">{user.cfHandle}</span>
          <Button variant="outline" size="sm" onClick={() => logout()}>
            Logout
          </Button>
        </>
      )}

      {!isLoading && !user && (
        <Button size="sm" onClick={() => login()}>
          Login
        </Button>
      )}

      <ThemeToggle />
    </nav>
  );
}
