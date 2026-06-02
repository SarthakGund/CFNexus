import { create } from "zustand";

/* ------------------------------------------------------------------ */
/* Domain types — mirror the Phase 2 backend contract.                 */
/* ------------------------------------------------------------------ */

export type DuelType = "RATED_1V1" | "UNRATED_TEAM" | "UNRATED_FFA";

export type DuelStatus =
  | "WAITING"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type ParticipantStatus =
  | "JOINED"
  | "READY"
  | "DISCONNECTED"
  | "SOLVED"
  | string;

export interface Problem {
  problemId: string;
  contestId: number;
  index: string;
  name: string;
  url: string;
  rating: number;
}

export interface Participant {
  userId: string;
  handle: string;
  avatarUrl: string | null;
  team: number | null;
  slot: number | null;
  status: ParticipantStatus;
}

export interface RoomState {
  roomCode: string;
  type: DuelType;
  status: DuelStatus;
  hostId: string;
  problemRating: number;
  problem: Problem | null;
  participants: Participant[];
  startedAt: string | null;
}

export type DuelResultType =
  | "SOLVE"
  | "RESIGN"
  | "DRAW"
  | "TIMEOUT"
  | "DISCONNECT"
  | string;

export interface DuelResult {
  winnerId: string | null;
  loserIds: string[];
  resultType: DuelResultType;
  solveDurationMs: number | null;
}

/* ------------------------------------------------------------------ */
/* WebSocket event union, discriminated by `eventType`.                */
/* ------------------------------------------------------------------ */

export type DuelEvent =
  | { eventType: "ROOM_STATE"; room: RoomState }
  | {
      eventType: "PLAYER_JOINED";
      userId: string;
      handle: string;
      avatarUrl?: string | null;
      team: number | null;
      slot: number | null;
    }
  | { eventType: "PLAYER_LEFT"; userId: string }
  | {
      eventType: "DUEL_STARTED";
      problemId: string;
      contestId?: number;
      index?: string;
      name: string;
      url: string;
      rating: number;
      startedAt: string;
    }
  | { eventType: "DRAW_OFFERED"; byUserId: string }
  | { eventType: "DRAW_DECLINED"; byUserId: string }
  | {
      eventType: "DUEL_ENDED";
      winnerId: string | null;
      loserIds: string[];
      resultType: DuelResultType;
      solveDurationMs: number | null;
    }
  | {
      eventType: "PLAYER_DISCONNECTED";
      userId: string;
      gracePeriodSeconds: number;
    }
  | { eventType: "PLAYER_RECONNECTED"; userId: string }
  | { eventType: "TIMER_UPDATE"; elapsedMs: number };

/* ------------------------------------------------------------------ */
/* Store                                                               */
/* ------------------------------------------------------------------ */

interface DuelState {
  room: RoomState | null;
  participants: Participant[];
  problem: Problem | null;
  status: DuelStatus | null;
  elapsedMs: number;
  /** userId of whoever offered the still-pending draw, or null. */
  drawOfferFrom: string | null;
  result: DuelResult | null;

  setRoom: (room: RoomState) => void;
  applyEvent: (event: DuelEvent) => void;
  reset: () => void;
}

const initialState = {
  room: null,
  participants: [] as Participant[],
  problem: null as Problem | null,
  status: null as DuelStatus | null,
  elapsedMs: 0,
  drawOfferFrom: null as string | null,
  result: null as DuelResult | null,
};

export const useDuelStore = create<DuelState>((set) => ({
  ...initialState,

  setRoom: (room) =>
    set({
      room,
      participants: room.participants,
      problem: room.problem,
      status: room.status,
    }),

  applyEvent: (event) =>
    set((state) => {
      switch (event.eventType) {
        case "ROOM_STATE": {
          return {
            room: event.room,
            participants: event.room.participants,
            problem: event.room.problem,
            status: event.room.status,
          };
        }

        case "PLAYER_JOINED": {
          // Replace if already present (e.g. slot change), else append.
          const without = state.participants.filter(
            (p) => p.userId !== event.userId,
          );
          const joined: Participant = {
            userId: event.userId,
            handle: event.handle,
            avatarUrl: event.avatarUrl ?? null,
            team: event.team,
            slot: event.slot,
            status: "JOINED",
          };
          const participants = [...without, joined];
          return {
            participants,
            room: state.room ? { ...state.room, participants } : state.room,
          };
        }

        case "PLAYER_LEFT": {
          const participants = state.participants.filter(
            (p) => p.userId !== event.userId,
          );
          return {
            participants,
            room: state.room ? { ...state.room, participants } : state.room,
          };
        }

        case "DUEL_STARTED": {
          const problem: Problem = {
            problemId: event.problemId,
            contestId: event.contestId ?? 0,
            index: event.index ?? "",
            name: event.name,
            url: event.url,
            rating: event.rating,
          };
          return {
            problem,
            status: "IN_PROGRESS",
            elapsedMs: 0,
            room: state.room
              ? {
                  ...state.room,
                  problem,
                  status: "IN_PROGRESS",
                  startedAt: event.startedAt,
                }
              : state.room,
          };
        }

        case "DRAW_OFFERED":
          return { drawOfferFrom: event.byUserId };

        case "DRAW_DECLINED":
          return { drawOfferFrom: null };

        case "DUEL_ENDED": {
          const result: DuelResult = {
            winnerId: event.winnerId,
            loserIds: event.loserIds,
            resultType: event.resultType,
            solveDurationMs: event.solveDurationMs,
          };
          return {
            result,
            status: "COMPLETED",
            drawOfferFrom: null,
            room: state.room
              ? { ...state.room, status: "COMPLETED" }
              : state.room,
          };
        }

        case "PLAYER_DISCONNECTED": {
          const participants = state.participants.map((p) =>
            p.userId === event.userId
              ? { ...p, status: "DISCONNECTED" }
              : p,
          );
          return { participants };
        }

        case "PLAYER_RECONNECTED": {
          const participants = state.participants.map((p) =>
            p.userId === event.userId ? { ...p, status: "JOINED" } : p,
          );
          return { participants };
        }

        case "TIMER_UPDATE":
          return { elapsedMs: event.elapsedMs };

        default:
          return {};
      }
    }),

  reset: () => set({ ...initialState }),
}));
