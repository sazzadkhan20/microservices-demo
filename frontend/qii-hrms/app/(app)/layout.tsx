import { redirect } from "next/navigation";
import { getMe } from "@/lib/api/server";
import { AuthProvider } from "@/components/auth/AuthProvider";
import { AppShell } from "@/components/shell/AppShell";
import { RouteGuard } from "@/components/shell/RouteGuard";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const result = await getMe();
  if (result.status === "unauthenticated") redirect("/login");
  if (result.status === "error") redirect("/login");
  const me = result.me;
  if (!me.roles || me.roles.length === 0) redirect("/access-pending");

  return (
    <AuthProvider initialMe={me}>
      <AppShell>
        <RouteGuard>{children}</RouteGuard>
      </AppShell>
    </AuthProvider>
  );
}
