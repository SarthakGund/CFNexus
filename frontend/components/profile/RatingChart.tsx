"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

export interface ChartPoint {
  /** X-axis label (formatted date). */
  label: string;
  rating: number;
}

interface RatingChartProps {
  data: ChartPoint[];
  /** Stroke color for the line (hsl/css color). */
  color?: string;
}

/**
 * Presentational Recharts LineChart of rating over time. Loaded via
 * {@code next/dynamic} (ssr: false) so Recharts never runs on the server.
 */
export default function RatingChart({ data, color = "hsl(var(--primary))" }: RatingChartProps) {
  return (
    <ResponsiveContainer width="100%" height={240}>
      <LineChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: -8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis
          dataKey="label"
          tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))" }}
          tickLine={false}
        />
        <YAxis
          domain={["dataMin - 50", "dataMax + 50"]}
          tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))" }}
          tickLine={false}
          width={48}
          allowDecimals={false}
        />
        <Tooltip
          contentStyle={{
            background: "hsl(var(--popover))",
            border: "1px solid hsl(var(--border))",
            borderRadius: 8,
            color: "hsl(var(--popover-foreground))",
            fontSize: 12,
          }}
        />
        <Line
          type="monotone"
          dataKey="rating"
          stroke={color}
          strokeWidth={2}
          dot={{ r: 2 }}
          activeDot={{ r: 4 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
