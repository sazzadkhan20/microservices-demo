import type { NextRequest } from "next/server";
import { proxyToGateway } from "@/lib/api/server";

// Generic BFF proxy: /api/reports/<path> -> GATEWAY /reports/api/reports/<path> (Bearer from httpOnly cookie).
export const dynamic = "force-dynamic";

const target = { upstreamPrefix: "/reports/api/reports" };

type Ctx = { params: Promise<{ path?: string[] }> };

async function handle(req: NextRequest, ctx: Ctx) {
  const { path } = await ctx.params;
  return proxyToGateway(req, path, target);
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
export const HEAD = handle;
