"use client";

import Link from "next/link";
import { useAuth } from "@/components/auth/AuthProvider";
import { MonthlyAttendanceReport } from "@/components/reports/MonthlyAttendanceReport";
import { PageHeader } from "@/components/ui/PageHeader";

export default function MyMonthlyReportPage() {
  const { me, can } = useAuth();
  return (
    <div className="stack">
      <PageHeader
        title="Monthly report"
        subtitle={`Your attendance for a month, ${me.name}. Preview it here or download the CSV.`}
        actions={
          can("reports", "export") ? (
            <Link href="/reports" className="btn">
              All reports
            </Link>
          ) : null
        }
      />
      <div className="card">
        <MonthlyAttendanceReport fixedEmployeeId={String(me.employeeId)} canExport />
      </div>
    </div>
  );
}
