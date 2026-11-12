"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote, Loading } from "@/components/ui/QueryState";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";

/**
 * Notification events catalogue; on/off state lives in the tenant notifications bag
 * (GET/PUT /hrms/api/notification-settings).
 */
const EVENTS: { key: string; name: string; who: string; def: boolean }[] = [
  { key: "LEAVE_SUBMITTED", name: "Leave request submitted or moved to the next step", who: "The next approver", def: true },
  { key: "LEAVE_DECISION", name: "Leave approved, rejected or cancelled", who: "The requester, and the manager when a later approver decides", def: true },
  { key: "LEAVE_IDLE", name: "Approval waiting 2 working days", who: "The approver, then admins", def: true },
  { key: "TEAM_CLASH", name: "Team leave limit exceeded", who: "Manager, team lead and first approver", def: true },
  { key: "ATT_CORRECTION", name: "Attendance correction submitted or decided", who: "Manager, then the employee", def: true },
  { key: "NO_CHECKIN", name: "No check-in by start time + 1 hour", who: "That employee", def: true },
  { key: "MISSING_CHECKOUT", name: "Missing check-out at the cut-off", who: "That employee", def: true },
  { key: "MISSING_WORKLOG", name: "Worked day with no work log", who: "That employee, then the manager", def: true },
  { key: "PROBATION_END", name: "Probation, contract or internship ending in 14 days", who: "Manager and HR", def: true },
  { key: "DOC_EXPIRY", name: "Document expiring in 30 days", who: "HR", def: true },
  { key: "CHECKLIST_DUE", name: "Checklist item due in 2 days or overdue", who: "The item owner", def: true },
  { key: "PAYSLIP_READY", name: "Payslip ready", who: "That employee (must)", def: true },
  {
    key: "holiday_changed",
    name: "Holiday dates shortened (ongoing)",
    who: "Employees at that holiday’s location only",
    def: true,
  },
];

const bagSchema = z.record(z.string(), z.unknown());

export default function NotificationSettingsPage() {
  const { can } = useAuth();
  const canEdit = can("settings", "manage");
  const qc = useQueryClient();
  const toast = useToast();
  const bag = useQuery({
    queryKey: ["settings-notifications"],
    queryFn: ({ signal }) => apiFetch(api.hrms("notification-settings"), { schema: bagSchema, signal }),
  });
  const [draft, setDraft] = useState<Record<string, boolean>>({});

  const stored = bag.data ?? {};
  const isOn = (key: string, def: boolean) => {
    if (key in draft) return draft[key];
    if (typeof stored[key] === "boolean") return stored[key] as boolean;
    if (key === "PAYSLIP_READY" && typeof stored.payroll === "boolean") return stored.payroll as boolean;
    return def;
  };
  const dirty = Object.keys(draft).length > 0;

  const save = useMutation({
    mutationFn: () => {
      const next: Record<string, unknown> = { ...stored };
      for (const e of EVENTS) next[e.key] = isOn(e.key, e.def);
      return apiFetch(api.hrms("notification-settings"), { method: "PUT", body: next });
    },
    onSuccess: () => {
      toast.show("Notification settings saved");
      setDraft({});
      void qc.invalidateQueries({ queryKey: ["settings-notifications"] });
    },
    onError: (e) => toast.error(e),
  });

  return (
    <div className="stack">
      <PageHeader
        title="Notifications"
        subtitle="In-app notifications and email to the work address. Switch each event on or off."
      />
      {bag.isLoading ? <Loading /> : null}
      <ErrorNote error={bag.error} />
      {!bag.isLoading && !bag.error ? (
        <div className="stack">
          <div className="tbl-wrap">
            <table>
              <thead>
                <tr>
                  <th>Event</th>
                  <th>Who is told</th>
                  <th className="n">On</th>
                </tr>
              </thead>
              <tbody>
                {EVENTS.map((e) => (
                  <tr key={e.key}>
                    <td>{e.name}</td>
                    <td className="muted">{e.who}</td>
                    <td className="n">
                      <input
                        type="checkbox"
                        id={`ns-${e.key}`}
                        aria-label={e.name}
                        checked={isOn(e.key, e.def)}
                        disabled={!canEdit}
                        onChange={(ev) => setDraft({ ...draft, [e.key]: ev.target.checked })}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {canEdit ? (
            <div className="row">
              <button
                type="button"
                className="btn primary"
                disabled={!dirty || save.isPending}
                onClick={() => save.mutate()}
              >
                {save.isPending ? "Saving…" : "Save changes"}
              </button>
              <button type="button" className="btn" disabled={!dirty || save.isPending} onClick={() => setDraft({})}>
                Discard
              </button>
              {dirty ? <span className="muted small">Unsaved changes</span> : null}
            </div>
          ) : (
            <div className="note small">
              You can view these settings; changing them needs the settings manage permission.
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
