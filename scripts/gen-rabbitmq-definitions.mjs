#!/usr/bin/env node
/**
 * Regenerates docker/rabbitmq/definitions.json users block from env:
 *   RABBITMQ_USER / RABBITMQ_PASSWORD
 * Topology (exchanges/queues/bindings) is preserved.
 */
import crypto from "crypto";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const defPath = path.join(root, "docker/rabbitmq/definitions.json");
const envPath = path.join(root, ".env");

function loadEnvFile(p) {
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, "utf8").split(/\r?\n/)) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const i = t.indexOf("=");
    if (i < 0) continue;
    const k = t.slice(0, i).trim();
    let v = t.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    if (!(k in process.env)) process.env[k] = v;
  }
}

loadEnvFile(envPath);

const user = process.env.RABBITMQ_USER || "platform";
const password = process.env.RABBITMQ_PASSWORD;
if (!password) {
  console.error("RABBITMQ_PASSWORD is required (set in .env)");
  process.exit(1);
}

const salt = crypto.randomBytes(4);
const hash = crypto.createHash("sha256").update(Buffer.concat([salt, Buffer.from(password, "utf8")])).digest();
const passwordHash = Buffer.concat([salt, hash]).toString("base64");

const existing = JSON.parse(fs.readFileSync(defPath, "utf8"));
const out = {
  rabbit_version: existing.rabbit_version || "3.13.0",
  rabbitmq_version: existing.rabbitmq_version || "3.13.0",
  users: [
    {
      name: user,
      password_hash: passwordHash,
      hashing_algorithm: "rabbit_password_hashing_sha256",
      tags: ["administrator"],
      limits: {},
    },
  ],
  vhosts: [{ name: "/" }],
  permissions: [
    {
      user,
      vhost: "/",
      configure: ".*",
      write: ".*",
      read: ".*",
    },
  ],
  topic_permissions: existing.topic_permissions || [],
  parameters: existing.parameters || [],
  global_parameters: existing.global_parameters || [],
  policies: existing.policies || [],
  exchanges: existing.exchanges || [],
  queues: existing.queues || [],
  bindings: existing.bindings || [],
};

fs.writeFileSync(defPath, JSON.stringify(out, null, 2) + "\n");
console.log(`Updated ${path.relative(root, defPath)} for user=${user}`);
