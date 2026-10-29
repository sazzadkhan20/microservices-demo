import { humanize } from "@/lib/format";

type Tone = "good" | "warn" | "bad" | "info" | "acc" | "";

const MAP: Record<string, Tone> = {
  // employees
  active: "good",
  "on leave": "info",
  on_leave: "info",
  inactive: "",
  resigned: "bad",
  terminated: "bad",
  probation: "warn",
  // leave / corrections
  approved: "good",
  pending: "warn",
  pending_relief: "warn",
  pending_approval: "warn",
  submitted: "warn",
  in_review: "warn",
  rejected: "bad",
  withdrawn: "",
  cancelled: "",
  canceled: "",
  cancel_pending: "warn",
  // payroll
  draft: "warn",
  finalized: "good",
  final: "good",
  // tasks
  open: "info",
  in_progress: "acc",
  done: "good",
  closed: "",
  // generic
  present: "good",
  late: "warn",
  absent: "bad",
  half_day: "info",
  read: "",
  unread: "acc",
  onboarding: "info",
  offboarding: "warn",
};

export function toneFor(status: unknown): Tone {
  const k = String(status ?? "")
    .trim()
    .toLowerCase();
  if (k in MAP) return MAP[k];
  if (k.includes("pending")) return "warn";
  if (k.includes("reject") || k.includes("fail") || k.includes("error")) return "bad";
  if (k.includes("approv") || k.includes("ok") || k.includes("done")) return "good";
  return "";
}

export function StatusPill({
  status,
  tone,
  children,
}: {
  status?: unknown;
  tone?: Tone;
  children?: React.ReactNode;
}) {
  const t = tone ?? toneFor(status);
  return <span className={`pill${t ? ` ${t}` : ""}`}>{children ?? humanize(status)}</span>;
}

/** Attendance short code badge (P/L/A/HD/…). */
export function AttCode({ code, label }: { code?: unknown; label?: unknown }) {
  const c = String(code ?? "").toUpperCase();
  if (!c && !label) return <span className="muted">—</span>;
  return (
    <span className="row" style={{ gap: 6, flexWrap: "nowrap" }}>
      <span className={`code c-${c}`}>{c || "·"}</span>
      {label ? <span className="small muted">{String(label)}</span> : null}
    </span>
  );
}
