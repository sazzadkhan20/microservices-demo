"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { apiFetch } from "@/lib/api/client";
import { accountListSchema, type Account } from "@/lib/schemas/account";
import styles from "./login.module.css";

/** Same-origin relative path only. Rejects //host, encoded slashes, and backslashes. */
function safeNext(raw: string | null): string {
  if (!raw) return "/";
  const trimmed = raw.trim();
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) return "/";
  if (trimmed.includes("\\") || trimmed.includes("://") || trimmed.includes("@")) return "/";
  let decoded = trimmed;
  try {
    decoded = decodeURIComponent(trimmed);
  } catch {
    return "/";
  }
  if (!decoded.startsWith("/") || decoded.startsWith("//") || decoded.startsWith("/\\")) return "/";
  if (decoded.includes("\\") || decoded.includes("://") || decoded.includes("@")) return "/";
  return decoded;
}

function LoginInner() {
  const router = useRouter();
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const [error, setError] = useState("");
  const [pendingId, setPendingId] = useState<string | null>(null);

  const accounts = useQuery({
    queryKey: ["accounts"],
    queryFn: ({ signal }) => apiFetch("/api/auth/accounts", { schema: accountListSchema, signal }),
  });

  async function pick(a: Account) {
    setError("");
    setPendingId(a.employeeId);
    try {
      await apiFetch("/api/auth/login", { method: "POST", body: { employeeId: a.employeeId } });
      router.replace(next);
      router.refresh();
    } catch (e) {
      setError((e as Error).message || "Login failed");
      setPendingId(null);
    }
  }

  return (
    <div className={styles.signin}>
      <div className={`card stack ${styles.box}`}>
        <div className="row">
          <div className={styles.mark}>Q</div>
          <div>
            <h1>Sign in to Qii HRMS</h1>
            <p className="muted small">Pick an account. Session uses an httpOnly cookie via the BFF.</p>
          </div>
        </div>
        {accounts.isLoading ? <p className="muted">Loading accounts…</p> : null}
        {accounts.error ? <div className="notice bad">{(accounts.error as Error).message}</div> : null}
        {error ? <div className="notice bad">{error}</div> : null}
        <div className={styles.list}>
          {(accounts.data || []).map((a) => (
            <button
              key={a.employeeId}
              type="button"
              className={styles.acct}
              disabled={pendingId !== null}
              onClick={() => void pick(a)}
            >
              <span>
                <b>{a.name}</b>
                <br />
                <span className="muted small">{a.email || a.department || a.employeeId}</span>
              </span>
              <span className="row">
                {a.role ? <span className="pill acc">{a.role}</span> : null}
                {pendingId === a.employeeId ? <span className="muted small">Signing in…</span> : null}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className="empty">Loading…</div>}>
      <LoginInner />
    </Suspense>
  );
}
