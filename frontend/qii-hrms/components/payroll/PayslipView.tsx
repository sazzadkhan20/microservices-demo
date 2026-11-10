"use client";

import type { ReactNode } from "react";
import type { Rec } from "@/lib/api/lookups";
import { fmtDateTime, fmtMoney, fmtNumber, str } from "@/lib/format";
import { StatusPill } from "@/components/ui/StatusPill";

/** Months are labelled the way the SRS writes them ("September 2026"). */
export function periodLabel(period: unknown): string {
  const p = String(period ?? "");
  if (!/^\d{4}-\d{2}$/.test(p)) return p || "—";
  const [y, m] = p.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleString("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });
}

function Row({ label, value, strong }: { label: string; value: ReactNode; strong?: boolean }) {
  return (
    <tr>
      <td>{label}</td>
      <td className="n">{strong ? <b>{value}</b> : value}</td>
    </tr>
  );
}

/**
 * FR-PAY-15 payslip: base pay, per-day rate or hours × rate, absent days, days covered by leave,
 * unpaid days, deduction, adjustment (with note) and net pay, plus the row notes (FR-PAY-10).
 */
export function PayslipView({ slip, estimate }: { slip: Rec; estimate?: boolean }) {
  const cur = str(slip.currency, "") || undefined;
  const hourly = String(slip.payType ?? "").toUpperCase() === "HOURLY";
  const covers = Array.isArray(slip.covers) ? (slip.covers as Rec[]) : [];
  const notes = Array.isArray(slip.notes) ? (slip.notes as unknown[]) : [];
  const adjustment = Number(slip.adjustment ?? 0);

  return (
    <div className="stack payslip">
      <div className="row between" style={{ alignItems: "flex-start" }}>
        <div>
          <div className="label">Employee</div>
          <b>{str(slip.employeeName)}</b> <span className="muted mono">{str(slip.employeeCode, "")}</span>
        </div>
        <div style={{ textAlign: "right" }}>
          <div className="label">{estimate ? "Month-to-date estimate" : "Pay period"}</div>
          <b>{periodLabel(slip.period)}</b>
          <div style={{ marginTop: 4 }}>
            {estimate ? <span className="pill info">Estimate — not final</span> : <StatusPill status={slip.status} />}
          </div>
        </div>
      </div>

      {slip.exception ? (
        <div className="notice bad">This row needs attention before the month can be finalised.</div>
      ) : null}

      <div className="grid g3">
        <div className="stat">
          <span className="s">Base pay</span>
          <span className="v">{fmtMoney(slip.basePay, cur)}</span>
        </div>
        <div className="stat">
          <span className="s">Deduction</span>
          <span className="v">{fmtMoney(slip.deduction, cur)}</span>
        </div>
        <div className="stat">
          <span className="s">Net pay</span>
          <span className="v">
            <b>{fmtMoney(slip.netPay, cur)}</b>
          </span>
        </div>
      </div>

      <div className="tbl-wrap">
        <table>
          <tbody>
            <Row label="Pay type" value={hourly ? "Hourly" : "Monthly"} />
            {hourly ? (
              <>
                <Row label="Hours worked (closed shifts)" value={`${fmtNumber(slip.hoursWorked)} h`} />
                <Row label="Hourly rate" value={fmtMoney(slip.hourlyRate, cur)} />
                <Row
                  label="Hours × rate"
                  value={`${fmtNumber(slip.hoursWorked)} × ${fmtMoney(slip.hourlyRate, cur)} = ${fmtMoney(slip.basePay, cur)}`}
                />
              </>
            ) : (
              <>
                <Row label="Working days in month" value={fmtNumber(slip.workingDays)} />
                <Row label="Working days employed" value={fmtNumber(slip.daysEmployed)} />
                <Row label="Per-day rate" value={fmtMoney(slip.perDayRate, cur)} />
                <Row label="Base pay" value={fmtMoney(slip.basePay, cur)} />
                <Row label="Absent days" value={fmtNumber(slip.absentDays)} />
                <Row
                  label="Days covered by leave"
                  value={
                    covers.length > 0
                      ? covers.map((c) => `${fmtNumber(c.days)} ${str(c.category)}`).join(", ")
                      : fmtNumber(slip.coveredDays)
                  }
                />
                <Row label="Unpaid leave days" value={fmtNumber(slip.unpaidLeaveDays)} />
                <Row label="Deducted days" value={fmtNumber(slip.deductedDays)} />
              </>
            )}
            <Row label="Deduction" value={`− ${fmtMoney(slip.deduction, cur)}`} />
            <Row
              label={adjustment !== 0 ? `Adjustment — ${str(slip.adjustmentNote)}` : "Adjustment"}
              value={`${adjustment > 0 ? "+ " : adjustment < 0 ? "− " : ""}${fmtMoney(Math.abs(adjustment), cur)}`}
            />
            <Row label="Net pay" value={fmtMoney(slip.netPay, cur)} strong />
          </tbody>
        </table>
      </div>

      {notes.length > 0 ? (
        <div>
          <div className="label" style={{ marginBottom: 4 }}>
            Notes
          </div>
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {notes.map((n, i) => (
              <li key={i} className="small">
                {str(n)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <p className="muted small">
        Gross pay and attendance-based deductions only. Tax and statutory items are not part of this payslip.
        {slip.finalizedAt ? ` Finalised ${fmtDateTime(slip.finalizedAt)}.` : ""}
      </p>
    </div>
  );
}
