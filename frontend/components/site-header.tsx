import Link from "next/link";
import { Swords } from "lucide-react";
import { NavActions } from "@/components/nav-actions";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
      <div className="container flex h-14 items-center justify-between">
        <Link href="/" className="flex items-center gap-2 font-bold">
          <Swords className="h-5 w-5 text-primary" aria-hidden="true" />
          <span>CFNexus</span>
        </Link>
        <NavActions />
      </div>
    </header>
  );
}
