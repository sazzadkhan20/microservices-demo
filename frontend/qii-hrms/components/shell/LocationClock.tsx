"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import styles from "./AppShell.module.css";

/** Live date/time in the logged-in employee's org location zone (DR-01 / NFR-16). */
export function LocationClock() {
  const { me, locationTimeZone } = useAuth();
  const tz = locationTimeZone || "Asia/Dhaka";
  const city = me.locationName?.trim() || shortZone(tz);

  // Avoid SSR/client Date mismatch — paint a stable shell, then start the live clock.
  const [now, setNow] = useState<Date | null>(null);

  useEffect(() => {
    const tick = () => setNow(new Date());
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, []);

  if (!now) {
    return (
      <time
        className={styles.clock}
        title={`Local time for ${city} (${tz.replace(/_/g, " ")})`}
        aria-label={`${city} local time`}
        suppressHydrationWarning
      >
        <span className={styles.clockFace}>
          <span className={styles.clockDigits}>
            <span>--</span>
            <span className={styles.clockColon} aria-hidden>
              :
            </span>
            <span>--</span>
          </span>
          <span className={styles.clockSeconds} aria-hidden>
            --
          </span>
        </span>
        <span className={styles.clockDivider} aria-hidden />
        <span className={styles.clockSide}>
          <span className={styles.clockPlace}>
            <span className={styles.clockLive} aria-hidden />
            {city}
          </span>
          <span className={styles.clockDate}>Local time</span>
        </span>
      </time>
    );
  }

  const weekday = now.toLocaleDateString("en-GB", { timeZone: tz, weekday: "short" });
  const dayMonth = now.toLocaleDateString("en-GB", {
    timeZone: tz,
    day: "numeric",
    month: "short",
  });
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: tz,
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "00";
  const hour = get("hour");
  const minute = get("minute");
  const second = get("second");
  const blinkOn = Number(second) % 2 === 0;

  return (
    <time
      className={styles.clock}
      dateTime={now.toISOString()}
      title={`Local time for ${city} (${tz.replace(/_/g, " ")})`}
      aria-label={`${city} local time ${hour}:${minute}, ${weekday} ${dayMonth}`}
      suppressHydrationWarning
    >
      <span className={styles.clockFace}>
        <span className={styles.clockDigits}>
          <span>{hour}</span>
          <span className={blinkOn ? styles.clockColon : styles.clockColonOff} aria-hidden>
            :
          </span>
          <span>{minute}</span>
        </span>
        <span className={styles.clockSeconds} aria-hidden>
          {second}
        </span>
      </span>
      <span className={styles.clockDivider} aria-hidden />
      <span className={styles.clockSide}>
        <span className={styles.clockPlace}>
          <span className={styles.clockLive} aria-hidden />
          {city}
        </span>
        <span className={styles.clockDate}>
          {weekday} {dayMonth}
        </span>
      </span>
    </time>
  );
}

function shortZone(tz: string): string {
  const part = tz.split("/").pop() || tz;
  return part.replace(/_/g, " ");
}
