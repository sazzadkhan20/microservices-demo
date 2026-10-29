import { NextResponse } from "next/server";
import { clearSessionCookie } from "@/lib/api/server";

export const dynamic = "force-dynamic";

export async function POST() {
  const res = NextResponse.json({ ok: true });
  clearSessionCookie(res);
  return res;
}
