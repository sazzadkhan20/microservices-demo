import { NextResponse } from "next/server";
import { config } from "@/lib/config";
import { oidcConfigured } from "@/lib/auth/oidc";

export const dynamic = "force-dynamic";

/** Public, non-secret status for the login page (no client secrets). */
export async function GET() {
  return NextResponse.json({
    oidcEnabled: oidcConfigured(),
    demoAuthEnabled: config.demoAuthEnabled && Boolean(config.demoAuthKey),
    googleHd: config.googleHd || null,
  });
}
