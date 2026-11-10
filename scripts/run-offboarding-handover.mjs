#!/usr/bin/env node
/**
 * Manually run FR-EMP-10 deferred offboarding handover (same as daily 02:00 cron).
 *
 * Usage (from repo root):
 *   npm run offboarding-handover
 *   node scripts/run-offboarding-handover.mjs
 *
 * Env (optional — defaults match local .env):
 *   PEOPLE_URL              default http://127.0.0.1:8081
 *   PLATFORM_SERVICE_KEY
 *   PLATFORM_INTERNAL_KEY
 */
import fs from "node:fs";
import http from "node:http";
import https from "node:https";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");

function loadDotEnv() {
  const envPath = path.join(ROOT, ".env");
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, "utf8").split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const i = t.indexOf("=");
    if (i <= 0) continue;
    const k = t.slice(0, i).trim();
    let v = t.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    if (process.env[k] === undefined) process.env[k] = v;
  }
}

loadDotEnv();

const base = (process.env.PEOPLE_URL || "http://127.0.0.1:8081").replace(/\/$/, "");
const serviceKey = process.env.PLATFORM_SERVICE_KEY || "local-service-key-change-me";
const internalKey = process.env.PLATFORM_INTERNAL_KEY || "local-internal-key-change-me";
const url = new URL(`${base}/api/internal/jobs/offboarding-handover`);

function request() {
  return new Promise((resolve, reject) => {
    const lib = url.protocol === "https:" ? https : http;
    const req = lib.request(
      {
        hostname: url.hostname,
        port: url.port || (url.protocol === "https:" ? 443 : 80),
        path: url.pathname,
        method: "POST",
        headers: {
          "X-Service-Key": serviceKey,
          "X-Internal-Key": internalKey,
          Accept: "application/json",
          "Content-Length": 0,
        },
        timeout: 60000,
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const raw = Buffer.concat(chunks).toString("utf8");
          let body = raw;
          try {
            body = JSON.parse(raw);
          } catch {
            /* keep string */
          }
          resolve({ status: res.statusCode || 0, body });
        });
      },
    );
    req.on("error", reject);
    req.on("timeout", () => {
      req.destroy();
      reject(new Error("Request timed out"));
    });
    req.end();
  });
}

const { status, body } = await request();
if (status >= 200 && status < 300) {
  console.log(JSON.stringify(body, null, 2));
  process.exit(0);
}
console.error(`offboarding-handover failed HTTP ${status}`);
console.error(typeof body === "string" ? body : JSON.stringify(body, null, 2));
process.exit(1);
