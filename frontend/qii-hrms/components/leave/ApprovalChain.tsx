import type { LeaveRow } from "@/lib/schemas/leave";

/** Prototype `.chain`: one pill per approval step, coloured by decision. */
export function ApprovalChain({ steps }: { steps?: LeaveRow["approvals"] }) {
  if (!steps || steps.length === 0) return <span className="muted small">Auto-approved</span>;
  return (
    <div className="chain">
      {steps.map((s, i) => {
        const st = String(s.status ?? "").toUpperCase();
        const cls = st === "APPROVED" ? "ok" : st === "REJECTED" ? "no" : st === "PENDING" ? "cur" : "";
        return (
          <span key={i} style={{ display: "contents" }}>
            {i > 0 ? <span className="muted">→</span> : null}
            <span className={`st ${cls}`} title={s.comment ?? undefined}>
              {s.approverRole ?? `Step ${s.stepOrder ?? i + 1}`}
              {s.actorName ? ` · ${s.actorName}` : ""}
            </span>
          </span>
        );
      })}
    </div>
  );
}
