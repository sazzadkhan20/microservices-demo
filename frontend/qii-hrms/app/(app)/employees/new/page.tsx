"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { EmployeeForm } from "@/components/employees/EmployeeForm";
import { PageHeader } from "@/components/ui/PageHeader";

export default function NewEmployeePage() {
  const router = useRouter();
  return (
    <div className="stack">
      <PageHeader
        title="Add employee"
        subtitle="Full employment profile. Employee ID is generated from the business pack; new people get the Employee role by default."
        actions={
          <Link href="/employees" className="btn">
            ← All employees
          </Link>
        }
      />
      <div className="card">
        <EmployeeForm
          mode="create"
          onSaved={(e) => router.push(`/employees/${e.id}`)}
          onCancel={() => router.push("/employees")}
        />
      </div>
    </div>
  );
}
