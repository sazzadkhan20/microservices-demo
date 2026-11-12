"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { useAuth } from "@/components/auth/AuthProvider";
import { DataTable } from "@/components/ui/DataTable";
import { FormField } from "@/components/ui/FormField";
import { ConfirmModal } from "@/components/ui/ConfirmModal";
import { Modal } from "@/components/ui/Modal";
import { PageHeader } from "@/components/ui/PageHeader";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { byId, useLocations, type Rec } from "@/lib/api/lookups";
import { fmtDate, isoDate, str } from "@/lib/format";

const copyYearSchema = z
  .object({
    fromYear: z.string().min(4, "Select a year"),
    toYear: z.string().min(4, "Select a year"),
    locationId: z.string(),
  })
  .refine((v) => v.fromYear !== v.toYear, { message: "From and to years must differ", path: ["toYear"] });

const rowSchema = z.object({ id: z.union([z.number(), z.string()]) }).passthrough();
/** FR-HOL-01 — Public or Company only; optional is a separate flag. */
const TYPES = ["PUBLIC", "COMPANY"] as const;

function normalizeHolidayType(raw: unknown): string {
  const t = String(raw ?? "PUBLIC").trim().toUpperCase();
  if (t === "COMPANY" || t === "PUBLIC") return t;
  // Legacy OPTIONAL / RELIGIOUS / NATIONAL → map into Public|Company
  if (t === "NATIONAL") return "PUBLIC";
  return "COMPANY";
}

function wasLegacyOptionalType(raw: unknown): boolean {
  return String(raw ?? "").trim().toUpperCase() === "OPTIONAL";
}

const holidaySchema = z
  .object({
    name: z.string().trim().min(2, "Name is required"),
    locationId: z.string().min(1, "Choose a location"),
    startDate: z.string().min(1, "Start date is required"),
    endDate: z.string(),
    holidayType: z.enum(["PUBLIC", "COMPANY"]),
    optionalFlag: z.boolean(),
  })
  .superRefine((v, ctx) => {
    if (v.endDate && v.startDate && v.endDate < v.startDate) {
      ctx.addIssue({ code: "custom", path: ["endDate"], message: "End date is before the start date" });
    }
  });

