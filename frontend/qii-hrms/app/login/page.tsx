"use client";

import { Suspense, useMemo } from "react";
import { useSearchParams } from "next/navigation";
import styles from "./login.module.css";

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

function GoogleMark() {
  return (
    <svg className={styles.googleIcon} viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
      />
      <path
        fill="#FBBC05"
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18A10.96 10.96 0 0 0 1 12c0 1.77.42 3.45 1.18 4.93l3.66-2.84z"
      />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
      />
    </svg>
  );
}

function errorCopy(code: string | null, detail: string | null): string | null {
  if (!code) return null;
  switch (code) {
    case "no_employee":
      return detail || "This Google account has no employee record. Ask HR to add you first.";
    case "inactive":
      return detail || "This account is not active. Contact HR if you need access restored.";
    case "domain":
      return detail || "Sign-in is limited to company Google accounts.";
    case "google_denied":
      return detail || "Google sign-in was cancelled.";
    case "oidc_misconfigured":
      return "Sign-in is not configured yet. Ask an administrator to enable Google OIDC.";
    case "oidc_state":
      return "Sign-in session expired. Please try again.";
    case "forbidden":
      return detail || "You are not allowed to sign in with this Google account.";
    default:
      return detail || "Sign-in failed. Please try again.";
  }
}

function LoginInner() {
  const params = useSearchParams();
  const next = safeNext(params.get("next"));
  const error = useMemo(
    () => errorCopy(params.get("error"), params.get("detail")),
    [params],
  );
  const startHref = `/api/auth/oidc/start?next=${encodeURIComponent(next)}`;

  return (
    <div className={styles.page}>
      <div className={styles.atmosphere} aria-hidden="true" />
      <div className={styles.glow} aria-hidden="true" />

      <main className={styles.stage}>
        <section className={styles.hero}>
          <div className={styles.brandRow}>
            <div className={styles.mark}>Q</div>
            <div className={styles.brandText}>
              <p className={styles.brandName}>Qii HRMS</p>
              <p className={styles.brandTag}>People · Time · Payroll</p>
            </div>
          </div>

          <h1 className={styles.headline}>Sign in with your work Google account</h1>
          <p className={styles.support}>
            Secure single sign-on for Qii Creative. Use the Google account tied to your work email.
          </p>

          {error ? (
            <div className={styles.error} role="alert">
              {error}
            </div>
          ) : null}

          <a className={styles.googleBtn} href={startHref}>
            <GoogleMark />
            <span>Continue with Google</span>
          </a>

          <p className={styles.footnote}>
            Access is granted only after your employee record is linked. No role yet? You&apos;ll land on
            Access pending.
          </p>
        </section>
      </main>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<div className={styles.page}><div className={styles.loading}>Loading…</div></div>}>
      <LoginInner />
    </Suspense>
  );
}
