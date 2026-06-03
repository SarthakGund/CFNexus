"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import {
  Check,
  Copy,
  ExternalLink,
  Flag,
  Handshake,
  Loader2,
  Play,
  X,
} from "lucide-react";
import api from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";
import { useDuel } from "@/hooks/useDuel";
import { useDuelStore, type Participant } from "@/store/duelStore";
import { DuelTimer } from "@/components/duel/DuelTimer";
import { TeamSlotSelector } from "@/components/duel/TeamSlotSelector";
import { CodeEditor } from "@/components/duel/CodeEditor";
import { ChatPanel } from "@/components/duel/ChatPanel";

const DRAW_AUTO_DECLINE_MS = 10_000;

export default function DuelRoomPage({
  params,
}: {
  params: Promise<{ roomCode: string }>;
}) {
  const { roomCode } = use(params);
  const { user } = useAuth();
  const { connected, loading, error, resign, offerDraw, respondDraw, refresh } =
    useDuel(roomCode);

  const room = useDuelStore((s) => s.room);
  const participants = useDuelStore((s) => s.participants);
  const status = useDuelStore((s) => s.status);
  const drawOfferFrom = useDuelStore((s) => s.drawOfferFrom);

  const isHost = !!user && !!room && room.hostId === user.id;

  if (loading) {
    return (
      <main className="container flex min-h-[50vh] items-center justify-center py-10">
        <Loader2 className="size-6 animate-spin text-muted-foreground" />
      </main>
    );
  }

  if (error || !room) {
    return (
      <main className="container py-10">
        <h1 className="text-2xl font-bold">Duel Room</h1>
        <p role="alert" className="mt-2 text-destructive">
          {error ?? "Room not found."}
        </p>
      </main>
    );
  }

  return (
    <main className="container py-6">
      <ConnectionBadge connected={connected} />

      {status === "WAITING" && (
        <WaitingView
          roomCode={roomCode}
          isHost={isHost}
          onStarted={refresh}
        />
      )}

      {status === "IN_PROGRESS" && (
        <InProgressView
          roomCode={roomCode}
          resign={resign}
          offerDraw={offerDraw}
        />
      )}

      {status === "COMPLETED" && <CompletedView participants={participants} />}

      {status === "CANCELLED" && (
        <p className="mt-6 text-muted-foreground">This duel was cancelled.</p>
      )}

      {/* Draw offer prompt (only if someone else offered). */}
      {drawOfferFrom && user && drawOfferFrom !== user.id && (
        <DrawPrompt onRespond={respondDraw} />
      )}

      <p className="sr-only" aria-live="polite">
        {connected ? "Connected to duel server." : "Disconnected."}
      </p>
    </main>
  );
}

/* ------------------------------------------------------------------ */
/* Sub-views                                                           */
/* ------------------------------------------------------------------ */

function ConnectionBadge({ connected }: { connected: boolean }) {
  return (
    <div className="mb-4 flex items-center gap-2 text-xs text-muted-foreground">
      <span
        className={cn(
          "inline-block size-2 rounded-full",
          connected ? "bg-green-500" : "bg-amber-500",
        )}
        aria-hidden
      />
      {connected ? "Live" : "Connecting…"}
    </div>
  );
}

