"use client";

/** Guide §8 scan states from file-service. */
export type ScanStatus =
  | "QUARANTINED"
  | "SCANNING"
  | "CLEAN"
  | "RELEASED"
  | "REJECTED"
  | "INFECTED"
  | "FAILED"
  | "UNKNOWN"
  | string
  | null
  | undefined;

export function normalizeScanStatus(status: ScanStatus): string {
  return String(status ?? "UNKNOWN").trim().toUpperCase();
}

export function isScanPending(status: ScanStatus): boolean {
  const s = normalizeScanStatus(status);
  return s === "QUARANTINED" || s === "SCANNING" || s === "";
}

export function isScanDownloadable(status: ScanStatus, downloadable?: boolean | null): boolean {
  if (downloadable === true) return true;
  if (downloadable === false) return false;
  const s = normalizeScanStatus(status);
  return s === "RELEASED" || s === "CLEAN";
}

export function isScanRejected(status: ScanStatus): boolean {
  const s = normalizeScanStatus(status);
  return s === "REJECTED" || s === "INFECTED" || s === "FAILED";
}

type Props = {
  status?: ScanStatus;
  detail?: string | null;
  /** Compact pill for tables; default shows animated scanner when pending. */
  compact?: boolean;
};

/**
 * Animated scan badge — spinner while quarantined/scanning; ready / blocked when terminal.
 */
export function ScanStatusBadge({ status, detail, compact }: Props) {
  const s = normalizeScanStatus(status);
  const pending = isScanPending(s);
  const rejected = isScanRejected(s);
  const ready = s === "RELEASED" || s === "CLEAN";

  const label = pending
    ? s === "SCANNING"
      ? "Scanning…"
      : "In quarantine…"
    : ready
      ? "Ready"
      : rejected
        ? s === "INFECTED"
          ? "Infected"
          : s === "FAILED"
            ? "Scan failed"
            : "Rejected"
        : s;

  const tone = pending ? "warn" : ready ? "good" : rejected ? "bad" : "info";
  const title = detail?.trim() ? detail : undefined;

  return (
    <span
      className={`pill ${tone} scan-pill${pending ? " scan-pill--active" : ""}`}
      title={title}
      aria-live={pending ? "polite" : undefined}
    >
      {pending ? <span className="scan-spinner" aria-hidden /> : null}
      {!compact && pending ? <span className="scan-pulse" aria-hidden /> : null}
      <span>{label}</span>
    </span>
  );
}

type PanelProps = {
  phase: "uploading" | "scanning" | "ready" | "rejected";
  fileName?: string | null;
  status?: ScanStatus;
  detail?: string | null;
};

/** Full-width panel used inside the attach-document modal during upload/scan. */
export function DocumentScanPanel({ phase, fileName, status, detail }: PanelProps) {
  const pending = phase === "uploading" || phase === "scanning";
  const rejected = phase === "rejected" || isScanRejected(status);
  const heading =
    phase === "uploading"
      ? "Uploading document…"
      : phase === "scanning"
        ? "Scanning for malware…"
        : rejected
          ? "Scan rejected this file"
          : "Scan complete — ready to download";

  return (
    <div
      className={`scan-panel${pending ? " scan-panel--active" : ""}${rejected ? " scan-panel--bad" : ""}${
        phase === "ready" ? " scan-panel--good" : ""
      }`}
      role="status"
      aria-live="polite"
    >
      <div className={`scan-radar${pending ? " scan-radar--on" : ""}`} aria-hidden>
        <span className="scan-radar__disc" />
        <span className="scan-radar__beam" />
        <span className="scan-radar__core">
          {pending ? <span className="scan-spinner scan-spinner--lg" /> : null}
          {!pending && !rejected ? <span className="scan-check">✓</span> : null}
          {rejected ? <span className="scan-x">✕</span> : null}
        </span>
      </div>
      <div className="scan-panel__copy">
        <strong>{heading}</strong>
        {fileName ? <p className="muted small">{fileName}</p> : null}
        {phase === "uploading" ? (
          <p className="muted small">Sending bytes to private object storage…</p>
        ) : null}
        {phase === "scanning" ? (
          <p className="muted small">
            Quarantined until the async content scan releases it. Download stays locked (guide §8).
          </p>
        ) : null}
        {detail ? <p className="small">{detail}</p> : null}
        {pending ? (
          <div className="scan-steps" aria-hidden>
            <span className={phase === "uploading" ? "on" : "done"}>1. Upload</span>
            <span className={phase === "scanning" ? "on" : phase === "uploading" ? "" : "done"}>2. Scan</span>
            <span className={phase === "ready" ? "on" : ""}>3. Release</span>
          </div>
        ) : null}
        {pending ? <div className="scan-bar" aria-hidden /> : null}
        {phase === "scanning" ? (
          <div style={{ marginTop: 10 }}>
            <ScanStatusBadge status={status || "SCANNING"} detail={detail} />
          </div>
        ) : null}
      </div>
    </div>
  );
}
