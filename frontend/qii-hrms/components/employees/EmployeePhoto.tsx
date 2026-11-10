"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api/client";

type Props = {
  photoFileId?: string | number | null;
  /** Local blob:/data: URL while uploading — not a server download path. */
  previewUrl?: string | null;
  fullName?: string | null;
  size?: number;
  className?: string;
};

function initials(name?: string | null) {
  const parts = (name || "?").trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function isLocalPreview(url?: string | null) {
  if (!url) return false;
  return url.startsWith("blob:") || url.startsWith("data:");
}

/**
 * Loads employee photo via cookie-auth BFF download (MinIO-backed file-service).
 * Falls back to initials while scanning / if download is locked.
 */
export function EmployeePhoto({ photoFileId, previewUrl, fullName, size = 44, className }: Props) {
  const [src, setSrc] = useState<string | null>(isLocalPreview(previewUrl) ? previewUrl! : null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (isLocalPreview(previewUrl)) {
      setSrc(previewUrl!);
      setFailed(false);
      return;
    }
    const id = photoFileId == null || String(photoFileId).trim() === "" ? null : String(photoFileId);
    if (!id) {
      setSrc(null);
      setFailed(false);
      return;
    }
    let cancelled = false;
    let objectUrl: string | null = null;
    setFailed(false);
    setSrc(null);
    (async () => {
      try {
        const res = await fetch(api.files(`${id}/download`), { credentials: "same-origin" });
        if (!res.ok) {
          if (!cancelled) setFailed(true);
          return;
        }
        const blob = await res.blob();
        objectUrl = URL.createObjectURL(blob);
        if (!cancelled) setSrc(objectUrl);
      } catch {
        if (!cancelled) setFailed(true);
      }
    })();
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [photoFileId, previewUrl]);

  if (src && !failed) {
    return (
      // eslint-disable-next-line @next/next/no-img-element
      <img
        src={src}
        alt={fullName || "Employee photo"}
        width={size}
        height={size}
        className={className}
        style={{
          width: size,
          height: size,
          borderRadius: "50%",
          objectFit: "cover",
          background: "var(--surface-2, #eee)",
          flexShrink: 0,
        }}
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <div
      className={className}
      aria-hidden
      style={{
        width: size,
        height: size,
        borderRadius: "50%",
        background: "var(--accent-soft)",
        color: "var(--accent)",
        display: "grid",
        placeItems: "center",
        fontWeight: 700,
        fontSize: Math.max(12, Math.round(size * 0.36)),
        flexShrink: 0,
      }}
    >
      {initials(fullName)}
    </div>
  );
}
