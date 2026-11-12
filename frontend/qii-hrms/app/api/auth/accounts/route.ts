import { NextResponse } from "next/server";
import { config } from "@/lib/config";
import { demoAuthHeaders, gatewayJson, GatewayError, jsonError } from "@/lib/api/server";
import { accountListSchema } from "@/lib/schemas/account";

export const dynamic = "force-dynamic";

/** Account-picker list (demo only). Disabled when demo auth is off. */
export async function GET() {
  if (!config.demoAuthEnabled || !config.demoAuthKey) {
    return jsonError(410, "Demo account picker is disabled. Sign in with Google.");
  }
  try {
    const raw = await gatewayJson(`/hrms/api/auth/accounts`, {
      headers: demoAuthHeaders(),
    });
    const parsed = accountListSchema.safeParse(raw);
    if (!parsed.success) {
      return jsonError(502, "Unexpected accounts response");
    }
    return NextResponse.json(parsed.data);
  } catch (err) {
    const e = err as GatewayError;
    return jsonError(e.status ?? 502, e.message ?? "Failed to load accounts");
  }
}
