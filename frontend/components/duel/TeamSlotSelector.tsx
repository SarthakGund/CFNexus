"use client";

import { useState } from "react";
import { Loader2, UserPlus } from "lucide-react";
import api from "@/lib/api";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import {
  useDuelStore,
  type DuelType,
  type Participant,
  type RoomState,
} from "@/store/duelStore";

interface Props {
  roomCode: string;
  /** Called with fresh room state after a successful join. */
  onJoined?: (room: RoomState) => void;
}

function findOccupant(
  participants: Participant[],
  team: number | null,
  slot: number,
): Participant | undefined {
  return participants.find(
    (p) => (p.team ?? null) === team && p.slot === slot,
  );
}

/**
 * Slot picker shown while a room is WAITING.
 * - RATED_1V1  -> two side-by-side player cards.
 * - UNRATED_TEAM -> 2x4 grid (Team 1 | Team 2).
 * - UNRATED_FFA -> single column of up to 4 slots.
 */
export function TeamSlotSelector({ roomCode, onJoined }: Props) {
  const { user } = useAuth();
  const type = useDuelStore((s) => s.room?.type) as DuelType | undefined;
  const participants = useDuelStore((s) => s.participants);

  const [joining, setJoining] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const alreadyIn = !!user && participants.some((p) => p.userId === user.id);

  async function join(team: number | null, slot: number, key: string) {
    setJoining(key);
    setError(null);
    try {
      const body: { team?: number; slot?: number } = { slot };
      if (team !== null) body.team = team;
      const { data } = await api.post<RoomState>(
        `/duels/${roomCode}/join`,
        body,
      );
      useDuelStore.getState().setRoom(data);
      onJoined?.(data);
    } catch {
      setError("Could not join that slot. It may be taken.");
    } finally {
      setJoining(null);
    }
  }

  function SlotCard({
    team,
    slot,
    label,
  }: {
    team: number | null;
    slot: number;
    label: string;
  }) {
    const occupant = findOccupant(participants, team, slot);
    const key = `${team ?? "x"}-${slot}`;
    const isMe = !!user && occupant?.userId === user.id;
    const busy = joining === key;

    return (
      <div
        className={cn(
          "flex flex-col items-center justify-center gap-3 rounded-xl border p-6 text-center transition-colors",
          occupant ? "bg-muted/40" : "border-dashed",
          isMe && "border-primary ring-1 ring-primary",
        )}
      >
        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
          {label}
        </span>
        {occupant ? (
          <div className="flex flex-col items-center gap-2">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={
                occupant.avatarUrl ??
                `https://avatars.dicebear.com/api/identicon/${occupant.handle}.svg`
              }
              alt=""
              className="h-10 w-10 rounded-full bg-muted object-cover"
            />
            <span className="font-mono text-sm font-semibold">
              {occupant.handle}
            </span>
            {isMe && (
              <span className="text-xs text-primary">You</span>
            )}
          </div>
        ) : (
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={alreadyIn || busy}
            onClick={() => join(team, slot, key)}
            aria-label={`Join ${label}`}
          >
            {busy ? (
              <Loader2 className="animate-spin" />
            ) : (
              <UserPlus />
            )}
            Join
          </Button>
        )}
      </div>
    );
  }

  let grid: React.ReactNode = null;

  if (type === "RATED_1V1") {
    grid = (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <SlotCard team={1} slot={1} label="Player 1" />
        <SlotCard team={2} slot={1} label="Player 2" />
      </div>
    );
  } else if (type === "UNRATED_TEAM") {
    grid = (
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        {[1, 2].map((team) => (
          <div key={team} className="space-y-3">
            <h3 className="text-sm font-semibold">Team {team}</h3>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {[1, 2, 3, 4].map((slot) => (
                <SlotCard
                  key={slot}
                  team={team}
                  slot={slot}
                  label={`Slot ${slot}`}
                />
              ))}
            </div>
          </div>
        ))}
      </div>
    );
  } else if (type === "UNRATED_FFA") {
    grid = (
      <div className="mx-auto grid max-w-md grid-cols-1 gap-3">
        {[1, 2, 3, 4].map((slot) => (
          <SlotCard key={slot} team={null} slot={slot} label={`Slot ${slot}`} />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {grid}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      {alreadyIn && (
        <p className="text-sm text-muted-foreground">
          You&apos;ve joined. Waiting for the host to start the duel.
        </p>
      )}
    </div>
  );
}

export default TeamSlotSelector;
