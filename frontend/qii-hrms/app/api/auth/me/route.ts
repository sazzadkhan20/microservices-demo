import { NextResponse } from "next/server";
import { getMe, jsonError, clearSessionCookie } from "@/lib/api/server";

export const dynamic = "force-dynamic";

export async function GET() {
  const result = await getMe();
  if (result.status === "unauthenticated") {
    const res = jsonError(401, "Not authenticated");
    clearSessionCookie(res);
    return res;
  }
  if (result.status === "error") return jsonError(502, result.message);
  return NextResponse.json(result.me);
}
