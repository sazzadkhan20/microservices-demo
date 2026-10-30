import crypto from "node:crypto";

export const GW = process.env.GW || "http://127.0.0.1:8090";
export const DEMO = process.env.DEMO || "local-service-key-change-me";
export const results = [];

export async function req(method, url, { headers = {}, body, raw = false } = {}) {
  const h = { ...headers };
  let data;
  if (body !== undefined) {
    if (body instanceof FormData) data = body;
    else { data = typeof body === "string" ? body : JSON.stringify(body); h["Content-Type"] ??= "application/json"; }
  }
  const t0 = performance.now();
  try {
    const r = await fetch(url, { method, headers: h, body: data, redirect: "manual" });
    const text = await r.text();
    let json = null;
    try { json = JSON.parse(text); } catch {}
    return { status: r.status, json, text, ms: Math.round(performance.now() - t0), headers: r.headers };
  } catch (e) {
    return { status: 0, error: String(e), text: "", json: null };
  }
}

export async function token(employeeId, tenantId = "qii_creative") {
  const r = await req("POST", `${GW}/api/registry/token`, {
    headers: { "X-Demo-Auth-Key": DEMO },
    body: { tenantId, employeeId: employeeId == null ? undefined : Number(employeeId) },
  });
  return r.json?.accessToken || null;
}

export const A = (t) => ({ Authorization: `Bearer ${t}` });

export function check(id, ok, detail, sev = "High") {
  results.push({ id, ok: !!ok, sev, detail: typeof detail === "string" ? detail : JSON.stringify(detail).slice(0, 400) });
  console.log(`${ok ? "PASS" : "FAIL"} [${sev}] ${id} — ${typeof detail === "string" ? detail : JSON.stringify(detail).slice(0, 300)}`);
}

export function b64url(o) {
  return Buffer.from(typeof o === "string" ? o : JSON.stringify(o)).toString("base64url");
}

export function signJwt(payload, secret, alg = "HS256") {
  const head = b64url({ alg, typ: "JWT" });
  const body = b64url(payload);
  if (alg === "none") return `${head}.${body}.`;
  const sig = crypto.createHmac("sha256", secret).update(`${head}.${body}`).digest("base64url");
  return `${head}.${body}.${sig}`;
}

export function decode(t) {
  return JSON.parse(Buffer.from(t.split(".")[1], "base64url").toString());
}

export function summary(name) {
  const f = results.filter((r) => !r.ok);
  console.log(`\n== ${name}: ${results.length - f.length}/${results.length} pass, ${f.length} fail`);
  return f;
}
