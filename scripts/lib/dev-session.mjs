// Dev/test-only helper: mints gateway session JWTs (HS256) with the LOCAL JWT_SECRET from the repo-root .env,
// so system / RBAC / load tests run while demo login is off (OIDC mode). Refuses to run against prod settings.
import fs from "node:fs";
import crypto from "node:crypto";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");

export function loadEnv() {
  const env = { ...process.env };
  const file = path.join(root, ".env");
  if (fs.existsSync(file)) {
    for (const line of fs.readFileSync(file, "utf8").split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
      if (m && env[m[1]] === undefined) env[m[1]] = m[2].replace(/^["']|["']$/g, "");
    }
  }
  return env;
}

const b64u = (b) => Buffer.from(b).toString("base64url");

export function mintSession(employeeId, { ttlSeconds = 3600, env = loadEnv(), overrides = {}, header = {} } = {}) {
  if ((env.PLATFORM_ENV || "").toLowerCase().startsWith("prod")) throw new Error("dev-session refuses PLATFORM_ENV=prod");
  if (!env.JWT_SECRET) throw new Error("JWT_SECRET missing in .env");
  const { __secret, ...claims } = overrides;
  const now = Math.floor(Date.now() / 1000);
  const h = { alg: "HS256", typ: "JWT", ...header };
  const p = {
    jti: crypto.randomUUID(), iss: "hrms-gateway", aud: ["hrms-services"], sub: `emp:${employeeId}`,
    employee_id: employeeId, iat: now, exp: now + ttlSeconds, ...claims,
  };
  const unsigned = `${b64u(JSON.stringify(h))}.${b64u(JSON.stringify(p))}`;
  const secret = __secret ?? env.JWT_SECRET;
  const sig = h.alg === "none" ? "" : crypto.createHmac("sha256", secret).update(unsigned).digest("base64url");
  return `${unsigned}.${sig}`;
}

export const GATEWAY = (loadEnv().GATEWAY_URL || "http://127.0.0.1:8090").replace(/\/+$/, "");