/** Location-local calendar day as YYYY-MM-DD (en-CA). */
function todayInTimeZone(timeZone?: string | null): string {
  try {
    return new Intl.DateTimeFormat("en-CA", {
      timeZone: timeZone?.trim() || "UTC",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(new Date());
  } catch {
    return new Date().toISOString().slice(0, 10);
  }
}

type HolidayPhase = "future" | "ongoing" | "past";

function holidayPhase(start: string, end: string, today: string): HolidayPhase {
  const s = start || "";
  const e = end || start || "";
  if (s && s > today) return "future";
  if (e && e < today) return "past";
  return "ongoing";
}

export default function HolidaysPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("holidays", "create") || can("holidays", "edit") || can("holidays", "delete");
  const thisYear = new Date().getFullYear();

  const [year, setYear] = useState(String(thisYear));
  const [locationId, setLocationId] = useState("");
  const blank = {
    name: "",
    locationId: "",
    startDate: "",
    endDate: "",
    holidayType: "PUBLIC" as (typeof TYPES)[number],
    optionalFlag: false,
  };
  const [editing, setEditing] = useState<Rec | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  const [copyOpen, setCopyOpen] = useState(false);
  const [copyForm, setCopyForm] = useState({
    fromYear: String(thisYear),
    toYear: String(thisYear + 1),
    locationId: "",
  });
  const [copyErrors, setCopyErrors] = useState<Record<string, string>>({});
  const [copyErr, setCopyErr] = useState("");
  const [confirmDel, setConfirmDel] = useState(false);

  const locations = useLocations();
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);

  // Default once to the first location so admins land on a single location calendar.
  const defaultedLoc = useRef(false);
  useEffect(() => {
    if (defaultedLoc.current || !(locations.data ?? []).length) return;
    defaultedLoc.current = true;
    setLocationId(String(locations.data![0].id));
  }, [locations.data]);

  function openFor(h: Rec | "new") {
    setErr("");
    setErrors({});
    setForm(
      h === "new"
        ? {
            ...blank,
            locationId: locationId || (locations.data?.[0] ? String(locations.data[0].id) : ""),
          }
        : {
            name: str(h.name, ""),
            locationId: h.locationId != null ? String(h.locationId) : "",
            startDate: isoDate(h.startDate),
            endDate: isoDate(h.endDate),
            holidayType: normalizeHolidayType(h.holidayType) as (typeof TYPES)[number],
            optionalFlag: Boolean(h.optionalFlag) || wasLegacyOptionalType(h.holidayType),
          },
    );
    setEditing(h);
  }

  const invalidate = () => void qc.invalidateQueries({ queryKey: ["holidays"] });

  const copyYear = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiFetch<{ count?: number; skipped?: number }>(api.hrms("holidays/copy-year"), { method: "POST", body }),
    onSuccess: (res) => {
      const n = Number(res?.count ?? 0);
      const skipped = Number(res?.skipped ?? 0);
      toast.show(
        skipped > 0
          ? `Copied ${n} holiday${n === 1 ? "" : "s"} (${skipped} already existed — skipped)`
          : `Copied ${n} holiday${n === 1 ? "" : "s"}`,
      );
      setCopyOpen(false);
      if (copyForm.toYear) setYear(copyForm.toYear);
      invalidate();
    },
    onError: (e: Error) => setCopyErr(e.message),
  });

  function submitCopy(e: FormEvent) {
    e.preventDefault();
    const parsed = copyYearSchema.safeParse(copyForm);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setCopyErrors(next);
    }
    setCopyErrors({});
    setCopyErr("");
    const body: Record<string, unknown> = {
      fromYear: Number(parsed.data.fromYear),
      toYear: Number(parsed.data.toYear),
    };
    if (parsed.data.locationId) body.locationId = Number(parsed.data.locationId);
    copyYear.mutate(body);
  }

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      editing && editing !== "new"
        ? apiFetch(api.hrms(`holidays/${editing.id}`), { method: "PUT", body })
        : apiFetch(api.hrms("holidays"), { method: "POST", body }),
    onSuccess: () => {
      toast.show(editing === "new" ? "Holiday added" : "Holiday saved");
      setEditing(null);
      invalidate();
    },
    onError: (e: Error) => setErr(e.message),
  });
  const remove = useMutation({
    mutationFn: (id: string | number) => apiFetch(api.hrms(`holidays/${id}`), { method: "DELETE" }),
    onSuccess: () => {
      toast.show("Holiday deleted");
      setConfirmDel(false);
      setEditing(null);
      invalidate();
    },
    onError: (e: Error) => toast.error(e),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    const parsed = holidaySchema.safeParse(form);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const i of parsed.error.issues) next[String(i.path[0])] ||= i.message;
      return setErrors(next);
    }
    const nextErrors: Record<string, string> = {};
    if (editing && editing !== "new") {
      const locTz =
        (locations.data ?? []).find((l) => String(l.id) === parsed.data.locationId)?.timeZone ?? null;
      const today = todayInTimeZone(locTz);
      const origStart = isoDate(editing.startDate);
      const origEnd = isoDate(editing.endDate) || origStart;
      const phase = holidayPhase(origStart, origEnd, today);
      const end = parsed.data.endDate || parsed.data.startDate;
      if (phase === "past") {
        if (parsed.data.startDate !== origStart || end !== origEnd) {
          nextErrors.endDate = "Past holidays cannot change dates";
        }
      } else if (phase === "ongoing") {
        if (parsed.data.startDate !== origStart) {
          nextErrors.startDate = "Start date of an ongoing holiday cannot change";
        }
        if (end < today) {
          nextErrors.endDate = `Ongoing holiday can only be shortened to today (${today}) or later`;
        }
      }
    }
    if (Object.keys(nextErrors).length) {
      return setErrors(nextErrors);
    }
    setErrors({});
    setErr("");
    save.mutate({
      name: parsed.data.name,
      locationId: Number(parsed.data.locationId),
      startDate: parsed.data.startDate,
      endDate: parsed.data.endDate || parsed.data.startDate,
      holidayType: parsed.data.holidayType,
      optionalFlag: parsed.data.optionalFlag,
    });
  }

  const years = [thisYear - 1, thisYear, thisYear + 1, thisYear + 2].map(String);
  const activeLocName = locationId ? locName.get(locationId) : null;

  const editLocTz =
    editing && editing !== "new"
      ? (locations.data ?? []).find((l) => String(l.id) === form.locationId)?.timeZone
      : (locations.data ?? []).find((l) => String(l.id) === form.locationId)?.timeZone;
  const editToday = todayInTimeZone(editLocTz);
  const editPhase =
    editing && editing !== "new"
      ? holidayPhase(isoDate(editing.startDate), isoDate(editing.endDate) || isoDate(editing.startDate), editToday)
      : null;
  const canDeleteEditing =
    !!editing &&
    editing !== "new" &&
    can("holidays", "delete") &&
    editPhase === "future";
  const datesLocked = editPhase === "past";
  const startLocked = editPhase === "ongoing" || editPhase === "past";
  const endMin = editPhase === "ongoing" ? editToday : form.startDate || undefined;

  return (
    <div className="stack">
      <PageHeader
        title="Holidays"
        subtitle="One calendar per location. Non-optional holidays are excluded from leave day counts and expected hours, and are never marked Absent."
        actions={
          canManage ? (
            <div className="row">
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setCopyErr("");
                  setCopyErrors({});
                  setCopyForm({
                    fromYear: year || String(thisYear),
                    toYear: String(Number(year || thisYear) + 1),
                    locationId: locationId || "",
                  });
                  setCopyOpen(true);
                }}
              >
                Copy year
              </button>
              <button type="button" className="btn primary" onClick={() => openFor("new")}>
                + Add holiday
              </button>
            </div>
          ) : null
        }
      />
      <div className="card stack">
        <div className="filters">
          <label className="field">
            <span>Location calendar</span>
            <select value={locationId} onChange={(e) => setLocationId(e.target.value)}>
              <option value="">All locations</option>
              {(locations.data ?? []).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Year</span>
            <select value={year} onChange={(e) => setYear(e.target.value)}>
              <option value="">All years</option>
              {years.map((y) => (
                <option key={y}>{y}</option>
              ))}
            </select>
          </label>
        </div>
        {activeLocName ? (
          <p className="small muted" style={{ margin: 0 }}>
            Showing <b>{activeLocName}</b>
            {year ? ` · ${year}` : ""}. Optional holidays stay on the calendar but still count as working days.
          </p>
        ) : null}
        <DataTable<Rec>
          queryKey="holidays"
          path={api.hrms("holidays")}
          itemSchema={rowSchema as never}
          query={{ year }}
          filters={{ locationId }}
          emptyText="No holidays for this location / year"
          defaultSort="startDate,asc"
          rowKey={(r) => String(r.id)}
          onRowClick={canManage ? (r) => openFor(r) : undefined}
          columns={[
            { key: "name", header: "Holiday", sortable: true, render: (r) => <b>{str(r.name)}</b> },
            {
              key: "startDate",
              header: "From",
              sortable: true,
              render: (r) => fmtDate(r.startDate),
            },
            {
              key: "endDate",
              header: "To",
              render: (r) => {
                const s = isoDate(r.startDate);
                const e = isoDate(r.endDate);
                return e && e !== s ? fmtDate(r.endDate) : "—";
              },
            },
            {
              key: "locationId",
              header: "Location",
              render: (r) => locName.get(String(r.locationId)) ?? `#${r.locationId}`,
            },
            {
              key: "holidayType",
              header: "Type",
              render: (r) => <span className="pill info">{normalizeHolidayType(r.holidayType)}</span>,
            },
            {
              key: "optionalFlag",
              header: "Optional",
              render: (r) =>
                r.optionalFlag || wasLegacyOptionalType(r.holidayType) ? (
                  <span className="pill warn">Optional</span>
                ) : (
                  "Required"
                ),
            },
          ]}
        />
      </div>

      <Modal open={!!editing} onClose={() => setEditing(null)} title={editing === "new" ? "Add holiday" : "Edit holiday"}>
        <form className="stack" onSubmit={submit} noValidate>
          <div className="form-grid">
            <FormField label="Name" required error={errors.name} full>
              <input type="text" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </FormField>
            <FormField label="Location" required error={errors.locationId}>
              <select
                value={form.locationId}
                disabled={startLocked}
                onChange={(e) => setForm({ ...form, locationId: e.target.value })}
              >
                <option value="">Select…</option>
                {(locations.data ?? []).map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Type" hint="Public or Company">
              <select
                value={form.holidayType}
                onChange={(e) => setForm({ ...form, holidayType: e.target.value as (typeof TYPES)[number] })}
              >
                {TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t === "PUBLIC" ? "Public" : "Company"}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField
              label="From"
              required
              error={errors.startDate}
              hint={startLocked ? (editPhase === "past" ? "Past holiday — dates locked" : "Ongoing — start is locked") : undefined}
            >
              <input
                type="date"
                value={form.startDate}
                disabled={startLocked}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    startDate: e.target.value,
                    endDate: !f.endDate || f.endDate < e.target.value ? e.target.value : f.endDate,
                  }))
                }
              />
            </FormField>
            <FormField
              label="To"
              error={errors.endDate}
              hint={
                editPhase === "ongoing"
                  ? `Ongoing — end must be ${editToday} or later (cannot remove today or past days)`
                  : datesLocked
                    ? "Past holiday — dates locked"
                    : "Same as From for a single day"
              }
            >
              <input
                type="date"
                min={endMin || form.startDate || undefined}
                value={form.endDate}
                disabled={datesLocked}
                onChange={(e) => setForm({ ...form, endDate: e.target.value })}
              />
            </FormField>
            <div className="checks full">
              <label>
                <input
                  type="checkbox"
                  checked={form.optionalFlag}
                  onChange={(e) => setForm({ ...form, optionalFlag: e.target.checked })}
                />
                Optional — employees may work; day still counts for leave and expected hours
              </label>
            </div>
          </div>
          {editPhase === "ongoing" ? (
            <p className="small muted" style={{ margin: 0 }}>
              Shortening an ongoing holiday notifies employees at this location only. Delete is not allowed
              once the holiday has started.
            </p>
          ) : null}
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            {canDeleteEditing ? (
              <button
                type="button"
                className="btn danger"
                style={{ marginRight: "auto" }}
                disabled={remove.isPending}
                onClick={() => setConfirmDel(true)}
              >
                Delete
              </button>
            ) : null}
            <button type="button" className="btn" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={confirmDel && !!editing && editing !== "new"}
        title="Delete holiday?"
        confirmLabel="Delete"
        danger
        busy={remove.isPending}
        onCancel={() => setConfirmDel(false)}
        onConfirm={() => editing && editing !== "new" && remove.mutate(editing.id as number)}
      >
        This day will count as a normal working day again for leave and attendance.
      </ConfirmModal>

      <Modal open={copyOpen} onClose={() => setCopyOpen(false)} title="Copy calendar to another year">
        <form className="stack" onSubmit={submitCopy} noValidate>
          <p className="small muted" style={{ margin: 0 }}>
            Copies the selected location calendar into the next year for editing. Holidays that already exist
            there (same name and dates) are skipped.
          </p>
          <div className="form-grid">
            <FormField label="From year" required error={copyErrors.fromYear}>
              <select value={copyForm.fromYear} onChange={(e) => setCopyForm({ ...copyForm, fromYear: e.target.value })}>
                {years.map((y) => (
                  <option key={y}>{y}</option>
                ))}
              </select>
            </FormField>
            <FormField label="To year" required error={copyErrors.toYear}>
              <select value={copyForm.toYear} onChange={(e) => setCopyForm({ ...copyForm, toYear: e.target.value })}>
                {years.map((y) => (
                  <option key={y}>{y}</option>
                ))}
              </select>
            </FormField>
            <FormField label="Location" hint="Recommended — one calendar per location">
              <select value={copyForm.locationId} onChange={(e) => setCopyForm({ ...copyForm, locationId: e.target.value })}>
                <option value="">All locations</option>
                {(locations.data ?? []).map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </FormField>
          </div>
          {copyErr ? <div className="notice bad">{copyErr}</div> : null}
          <div className="modal-foot">
            <button type="button" className="btn" onClick={() => setCopyOpen(false)}>
              Cancel
            </button>
            <button type="submit" className="btn primary" disabled={copyYear.isPending}>
              {copyYear.isPending ? "Copying…" : "Copy holidays"}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