function WaitingView({
  roomCode,
  isHost,
  onStarted,
}: {
  roomCode: string;
  isHost: boolean;
  onStarted: () => void | Promise<void>;
}) {
  const [copied, setCopied] = useState(false);
  const [starting, setStarting] = useState(false);
  const [startError, setStartError] = useState<string | null>(null);
  const participants = useDuelStore((s) => s.participants);

  const inviteUrl =
    typeof window !== "undefined"
      ? `${window.location.origin}/duel/${roomCode}`
      : `/duel/${roomCode}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(inviteUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      /* ignore clipboard errors */
    }
  }

  async function start() {
    setStarting(true);
    setStartError(null);
    try {
      await api.post(`/duels/${roomCode}/start`);
      await onStarted();
    } catch {
      setStartError("Could not start the duel. Need enough players?");
    } finally {
      setStarting(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Waiting Room</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Room code:{" "}
            <span className="font-mono font-semibold">{roomCode}</span>
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={copy}
            aria-label="Copy invite link"
          >
            {copied ? <Check /> : <Copy />}
            {copied ? "Copied" : "Copy invite"}
          </Button>
          {isHost && (
            <Button
              type="button"
              onClick={start}
              disabled={starting || participants.length < 2}
            >
              {starting ? <Loader2 className="animate-spin" /> : <Play />}
              Start Duel
            </Button>
          )}
        </div>
      </div>

      {startError && (
        <p role="alert" className="text-sm text-destructive">
          {startError}
        </p>
      )}

      <TeamSlotSelector roomCode={roomCode} onJoined={() => undefined} />
    </div>
  );
}

function InProgressView({
  roomCode,
  resign,
  offerDraw,
}: {
  roomCode: string;
  resign: () => void;
  offerDraw: () => void;
}) {
  const problem = useDuelStore((s) => s.problem);
  const participants = useDuelStore((s) => s.participants);

  return (
    <div className="space-y-4">
      {/* Header bar */}
      <div className="flex flex-col gap-3 rounded-xl border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="min-w-0">
          <h1 className="truncate text-lg font-semibold">
            {problem?.name ?? "Loading problem…"}
          </h1>
          {problem && (
            <p className="text-sm text-muted-foreground">
              Rating {problem.rating}
            </p>
          )}
        </div>
        <div className="flex items-center gap-4">
          <DuelTimer />
          <Button
            type="button"
            variant="secondary"
            onClick={offerDraw}
            aria-label="Offer a draw"
          >
            <Handshake />
            Draw
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={resign}
            aria-label="Resign the duel"
          >
            <Flag />
            Resign
          </Button>
        </div>
      </div>

      {/* Main split: problem | editor */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <Card>
          <CardHeader className="flex-row items-center justify-between space-y-0">
            <CardTitle className="text-base">Problem</CardTitle>
            {problem && (
              <Button asChild size="sm" variant="outline">
                <a href={problem.url} target="_blank" rel="noreferrer">
                  <ExternalLink />
                  Open on Codeforces
                </a>
              </Button>
            )}
          </CardHeader>
          <CardContent>
            {problem ? (
              <iframe
                title={`Problem ${problem.name}`}
                src={problem.url}
                className="h-[60vh] w-full rounded-md border"
              />
            ) : (
              <p className="text-sm text-muted-foreground">
                Waiting for the problem…
              </p>
            )}
          </CardContent>
        </Card>

        <CodeEditor
          roomCode={roomCode}
          problemUrl={problem?.url}
          contestId={problem?.contestId}
          index={problem?.index}
        />
      </div>

      {/* Participants bar */}
      <ParticipantsBar participants={participants} />

      {/* End-to-end encrypted chat */}
      <ChatPanel roomCode={roomCode} />
    </div>
  );
}

function ParticipantsBar({ participants }: { participants: Participant[] }) {
  return (
    <div className="flex flex-wrap gap-3 rounded-xl border bg-card p-3">
      {participants.map((p) => (
        <div
          key={p.userId}
          className={cn(
            "flex items-center gap-2 rounded-lg border px-3 py-1.5",
            p.status === "DISCONNECTED" && "opacity-50",
          )}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={
              p.avatarUrl ??
              `https://avatars.dicebear.com/api/identicon/${p.handle}.svg`
            }
            alt=""
            className="h-6 w-6 rounded-full bg-muted object-cover"
          />
          <span className="font-mono text-sm">{p.handle}</span>
          {p.team != null && (
            <span className="text-xs text-muted-foreground">
              T{p.team}
            </span>
          )}
          {p.status === "DISCONNECTED" && (
            <span className="text-xs text-amber-500">offline</span>
          )}
        </div>
      ))}
    </div>
  );
}

function CompletedView({ participants }: { participants: Participant[] }) {
  const result = useDuelStore((s) => s.result);
  const roomType = useDuelStore((s) => s.room?.type);
  const winner = participants.find((p) => p.userId === result?.winnerId);
  const isTeamDuel = roomType === "UNRATED_TEAM";

  const durationLabel =
    result?.solveDurationMs != null
      ? `${Math.floor(result.solveDurationMs / 60000)}m ${Math.floor(
          (result.solveDurationMs % 60000) / 1000,
        )}s`
      : null;

  return (
    <div className="mx-auto max-w-md">
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Duel Complete</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {result?.resultType === "DRAW" ? (
            <p className="text-lg font-semibold">It&apos;s a draw.</p>
          ) : winner ? (
            <p className="text-lg">
              {isTeamDuel && winner.team != null ? (
                <>
                  Winner:{" "}
                  <span className="font-semibold">Team {winner.team}</span>{" "}
                  <span className="text-sm text-muted-foreground">
                    (solved by{" "}
                    <span className="font-mono">{winner.handle}</span>)
                  </span>
                </>
              ) : (
                <>
                  Winner:{" "}
                  <span className="font-mono font-semibold">
                    {winner.handle}
                  </span>
                </>
              )}
            </p>
          ) : (
            <p className="text-lg">No winner recorded.</p>
          )}
          {result?.resultType && (
            <p className="text-sm text-muted-foreground">
              Result: {result.resultType}
            </p>
          )}
          {durationLabel && (
            <p className="text-sm text-muted-foreground">
              Solve time: {durationLabel}
            </p>
          )}
          <Button asChild variant="outline">
            <Link href="/duel/create">New Duel</Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

function DrawPrompt({
  onRespond,
}: {
  onRespond: (accept: boolean) => void;
}) {
  const [secondsLeft, setSecondsLeft] = useState(
    DRAW_AUTO_DECLINE_MS / 1000,
  );

  useEffect(() => {
    const tick = setInterval(
      () => setSecondsLeft((s) => Math.max(0, s - 1)),
      1000,
    );
    const auto = setTimeout(() => onRespond(false), DRAW_AUTO_DECLINE_MS);
    return () => {
      clearInterval(tick);
      clearTimeout(auto);
    };
  }, [onRespond]);

  return (
    <div
      role="alertdialog"
      aria-label="Draw offer"
      className="fixed inset-x-0 bottom-6 z-50 mx-auto w-[min(90vw,28rem)] rounded-xl border bg-card p-4 shadow-lg"
    >
      <p className="font-medium">Your opponent offered a draw.</p>
      <p className="mt-1 text-sm text-muted-foreground">
        Auto-declines in {secondsLeft}s.
      </p>
      <div className="mt-3 flex gap-2">
        <Button type="button" onClick={() => onRespond(true)}>
          <Check />
          Accept
        </Button>
        <Button
          type="button"
          variant="outline"
          onClick={() => onRespond(false)}
        >
          <X />
          Decline
        </Button>
      </div>
    </div>
  );
}
