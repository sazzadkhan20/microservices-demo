"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { EmptyState } from "@/components/ui/EmptyState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorNote, Loading } from "@/components/ui/QueryState";
import { StatusPill } from "@/components/ui/StatusPill";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { humanize } from "@/lib/format";

/**
 * Notification events are config: this list is the catalogue shown in the UI; the on/off state
 * lives in the tenant `notifications` bag (GET/PUT /hrms/api/settings/notifications).
 */
const EVENTS: { key: string; name: string; who: string; def: boolean }[] = [
  { key: "leave_submitted", name: "Leave request submitted", who: "The next approver", def: true },
  { key: "leave_decided", name: "Leave approved, rejected or cancelled", who: "The requester; the manager when a later approver decides", def: true },
  { key: "approval_waiting", name: "Approval waiting 2 working days", who: "The approver, then admins", def: true },
  { key: "correction", name: "Attendance correction submitted or decided", who: "Manager, then the employee", def: true },
  { key: "missing_checkin", name: "Missing check-in by start time + 1 hour", who: "The employee", def: true },
  { key: "missing_checkout", name: "Missing check-out at the cut-off", who: "The employee", def: true },
  { key: "contract_end", name: "Probation, contract or internship ending in 14 days", who: "The manager and HR", def: true },
  { key: "doc_expiry", name: "Document expiring in 30 days", who: "HR", def: true },
  { key: "checklist_due", name: "Onboarding or offboarding item due", who: "The item owner", def: true },
  { key: "team_clash", name: "Team leave limit exceeded", who: "The manager, team lead and first approver", def: true },
  { key: "worklog_missing", name: "Work log missing for a worked day", who: "The employee, then the manager", def: true },
  { key: "monthly_report", name: "Monthly attendance report ready", who: "Every employee", def: true },
  { key: "payroll", name: "Payslip ready", who: "The employee", def: true },
  { key: "celebration", name: "Work anniversary and birthday", who: "Team feed (optional)", def: false },
];

const bagSchema = z.record(z.string(), z.unknown());
const settingsSchema = z.record(z.string(), z.unknown());

const TABS = [
  { id: "events", label: "Events" },
  { id: "tenant", label: "Tenant config" },
];

export default function NotificationSettingsPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState("events");
  return (
    <div className="stack">
      <PageHeader title="Notifications" subtitle="In-app notifications and email to the work address. Switch each event on or off." />
      {can("settings", "manage") ? <Tabs tabs={TABS} value={tab} onChange={setTab} /> : null}
      {tab === "events" || !can("settings", "manage") ? <Events /> : <TenantConfig />}
    </div>
  );
}

function Events() {
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
  const isOn = (key: string, def: boolean) => (key in draft ? draft[key] : typeof stored[key] === "boolean" ? (stored[key] as boolean) : def);
  const dirty = Object.keys(draft).length > 0;

  const save = useMutation({
    mutationFn: () => {
      // The endpoint replaces the whole bag, so send every known event plus any unknown keys already stored.
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

  if (bag.isLoading) return <Loading />;
  if (bag.error) return <ErrorNote error={bag.error} />;

  return (
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
          <button type="button" className="btn primary" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>
            {save.isPending ? "Saving…" : "Save changes"}
          </button>
          <button type="button" className="btn" disabled={!dirty || save.isPending} onClick={() => setDraft({})}>
            Discard
          </button>
          {dirty ? <span className="muted small">Unsaved changes</span> : null}
        </div>
      ) : (
        <div className="note small">You can view these settings; changing them needs the settings manage permission.</div>
      )}
      <div className="note small">
        Preferences are stored as tenant configuration. Whether an event is actually sent depends on the service that
        raises it reading these switches — the notification service itself only stores and lists messages.
      </div>
    </div>
  );
}

function TenantConfig() {
  const cfg = useQuery({
    queryKey: ["settings-tenant"],
    queryFn: ({ signal }) => apiFetch(api.hrms("settings"), { schema: settingsSchema, signal }),
  });
  if (cfg.isLoading) return <Loading />;
  if (cfg.error) return <ErrorNote error={cfg.error} />;
  const entries = Object.entries(cfg.data ?? {}).filter(([k]) => k !== "notifications");
  if (entries.length === 0) {
    return <EmptyState title="No tenant configuration">No settings have been stored for this tenant.</EmptyState>;
  }
  return (
    <div className="stack">
      <div className="tbl-wrap">
        <table>
          <thead>
            <tr>
              <th>Setting</th>
              <th>Value</th>
            </tr>
          </thead>
          <tbody>
            {entries.map(([k, v]) => (
              <tr key={k}>
                <td>
                  {humanize(k.replace(/([a-z])([A-Z])/g, "$1_$2"))}
                  <br />
                  <span className="mono small muted">{k}</span>
                </td>
                <td className="mono small">
                  {typeof v === "boolean" ? <StatusPill tone={v ? "good" : ""}>{v ? "On" : "Off"}</StatusPill> : typeof v === "object" ? JSON.stringify(v) : String(v)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="note small">Read-only view of the tenant settings bag (work-log rules, report day, polling interval, …).</div>
    </div>
  );
}
