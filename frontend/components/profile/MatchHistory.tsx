"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import api from "@/lib/api";
import { formatDuration, type MatchRow, type PagedResponse } from "@/lib/profile";

const PAGE_SIZE = 10;

function resultClass(result: string): string {
  switch (result) {
    case "WIN":
      return "text-green-500";
    case "LOSS":
    case "RESIGN":
      return "text-red-500";
    default:
      return "text-muted-foreground";
  }
}

function formatDelta(delta: number | null): string {
  if (delta == null) {
    return "—";
  }
  return delta > 0 ? `+${delta}` : `${delta}`;
}

export function MatchHistory({ handle }: { handle: string }) {
  const [page, setPage] = useState(0);
  const [data, setData] = useState<PagedResponse<MatchRow> | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api
      .get<PagedResponse<MatchRow>>(
        `/users/${encodeURIComponent(handle)}/match-history?page=${page}&size=${PAGE_SIZE}`,
      )
      .then((res) => !cancelled && setData(res.data))
      .catch(() => !cancelled && setData({ items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 }))
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [handle, page]);

  const rows = data?.items ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Match History</CardTitle>
      </CardHeader>
      <CardContent>
        {loading && !data ? (
          <div className="h-40 w-full animate-pulse rounded-md bg-muted" />
        ) : rows.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">No matches played yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-muted-foreground">
                  <th className="py-2 pr-4 font-medium">Date</th>
                  <th className="py-2 pr-4 font-medium">Opponent</th>
                  <th className="py-2 pr-4 font-medium">Problem</th>
                  <th className="py-2 pr-4 font-medium">Result</th>
                  <th className="py-2 pr-4 font-medium">Rating</th>
                  <th className="py-2 pr-4 font-medium">Duration</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((m, i) => (
                  <tr key={`${m.roomId}-${i}`} className="border-b last:border-0">
                    <td className="py-2 pr-4 whitespace-nowrap text-muted-foreground">
                      {new Date(m.playedAt).toLocaleDateString()}
                    </td>
                    <td className="py-2 pr-4">
                      {m.opponentHandles.length > 0
                        ? m.opponentHandles.map((h, idx) => (
                            <span key={h}>
                              {idx > 0 && ", "}
                              <Link href={`/u/${h}`} className="hover:underline">
                                {h}
                              </Link>
                            </span>
                          ))
                        : "—"}
                    </td>
                    <td className="py-2 pr-4">
                      {m.problemUrl ? (
                        <a
                          href={m.problemUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="hover:underline"
                        >
                          {m.problemId ?? "Problem"}
                          {m.problemRating ? ` (${m.problemRating})` : ""}
                        </a>
                      ) : (
                        (m.problemId ?? "—")
                      )}
                    </td>
                    <td className={`py-2 pr-4 font-medium ${resultClass(m.result)}`}>{m.result}</td>
                    <td className="py-2 pr-4 whitespace-nowrap">
                      {m.ratingAfter != null ? (
                        <span>
                          {m.ratingAfter}{" "}
                          <span className={m.ratingDelta && m.ratingDelta < 0 ? "text-red-500" : "text-green-500"}>
                            ({formatDelta(m.ratingDelta)})
                          </span>
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="py-2 pr-4 whitespace-nowrap text-muted-foreground">
                      {formatDuration(m.durationMs)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <Button
              variant="outline"
              size="sm"
              disabled={page <= 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              Previous
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
