import { api } from "@/lib/api";

/** An accepted friend (mirrors backend FriendDto). */
export interface Friend {
  cfHandle: string;
  avatarUrl: string | null;
  cfRating: number | null;
  cfRank: string | null;
  duelRating: number | null;
  online: boolean;
  lastSeen: string | null;
}

/** A pending incoming request (mirrors backend IncomingRequestDto). */
export interface IncomingRequest {
  cfHandle: string;
  avatarUrl: string | null;
  cfRating: number | null;
  cfRank: string | null;
  duelRating: number | null;
  requestedAt: string | null;
}

export async function fetchFriends(): Promise<Friend[]> {
  const { data } = await api.get<Friend[]>("/friends");
  return data;
}

export async function fetchIncoming(): Promise<IncomingRequest[]> {
  const { data } = await api.get<IncomingRequest[]>("/friends/incoming");
  return data;
}

export async function sendFriendRequest(handle: string): Promise<void> {
  await api.post(`/friends/request/${encodeURIComponent(handle)}`);
}

export async function acceptFriendRequest(handle: string): Promise<void> {
  await api.post(`/friends/accept/${encodeURIComponent(handle)}`);
}

export async function removeFriend(handle: string): Promise<void> {
  await api.delete(`/friends/${encodeURIComponent(handle)}`);
}

/** Human-friendly "last seen" relative label. */
export function formatLastSeen(lastSeen: string | null): string {
  if (!lastSeen) {
    return "offline";
  }
  const then = new Date(lastSeen).getTime();
  if (Number.isNaN(then)) {
    return "offline";
  }
  const diffMs = Date.now() - then;
  const min = Math.floor(diffMs / 60_000);
  if (min < 1) return "just now";
  if (min < 60) return `${min}m ago`;
  const hours = Math.floor(min / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}
