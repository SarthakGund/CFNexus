"use client";

import { useEffect, useRef, useState } from "react";
import { cn } from "@/lib/utils";
import { useDuelStore } from "@/store/duelStore";

function format(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

/**
 * Counts UP from the duel start. Prefers authoritative `TIMER_UPDATE`
 * (elapsedMs in the store); otherwise ticks locally from `startedAt`.
 */
export function DuelTimer({ className }: { className?: string }) {
  const startedAt = useDuelStore((s) => s.room?.startedAt ?? null);
  const serverElapsedMs = useDuelStore((s) => s.elapsedMs);
  const [localMs, setLocalMs] = useState(0);

  // Keep a ref to the latest server value so the interval reads fresh data.
  const serverRef = useRef(serverElapsedMs);
  serverRef.current = serverElapsedMs;
  const startRef = useRef<number | null>(null);
  startRef.current = startedAt ? new Date(startedAt).getTime() : null;

  useEffect(() => {
    const id = setInterval(() => {
      if (serverRef.current > 0) {
        setLocalMs(serverRef.current);
      } else if (startRef.current) {
        setLocalMs(Date.now() - startRef.current);
      }
    }, 250);
    return () => clearInterval(id);
  }, []);

  const display = serverElapsedMs > 0 ? serverElapsedMs : localMs;

  return (
    <span
      role="timer"
      aria-label="Elapsed duel time"
      className={cn(
        "font-mono tabular-nums text-lg font-semibold",
        className,
      )}
    >
      {format(display)}
    </span>
  );
}

export default DuelTimer;
