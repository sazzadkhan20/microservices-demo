"use client";

import { useMemo, useState, type FormEvent } from "react";
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
const TYPES = ["PUBLIC", "COMPANY", "RELIGIOUS", "OPTIONAL"];

const holidaySchema = z
  .object({
    name: z.string().trim().min(2, "Name is required"),
    locationId: z.string().min(1, "Choose a location"),
    startDate: z.string().min(1, "Start date is required"),
    endDate: z.string(),
    holidayType: z.string().min(1),
    optionalFlag: z.boolean(),
  })
  .superRefine((v, ctx) => {
    if (v.endDate && v.startDate && v.endDate < v.startDate) {
      ctx.addIssue({ code: "custom", path: ["endDate"], message: "End date is before the start date" });
    }
  });

export default function HolidaysPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const canManage = can("holidays", "create") || can("holidays", "edit") || can("holidays", "delete");
  const thisYear = new Date().getFullYear();

  const [year, setYear] = useState(String(thisYear));
  const [locationId, setLocationId] = useState("");
  const blank = { name: "", locationId: "", startDate: "", endDate: "", holidayType: "PUBLIC", optionalFlag: false };
  const [editing, setEditing] = useState<Rec | "new" | null>(null);
  const [form, setForm] = useState(blank);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [err, setErr] = useState("");

  // Copy-year state
  const [copyOpen, setCopyOpen] = useState(false);
  const [copyForm, setCopyForm] = useState({ fromYear: String(thisYear), toYear: String(thisYear + 1), locationId: "" });
  const [copyErrors, setCopyErrors] = useState<Record<string, string>>({});
  const [copyErr, setCopyErr] = useState("");
  const [confirmDel, setConfirmDel] = useState(false);

  const locations = useLocations();
  const locName = useMemo(() => byId(locations.data, (l) => l.name), [locations.data]);

  function openFor(h: Rec | "new") {
    setErr("");
    setErrors({});
    setForm(
      h === "new"
        ? { ...blank, locationId: locations.data?.[0] ? String(locations.data[0].id) : "" }
        : {
            name: str(h.name, ""),
            locationId: h.locationId != null ? String(h.locationId) : "",
            startDate: isoDate(h.startDate),
            endDate: isoDate(h.endDate),
            holidayType: str(h.holidayType, "PUBLIC"),
            optionalFlag: Boolean(h.optionalFlag),
          },
    );
    setEditing(h);
  }

  const invalidate = () => void qc.invalidateQueries({ queryKey: ["holidays"] });

  const copyYear = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiFetch(api.hrms("holidays/copy-year"), { method: "POST", body }),
    onSuccess: () => {
      toast.show("Holidays copied successfully");
      setCopyOpen(false);
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

  return (
    <div className="stack">
      <PageHeader
        title="Holidays"
        subtitle="Days off per location. Leave requests skip these when counting working days."
        actions={
          canManage ? (
            <div className="row">
              <button type="button" className="btn" onClick={() => { setCopyErr(""); setCopyErrors({}); setCopyOpen(true); }}>
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
            <span>Year</span>
            <select value={year} onChange={(e) => setYear(e.target.value)}>
              <option value="">All years</option>
              {years.map((y) => (
                <option key={y}>{y}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Location</span>
            <select value={locationId} onChange={(e) => setLocationId(e.target.value)}>
              <option value="">All locations</option>
              {(locations.data ?? []).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </label>
        </div>
        <DataTable<Rec>
          queryKey="holidays"
          path={api.hrms("holidays")}
          itemSchema={rowSchema as never}
          query={{ year }}
          filters={{ locationId }}
          emptyText="No holidays for this selection"
          defaultSort="startDate,asc"
          rowKey={(r) => String(r.id)}
          onRowClick={canManage ? (r) => openFor(r) : undefined}
          columns={[
            { key: "name", header: "Holiday", sortable: true, render: (r) => <b>{str(r.name)}</b> },
            { key: "startDate", header: "From", sortable: true, render: (r) => fmtDate(r.startDate) },
            { key: "endDate", header: "To", render: (r) => fmtDate(r.endDate) },
            { key: "locationId", header: "Location", render: (r) => locName.get(String(r.locationId)) ?? `#${r.locationId}` },
            { key: "holidayType", header: "Type", render: (r) => <span className="pill info">{str(r.holidayType)}</span> },
            { key: "optionalFlag", header: "Optional", render: (r) => (r.optionalFlag ? <span className="pill warn">Optional</span> : "—") },
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
              <select value={form.locationId} onChange={(e) => setForm({ ...form, locationId: e.target.value })}>
                <option value="">Select…</option>
                {(locations.data ?? []).map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Type">
              <select value={form.holidayType} onChange={(e) => setForm({ ...form, holidayType: e.target.value })}>
                {[...new Set([form.holidayType, ...TYPES])].map((t) => (
                  <option key={t}>{t}</option>
                ))}
              </select>
            </FormField>
            <FormField label="From" required error={errors.startDate}>
              <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} />
            </FormField>
            <FormField label="To" error={errors.endDate} hint="Leave blank for a single day">
              <input type="date" min={form.startDate} value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} />
            </FormField>
            <div className="checks full">
              <label>
                <input type="checkbox" checked={form.optionalFlag} onChange={(e) => setForm({ ...form, optionalFlag: e.target.checked })} />
                Optional holiday (employees may choose to work)
              </label>
            </div>
          </div>
          {err ? <div className="notice bad">{err}</div> : null}
          <div className="modal-foot">
            {editing && editing !== "new" ? (
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
        This holiday will be removed for everyone it applies to.
      </ConfirmModal>

      <Modal open={copyOpen} onClose={() => setCopyOpen(false)} title="Copy holidays to another year">
        <form className="stack" onSubmit={submitCopy} noValidate>
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
            <FormField label="Location" hint="Leave blank to copy all locations">
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
