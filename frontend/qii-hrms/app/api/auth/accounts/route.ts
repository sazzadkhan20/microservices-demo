import { NextResponse } from "next/server";
import { config } from "@/lib/config";
import { demoAuthHeaders, gatewayJson, GatewayError, jsonError } from "@/lib/api/server";
import { accountListSchema } from "@/lib/schemas/account";

export const dynamic = "force-dynamic";

/** Account-picker list. The gateway requires X-Demo-Auth-Key, which only this BFF holds. */
export async function GET() {
  try {
    const raw = await gatewayJson(`/hrms/api/auth/accounts?tenantId=${encodeURIComponent(config.tenantId)}`, {
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
