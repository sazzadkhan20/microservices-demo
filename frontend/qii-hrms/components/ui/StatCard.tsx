import type { ReactNode } from "react";

type Tone = "good" | "warn" | "bad" | "info" | "acc" | "";

const COLOR: Record<string, string> = {
  good: "var(--good)",
  warn: "var(--warn)",
  bad: "var(--bad)",
  info: "var(--info)",
  acc: "var(--accent)",
};

/** KPI tile (`.stat` from globals.css). `value` can be "—" while loading / unavailable. */
export function StatCard({
  label,
  value,
  sub,
  tone = "",
  href,
}: {
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  tone?: Tone;
  href?: ReactNode;
}) {
  return (
    <div className="card stat">
      <span className="label">{label}</span>
      <span className="v" style={tone ? { color: COLOR[tone] } : undefined}>
        {value}
      </span>
      {sub ? <span className="s">{sub}</span> : null}
      {href}
    </div>
  );
}
