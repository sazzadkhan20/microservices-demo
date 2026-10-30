import { GW, req, token, A } from "./lib.mjs";
const H = `${GW}/hrms/api`, P = `${GW}/payroll/api`;
const tEmp = await token(9), tHr = await token(2), tMgr = await token(4), tAdm = await token(1);
const show = (k, r, n = 300) => console.log(`\n### ${k}: ${r.status}\n${r.text.slice(0, n)}`);

let r = await req("GET", `${H}/attendance/employee/1?page=0&size=50`, { headers: A(tEmp) });
console.log("attendance/employee/1 as Karim -> distinct employeeIds:", [...new Set((r.json?.content || []).map((x) => x.employeeId))], "status", r.status);
r = await req("GET", `${H}/attendance/employee/4?page=0&size=50`, { headers: A(tEmp) });
console.log("attendance/employee/4 as Karim -> ids:", [...new Set((r.json?.content || []).map((x) => x.employeeId))]);

r = await req("GET", `${P}/payroll/payslips?size=100`, { headers: A(tEmp) });
console.log("payslips as Karim -> employeeIds:", [...new Set((r.json?.content || []).map((x) => x.employeeId))], "statuses", [...new Set((r.json?.content || []).map((x) => x.status))]);

r = await req("GET", `${H}/reports/leave-balances.csv`, { headers: A(tEmp) });
console.log("leave-balances.csv as Karim rows:", r.text.trim().split("\n").length - 1);
r = await req("GET", `${H}/reports/leave-requests.csv`, { headers: A(tEmp) });
console.log("leave-requests.csv as Karim:", r.status, "rows", r.text.trim().split("\n").length - 1, r.text.split("\n")[0]);
r = await req("GET", `${H}/reports/late-absence.csv?month=2026-10`, { headers: A(tEmp) });
console.log("late-absence.csv as Karim:", r.status, "rows", r.text.trim().split("\n").length - 1);

r = await req("GET", `${H}/attendance/team`, { headers: A(tEmp) });
console.log("attendance/team as Karim ids:", [...new Set((r.json?.content || r.json || []).map((x) => x.employeeId))]);

r = await req("GET", `${GW}/reports/api/reports/exports`, { headers: A(tEmp) });
const ex = r.json || [];
console.log("report exports as Karim:", ex.length, "sample", JSON.stringify(ex[0]).slice(0, 300));

r = await req("GET", `${H}/dashboard/work-hours?period=month&date=2026-10-01`, { headers: A(tEmp) });
console.log("work-hours as Karim rows:", (r.json || []).length, r.status);

// Salary exposure to HR & manager
for (const [who, t] of [["HR", tHr], ["MGR", tMgr]]) {
  r = await req("GET", `${H}/employees?page=0&size=100`, { headers: A(t) });
  const rows = r.json?.content || [];
  const withSal = rows.filter((x) => x.baseSalary != null);
  console.log(`${who} employee list rows=${rows.length} withBaseSalary=${withSal.length} e.g.`, withSal.slice(0, 2).map((x) => [x.id, x.fullName, x.baseSalary]));
  r = await req("GET", `${H}/employees/1`, { headers: A(t) });
  console.log(`${who} GET employees/1 -> ${r.status} baseSalary=${r.json?.baseSalary}`);
  r = await req("GET", `${P}/payroll/salary-history/employee/1`, { headers: A(t) });
  console.log(`${who} salary-history/1 -> ${r.status}`);
}

// Manager scope: Lina (4) sees who?
r = await req("GET", `${H}/employees?page=0&size=100`, { headers: A(tMgr) });
console.log("Lina sees employees:", (r.json?.content || []).map((x) => `${x.id}:${x.fullName}:mgr${x.managerId}`).join(", "));

// Directory as employee
r = await req("GET", `${H}/employees/directory?page=0&size=100`, { headers: A(tEmp) });
show("directory as employee", r, 400);

// nonexistent employee token
const t999 = await token(99999);
r = await req("GET", `${H}/me`, { headers: A(t999) });
show("me as employee 99999", r);
r = await req("GET", `${H}/employees?page=0&size=5`, { headers: A(t999) });
show("employees as 99999", r, 200);
r = await req("POST", `${H}/attendance/check-in`, { headers: A(t999), body: {} });
show("check-in as 99999", r, 200);

// inactive employee token: find any inactive
r = await req("GET", `${H}/employees?page=0&size=100&status=INACTIVE`, { headers: A(tAdm) });
console.log("inactive:", (r.json?.content || []).filter(x => x.status !== "ACTIVE").map((x) => [x.id, x.status]));

// notification create schema
r = await req("POST", `${GW}/notifications/api/notifications`, { headers: A(tEmp), body: { employeeId: 1, type: "PHISH", title: "Security alert", body: "Click http://evil" } });
show("notif create as employee for emp 1", r);
r = await req("GET", `${GW}/notifications/api/notifications`, { headers: A(tAdm) });
console.log("admin inbox top:", JSON.stringify((r.json?.content || r.json || [])[0]).slice(0, 250));
