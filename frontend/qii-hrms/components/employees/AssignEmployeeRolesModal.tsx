"use client";

import { useCallback, useMemo, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useAuth } from "@/components/auth/AuthProvider";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { api, apiFetch } from "@/lib/api/client";
import { useRoles, type Role } from "@/lib/api/lookups";
import { employeeSchema, type Employee } from "@/lib/schemas/employee";

type Props = {
  open: boolean;
  employee: Employee;
  onClose: () => void;
};

/**
 * Prototype-style employee-wise role assignment.
 * Gates: roles:assign + each role's assignableBy / minHolders from tenant config.
 */
export function AssignEmployeeRolesModal({ open, employee, onClose }: Props) {
  const { can, me } = useAuth();
  const toast = useToast();
  const qc = useQueryClient();
  const roles = useRoles(open && can("roles", "assign"));
  const [selected, setSelected] = useState<string[] | null>(null);
  const [err, setErr] = useState("");

  const liveRoles = useQuery({
    queryKey: ["employee", String(employee.id), "roles-live"],
    queryFn: async ({ signal }) => {
      const e = await apiFetch(api.hrms(`employees/${employee.id}`), { schema: employeeSchema, signal });
      return e.roles ?? [];
    },
    enabled: open,
  });

  const current = selected ?? liveRoles.data ?? employee.roles ?? [];

  const actorRoleCodes = useMemo(
    () => new Set((me.roles ?? []).map((c) => String(c).toUpperCase())),
    [me.roles],
  );
  const actorHasWildcard = useMemo(
    () => (me.permissions ?? []).some((p) => p.trim().toLowerCase() === "*:*"),
    [me.permissions],
  );

  const canAssignRole = useCallback(
    (r: Role) => {
      const gate = (r.assignableBy ?? "").trim();
      if (!gate) return true;
      if (actorHasWildcard) return true;
      const allowed = gate
        .split(",")
        .map((s) => s.trim().toUpperCase())
        .filter(Boolean);
      if (allowed.length === 0) return true;
      return allowed.some((code) => actorRoleCodes.has(code));
    },
    [actorHasWildcard, actorRoleCodes],
  );

  const holderCounts = useMemo(() => {
    const m = new Map<string, number>();
    for (const r of roles.data ?? []) m.set(r.code.toUpperCase(), r.holderCount ?? 0);
    return m;
  }, [roles.data]);

  const save = useMutation({
    mutationFn: (roleCodes: string[]) =>
      apiFetch<{ roleCodes: string[] }>(api.hrms(`employees/${employee.id}/roles`), {
        method: "POST",
        body: { roleCodes },
      }),
    onSuccess: (res) => {
      void qc.invalidateQueries({ queryKey: ["employee", String(employee.id)] });
      void qc.invalidateQueries({ queryKey: ["employees"] });
      void qc.invalidateQueries({ queryKey: ["roles"] });
      void qc.invalidateQueries({ queryKey: ["lookup", "roles"] });
      toast.show(`Roles updated for ${employee.fullName}`);
      setSelected(null);
      setErr("");
      onClose();
      return res;
    },
    onError: (e: Error) => {
      setErr(e.message || "Could not save roles");
      toast.error(e);
    },
  });

  function toggle(code: string, on: boolean) {
    setSelected((prev) => {
      const base = prev ?? liveRoles.data ?? employee.roles ?? [];
      return on ? [...new Set([...base, code])] : base.filter((c) => c !== code);
    });
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr("");
    save.mutate(current);
  }

  if (!open) return null;

  return (
    <Modal
      open={open}
      onClose={() => {
        if (!save.isPending) {
          setSelected(null);
          setErr("");
          onClose();
        }
      }}
      title={`Roles · ${employee.fullName}`}
      subtitle="Changes take effect on the person’s next request (permissions follow the roles below)."
    >
      <form className="stack" onSubmit={onSubmit}>
        <div className="checks" style={{ flexDirection: "column" }}>
          {(roles.data ?? [])
            .filter((r) => r.active !== false)
            .filter((r) => canAssignRole(r) || current.includes(r.code))
            .map((r) => {
              const held = current.includes(r.code);
              const assignable = canAssignRole(r);
              const holders = holderCounts.get(r.code.toUpperCase()) ?? 0;
              const lockedMin = held && (r.minHolders ?? 0) > 0 && holders <= (r.minHolders ?? 0);
              const disabled = !assignable || lockedMin || save.isPending;
              return (
                <label key={r.code}>
                  <input
                    type="checkbox"
                    checked={held}
                    disabled={disabled}
                    onChange={(ev) => toggle(r.code, ev.target.checked)}
                  />
                  <b>{r.name || r.code}</b>{" "}
                  <span className="muted small">{r.description || r.code}</span>
                  {!assignable && held ? <span className="muted small"> · restricted</span> : null}
                  {lockedMin ? <span className="muted small"> · min holders</span> : null}
                </label>
              );
            })}
        </div>
        {current.length === 0 ? (
          <p className="note">No role selected — this person cannot sign in (access pending).</p>
        ) : null}
        {err ? <div className="notice bad">{err}</div> : null}
        <div className="modal-foot">
          <button
            type="button"
            className="btn"
            disabled={save.isPending}
            onClick={() => {
              setSelected(null);
              setErr("");
              onClose();
            }}
          >
            Cancel
          </button>
          <button type="submit" className="btn primary" disabled={save.isPending || liveRoles.isLoading}>
            {save.isPending ? "Saving…" : "Save roles"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
