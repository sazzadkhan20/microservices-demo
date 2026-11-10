/**
 * FR-ATT-02 — build check-in/out body: TZ, optional geo, device, note.
 * Refused / unavailable location never blocks; label becomes "Location not shared".
 * Time math uses employee location zone (DR-01), not browser zone.
 */

import { getAppTimeZone, zonedLocalToUtc } from "@/lib/format";

export type PunchBody = {
  locationLabel: string;
  note?: string;
  latitude?: number;
  longitude?: number;
  timeZone: string;
  deviceType: string;
};

function deviceType(): string {
  if (typeof navigator === "undefined") return "Desktop";
  const ua = navigator.userAgent || "";
  if (/Mobi|Android|iPhone|iPad/i.test(ua)) return "Mobile";
  return "Desktop";
}

/** Prefer employee location TZ from /me; browser TZ is metadata only. */
function punchTimeZone(): string {
  const loc = getAppTimeZone();
  if (loc) return loc;
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

function readGeo(timeoutMs = 4000): Promise<{ lat: number; lng: number } | null> {
  if (typeof navigator === "undefined" || !navigator.geolocation) {
    return Promise.resolve(null);
  }
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(null), timeoutMs);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        clearTimeout(timer);
        resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      () => {
        clearTimeout(timer);
        resolve(null);
      },
      { enableHighAccuracy: false, maximumAge: 60_000, timeout: timeoutMs },
    );
  });
}

/** Gather punch metadata. Never throws on geo refusal. */
export async function buildPunchBody(note?: string): Promise<PunchBody> {
  const geo = await readGeo();
  const body: PunchBody = {
    locationLabel: geo ? "Share my location" : "Location not shared",
    timeZone: punchTimeZone(),
    deviceType: deviceType(),
  };
  if (geo) {
    body.latitude = geo.lat;
    body.longitude = geo.lng;
  }
  const n = note?.trim();
  if (n) body.note = n;
  return body;
}

/**
 * Overnight-aware local times in employee location TZ → ISO instants (FR-HRS-01 / FR-ATT-07).
 * Falls back to browser local only when location TZ is unknown.
 */
export function localRangeToIso(
  date: string,
  checkInLocal: string,
  checkOutLocal: string,
  timeZone?: string | null,
) {
  const tz = timeZone?.trim() || getAppTimeZone();
  if (tz) {
    const cin = zonedLocalToUtc(date, checkInLocal, tz);
    let cout = zonedLocalToUtc(date, checkOutLocal, tz);
    if (!Number.isNaN(cin.getTime()) && !Number.isNaN(cout.getTime()) && cout <= cin) {
      cout = new Date(cout.getTime() + 24 * 60 * 60 * 1000);
    }
    return {
      requestedCheckIn: cin.toISOString(),
      requestedCheckOut: cout.toISOString(),
    };
  }
  const cin = new Date(`${date}T${checkInLocal}:00`);
  let cout = new Date(`${date}T${checkOutLocal}:00`);
  if (!Number.isNaN(cin.getTime()) && !Number.isNaN(cout.getTime()) && cout <= cin) {
    cout = new Date(cout.getTime() + 24 * 60 * 60 * 1000);
  }
  return {
    requestedCheckIn: cin.toISOString(),
    requestedCheckOut: cout.toISOString(),
  };
}

export function fmtHoursMinutes(mins: unknown): string {
  const n = Math.max(0, Math.round(Number(mins) || 0));
  return `${Math.floor(n / 60)}h ${n % 60}m`;
}
