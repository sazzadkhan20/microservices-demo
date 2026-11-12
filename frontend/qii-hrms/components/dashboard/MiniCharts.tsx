"use client";

import type { ReactNode } from "react";

/** Lightweight SVG charts for the admin dashboard (no chart library). */

export type Slice = { label: string; value: number; color: string };

export function DonutChart({
  slices,
  size = 148,
  thickness = 22,
  center,
}: {
  slices: Slice[];
  size?: number;
  thickness?: number;
  center?: ReactNode;
}) {
  const total = slices.reduce((s, x) => s + Math.max(0, x.value), 0) || 1;
  const r = (size - thickness) / 2;
  const c = size / 2;
  const circ = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div style={{ position: "relative", width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle cx={c} cy={c} r={r} fill="none" stroke="var(--sunk)" strokeWidth={thickness} />
        {slices.map((sl) => {
          const len = (Math.max(0, sl.value) / total) * circ;
          const el = (
            <circle
              key={sl.label}
              cx={c}
              cy={c}
              r={r}
              fill="none"
              stroke={sl.color}
              strokeWidth={thickness}
              strokeDasharray={`${len} ${circ - len}`}
              strokeDashoffset={-offset}
              strokeLinecap="butt"
              transform={`rotate(-90 ${c} ${c})`}
            />
          );
          offset += len;
          return el;
        })}
      </svg>
      {center ? (
        <div
          style={{
            position: "absolute",
            inset: 0,
            display: "grid",
            placeItems: "center",
            textAlign: "center",
            pointerEvents: "none",
            padding: thickness,
          }}
        >
          {center}
        </div>
      ) : null}
    </div>
  );
}

export function HBarList({
  items,
  max,
}: {
  items: { label: string; value: number; color?: string }[];
  max?: number;
}) {
  const peak = Math.max(1, max ?? Math.max(1, ...items.map((i) => i.value)));
  if (items.length === 0) {
    return <p className="muted small">No data</p>;
  }
  return (
    <div className="stack" style={{ gap: 8 }}>
      {items.map((it) => (
        <div key={it.label} className="stack" style={{ gap: 3 }}>
          <div className="row between small">
            <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", maxWidth: "75%" }}>
              {it.label}
            </span>
            <span className="mono">{it.value}</span>
          </div>
          <div style={{ background: "var(--sunk)", borderRadius: 4, height: 8 }}>
            <div
              style={{
                width: `${(it.value / peak) * 100}%`,
                background: it.color ?? "var(--accent)",
                height: "100%",
                borderRadius: 4,
                minWidth: it.value > 0 ? 4 : 0,
              }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

export function ProgressBar({
  pct,
  tone = "acc",
}: {
  pct: number;
  tone?: "good" | "warn" | "bad" | "acc";
}) {
  const color =
    tone === "good"
      ? "var(--good)"
      : tone === "warn"
        ? "var(--warn)"
        : tone === "bad"
          ? "var(--bad)"
          : "var(--accent)";
  const w = Math.max(0, Math.min(100, pct));
  return (
    <div
      style={{ background: "var(--sunk)", borderRadius: 4, height: 8, minWidth: 72 }}
      title={`${pct}%`}
      role="progressbar"
      aria-valuenow={w}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div style={{ width: `${w}%`, background: color, height: "100%", borderRadius: 4 }} />
    </div>
  );
}
