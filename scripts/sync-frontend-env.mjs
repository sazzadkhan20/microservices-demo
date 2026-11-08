#!/usr/bin/env node
/**
 * Syncs frontend/qii-hrms/.env secrets from repo-root .env.
 * Non-secret frontend settings are preserved when already set.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const rootEnv = path.join(root, ".env");
const feEnv = path.join(root, "frontend/qii-hrms/.env");

function parseEnv(text) {
  const out = {};
  for (const line of text.split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const i = t.indexOf("=");
    if (i < 0) continue;
    out[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return out;
}

if (!fs.existsSync(rootEnv)) {
  console.error("Missing root .env — copy .env.example first");
  process.exit(1);
}

const rootVars = parseEnv(fs.readFileSync(rootEnv, "utf8"));
const existing = fs.existsSync(feEnv) ? parseEnv(fs.readFileSync(feEnv, "utf8")) : {};

const next = {
  TENANT_ID: existing.TENANT_ID || "qii_creative",
  GATEWAY_URL: existing.GATEWAY_URL || "http://127.0.0.1:8090",
  COOKIE_NAME: existing.COOKIE_NAME || "qii_session",
  GATEWAY_TIMEOUT_MS: existing.GATEWAY_TIMEOUT_MS || "30000",
  DEMO_AUTH_KEY: rootVars.DEMO_AUTH_KEY || "",
};
if (existing.COOKIE_SECURE) next.COOKIE_SECURE = existing.COOKIE_SECURE;

const body =
  Object.entries(next)
    .map(([k, v]) => `${k}=${v}`)
    .join("\n") + "\n";

fs.writeFileSync(feEnv, body);
console.log(`Synced ${path.relative(root, feEnv)} DEMO_AUTH_KEY from root .env`);
