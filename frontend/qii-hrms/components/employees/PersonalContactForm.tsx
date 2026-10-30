"use client";

import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { FormField } from "@/components/ui/FormField";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { employeeSchema, type Employee } from "@/lib/schemas/employee";

const schema = z.object({
  phone: z.string(),
  personalEmail: z
    .string()
    .trim()
    .refine((v) => v === "" || z.string().email().safeParse(v).success, "Enter a valid email"),
  address: z.string(),
  emergencyName: z.string(),
  emergencyRelationship: z.string(),
  emergencyPhone: z.string(),
});

type Values = z.infer<typeof schema>;

function fromEmp(e?: Employee | null): Values {
  const cf = (e as Employee & { customFields?: Record<string, unknown> } | null | undefined)?.customFields;
  const pick = (k: string, fallback?: unknown) => {
    if (fallback != null && String(fallback).trim()) return String(fallback);
    if (cf?.[k] != null) return String(cf[k]);
    const direct = e ? (e as Record<string, unknown>)[k] : undefined;
    return direct == null ? "" : String(direct);
  };
  return {
    phone: pick("phone", e?.phone),
    personalEmail: pick("personalEmail", e?.personalEmail),
    address: pick("address"),
    emergencyName: pick("emergencyName"),
    emergencyRelationship: pick("emergencyRelationship"),
    emergencyPhone: pick("emergencyPhone"),
  };
}

type Props = {
  employee?: Employee | null;
  onSaved?: (e: Employee) => void;
  onCancel?: () => void;
};

/** FR-EMP-06 — self-edit allow-list via PATCH /me/personal. */
export function PersonalContactForm({ employee, onSaved, onCancel }: Props) {
  const qc = useQueryClient();
  const toast = useToast();
  const [v, setV] = useState<Values>(() => fromEmp(employee));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState("");

  const save = useMutation({
    mutationFn: async (body: Values) =>
      apiFetch(api.hrms("me/personal"), {
        method: "PATCH",
        body: {
          phone: body.phone.trim() || null,
          personalEmail: body.personalEmail.trim() || null,
          address: body.address.trim() || null,
          emergencyName: body.emergencyName.trim() || null,
          emergencyRelationship: body.emergencyRelationship.trim() || null,
          emergencyPhone: body.emergencyPhone.trim() || null,
        },
        schema: employeeSchema,
      }),
    onSuccess: (saved) => {
      void qc.invalidateQueries({ queryKey: ["employee", String(saved.id)] });
      void qc.invalidateQueries({ queryKey: ["me"] });
      toast.show("Contact details updated");
      onSaved?.(saved);
    },
    onError: (e: Error) => setFormError(e.message || "Could not save"),
  });

  function submit(ev: FormEvent) {
    ev.preventDefault();
    setFormError("");
    const parsed = schema.safeParse(v);
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const issue of parsed.error.issues) {
        const key = String(issue.path[0] ?? "form");
        if (!next[key]) next[key] = issue.message;
      }
      setErrors(next);
      return;
    }
    setErrors({});
    save.mutate(parsed.data);
  }

  function set<K extends keyof Values>(k: K, val: Values[K]) {
    setV((cur) => ({ ...cur, [k]: val }));
    if (errors[k]) setErrors((e) => ({ ...e, [k]: "" }));
  }

  return (
    <form className="stack" onSubmit={submit} noValidate>
      <p className="muted small">You can update your phone, personal email, address and emergency contact.</p>
      <div className="form-grid">
        <FormField label="Phone" error={errors.phone}>
          <input type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} />
        </FormField>
        <FormField label="Personal email" error={errors.personalEmail}>
          <input type="email" value={v.personalEmail} onChange={(e) => set("personalEmail", e.target.value)} />
        </FormField>
        <FormField label="Address" error={errors.address} full>
          <input type="text" value={v.address} onChange={(e) => set("address", e.target.value)} />
        </FormField>
        <FormField label="Emergency contact" error={errors.emergencyName}>
          <input type="text" value={v.emergencyName} onChange={(e) => set("emergencyName", e.target.value)} />
        </FormField>
        <FormField label="Relationship" error={errors.emergencyRelationship}>
          <input
            type="text"
            value={v.emergencyRelationship}
            onChange={(e) => set("emergencyRelationship", e.target.value)}
          />
        </FormField>
        <FormField label="Emergency phone" error={errors.emergencyPhone}>
          <input type="tel" value={v.emergencyPhone} onChange={(e) => set("emergencyPhone", e.target.value)} />
        </FormField>
      </div>
      {formError ? <p className="err" role="alert">{formError}</p> : null}
      <div className="row">
        <button type="submit" className="btn primary" disabled={save.isPending}>
          {save.isPending ? "Saving…" : "Save contact"}
        </button>
        {onCancel ? (
          <button type="button" className="btn" onClick={onCancel} disabled={save.isPending}>
            Cancel
          </button>
        ) : null}
      </div>
    </form>
  );
}
