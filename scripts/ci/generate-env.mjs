#!/usr/bin/env node
// CI / ephemeral environments: build a throwaway .env from .env.example with a fresh random value for
// every secret (no real or shared credential ever reaches CI). Demo login stays OFF; system tests mint
// sessions with the generated JWT secret (scripts/lib/dev-session.mjs).
//   node scripts/ci/generate-env.mjs            # writes ./.env (refuses to overwrite unless --force)
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const target = path.join(root, ".env");
if (fs.existsSync(target) && !process.argv.includes("--force")) {
  console.error(".env already exists — pass --force to replace it (CI only)");
  process.exit(1);
}
const rnd = (n = 32) => crypto.randomBytes(n).toString("base64url");
const SECRET = /(PASSWORD|SECRET|_KEY|TOKEN)$/;
const FIXED = {
  DEMO_AUTH_ENABLED: "false",
  OIDC_ENABLED: "false",
  PLATFORM_ENV: "ci",
  MINIO_ROOT_USER: "ci-minio-root",
  RABBITMQ_USER: "ci-broker",
  GOOGLE_CLIENT_ID: "",
  GOOGLE_CLIENT_SECRET: "",
};
const out = [];
for (const line of fs.readFileSync(path.join(root, ".env.example"), "utf8").split(/\r?\n/)) {
  const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
  if (!m) { out.push(line); continue; }
  const [, k, v] = m;
  if (k in FIXED) out.push(`${k}=${FIXED[k]}`);
  else if (SECRET.test(k)) out.push(`${k}=${rnd(k === "JWT_SECRET" ? 48 : 32)}`);
  else out.push(`${k}=${v}`);
}
for (const [k, v] of Object.entries(FIXED)) if (!out.some((l) => l.startsWith(`${k}=`))) out.push(`${k}=${v}`);
// MinIO service credentials must match the root user in the throwaway environment.
const get = (k) => (out.find((l) => l.startsWith(`${k}=`)) || "").split("=").slice(1).join("=");
const set = (k, v) => { const i = out.findIndex((l) => l.startsWith(`${k}=`)); if (i >= 0) out[i] = `${k}=${v}`; else out.push(`${k}=${v}`); };
set("MINIO_ACCESS_KEY", get("MINIO_ROOT_USER"));
set("MINIO_SECRET_KEY", get("MINIO_ROOT_PASSWORD"));
fs.writeFileSync(target, out.join("\n") + "\n", { mode: 0o600 });
console.log(`wrote ${target} (${out.filter((l) => /^[A-Z0-9_]+=/.test(l)).length} keys, secrets randomised)`);
