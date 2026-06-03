"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import axios from "axios";
import { Loader2, UserPlus, Check, X } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { rankTier } from "@/lib/rank";
import {
  fetchFriends,
  fetchIncoming,
  sendFriendRequest,
  acceptFriendRequest,
  removeFriend,
  formatLastSeen,
  type Friend,
  type IncomingRequest,
} from "@/lib/friends";
import { useFriendNotifications } from "@/hooks/useFriendNotifications";

/**
 * Friends page (spec §14): accepted friends with live presence + ratings,
 * incoming pending requests with accept/reject, and add-by-handle. Real-time
 * notifications trigger a refetch of both lists.
 */
export default function FriendsPage() {
  const [friends, setFriends] = useState<Friend[]>([]);
  const [incoming, setIncoming] = useState<IncomingRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [handle, setHandle] = useState("");
  const [sending, setSending] = useState(false);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [f, i] = await Promise.all([fetchFriends(), fetchIncoming()]);
      setFriends(f);
      setIncoming(i);
      setError(null);
    } catch {
      setError("Could not load your friends. Please try again.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // Refetch on real-time friend events (new request / acceptance).
  useFriendNotifications(
    useCallback(() => {
      void refresh();
    }, [refresh]),
  );

  function describeError(err: unknown, fallback: string): string {
    if (axios.isAxiosError(err)) {
      const status = err.response?.status;
      if (status === 404) return "No user with that handle.";
      if (status === 409) return "You're already connected with that user.";
      if (status === 400) return "You can't add yourself.";
    }
    return fallback;
  }

  async function onSend(e: React.FormEvent) {
    e.preventDefault();
    const h = handle.trim();
    if (!h || sending) return;
    setSending(true);
    setActionMsg(null);
    try {
      await sendFriendRequest(h);
      setHandle("");
      setActionMsg(`Friend request sent to ${h}.`);
      await refresh();
    } catch (err) {
      setActionMsg(describeError(err, "Could not send the request."));
    } finally {
      setSending(false);
    }
  }

  async function onAccept(h: string) {
    setBusy(h);
    try {
      await acceptFriendRequest(h);
      await refresh();
    } catch (err) {
      setActionMsg(describeError(err, "Could not accept the request."));
    } finally {
      setBusy(null);
    }
  }

  async function onRemove(h: string) {
    setBusy(h);
    try {
      await removeFriend(h);
      await refresh();
    } catch (err) {
      setActionMsg(describeError(err, "Could not update the request."));
    } finally {
      setBusy(null);
    }
  }

  return (
    <main className="container py-10">
      <h1 className="text-3xl font-bold">Friends</h1>
      <p className="mt-2 text-muted-foreground">
        Your friends list with live online status.
      </p>

      {/* Add a friend */}
      <Card className="mt-6 p-4">
        <form onSubmit={onSend} className="flex flex-wrap items-center gap-2">
          <input
            value={handle}
            onChange={(e) => setHandle(e.target.value)}
            placeholder="Add a friend by Codeforces handle"
            aria-label="Codeforces handle"
            className="h-9 flex-1 rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
          <Button type="submit" disabled={sending || !handle.trim()}>
            {sending ? (
              <Loader2 className="animate-spin" />
            ) : (
              <UserPlus />
            )}
            Send request
          </Button>
        </form>
        {actionMsg && (
          <p className="mt-2 text-sm text-muted-foreground">{actionMsg}</p>
        )}
      </Card>

      {error && (
        <Card className="mt-6 p-4 text-sm text-destructive">{error}</Card>
      )}

      {loading ? (
        <div className="mt-10 flex justify-center">
          <Loader2 className="size-6 animate-spin text-muted-foreground" />
        </div>
      ) : (
        <div className="mt-8 space-y-8">
          {/* Incoming requests */}
          {incoming.length > 0 && (
            <section>
              <h2 className="mb-3 text-lg font-semibold">
                Incoming requests
                <span className="ml-2 rounded-full bg-primary px-2 py-0.5 text-xs text-primary-foreground">
                  {incoming.length}
                </span>
              </h2>
              <div className="space-y-2">
                {incoming.map((r) => (
                  <Card
                    key={r.cfHandle}
                    className="flex items-center gap-3 p-3"
                  >
                    <Avatar handle={r.cfHandle} url={r.avatarUrl} />
                    <div className="min-w-0 flex-1">
                      <HandleLink
                        handle={r.cfHandle}
                        duelRating={r.duelRating}
                      />
                      <p className="text-xs text-muted-foreground">
                        CF {r.cfRating ?? "—"}
                        {r.cfRank ? ` · ${r.cfRank}` : ""} · Duel{" "}
                        {r.duelRating ?? "—"}
                      </p>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => onAccept(r.cfHandle)}
                      disabled={busy === r.cfHandle}
                    >
                      {busy === r.cfHandle ? (
                        <Loader2 className="animate-spin" />
                      ) : (
                        <Check />
                      )}
                      Accept
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onRemove(r.cfHandle)}
                      disabled={busy === r.cfHandle}
                      aria-label={`Reject request from ${r.cfHandle}`}
                    >
                      <X />
                    </Button>
                  </Card>
                ))}
              </div>
            </section>
          )}

          {/* Accepted friends */}
          <section>
            <h2 className="mb-3 text-lg font-semibold">
              Your friends
              {friends.length > 0 && (
                <span className="ml-2 text-sm font-normal text-muted-foreground">
                  ({friends.length})
                </span>
              )}
            </h2>
            {friends.length === 0 ? (
              <Card className="p-6 text-center text-sm text-muted-foreground">
                No friends yet. Add someone by their Codeforces handle above.
              </Card>
            ) : (
              <div className="space-y-2">
                {friends.map((f) => (
                  <Card
                    key={f.cfHandle}
                    className="flex items-center gap-3 p-3"
                  >
                    <Avatar handle={f.cfHandle} url={f.avatarUrl} />
                    <div className="min-w-0 flex-1">
                      <HandleLink
                        handle={f.cfHandle}
                        duelRating={f.duelRating}
                      />
                      <p className="text-xs text-muted-foreground">
                        CF {f.cfRating ?? "—"}
                        {f.cfRank ? ` · ${f.cfRank}` : ""} · Duel{" "}
                        {f.duelRating ?? "—"}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 text-xs">
                      <span
                        className={cn(
                          "inline-block size-2 rounded-full",
                          f.online ? "bg-green-500" : "bg-muted-foreground/40",
                        )}
                        aria-hidden
                      />
                      <span className="text-muted-foreground">
                        {f.online ? "online" : formatLastSeen(f.lastSeen)}
                      </span>
                    </div>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onRemove(f.cfHandle)}
                      disabled={busy === f.cfHandle}
                    >
                      {busy === f.cfHandle ? (
                        <Loader2 className="animate-spin" />
                      ) : (
                        "Remove"
                      )}
                    </Button>
                  </Card>
                ))}
              </div>
            )}
          </section>
        </div>
      )}
    </main>
  );
}

function Avatar({ handle, url }: { handle: string; url: string | null }) {
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={url ?? `https://avatars.dicebear.com/api/identicon/${handle}.svg`}
      alt=""
      className="size-9 rounded-full bg-muted object-cover"
    />
  );
}

function HandleLink({
  handle,
  duelRating,
}: {
  handle: string;
  duelRating: number | null;
}) {
  const tier = rankTier(duelRating ?? 0);
  return (
    <Link
      href={`/u/${handle}`}
      className={cn(
        "block truncate font-mono font-semibold hover:underline",
        tier.colorClass,
      )}
    >
      {handle}
    </Link>
  );
}
