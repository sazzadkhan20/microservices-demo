import { execFileSync } from "child_process";
import fs from "fs";
import path from "path";

const tenants = [
  "impit_limited",
  "qa_should_not",
  "qa_should_not_exist",
  "qii_creative",
  "techshop_retail",
];
const outDir = path.resolve(".tmp-enrich");
fs.mkdirSync(outDir, { recursive: true });

function psql(db, sql) {
  return execFileSync(
    "docker",
    ["exec", "microservices-demo-postgres-hrms-1", "psql", "-U", "platform", "-d", db, "-Atc", sql],
    { encoding: "utf8" },
  );
}

for (const t of tenants) {
  const csv = psql(
    `people_${t}`,
    `COPY (
      SELECT e.id,
             COALESCE(d.approver_role,''),
             COALESCE(tm.code,''),
             COALESCE(tm.max_on_leave::text,''),
             COALESCE(tm.clash_action,''),
             COALESCE(tm.lead_employee_id::text,'')
      FROM employee e
      LEFT JOIN designation d ON d.id = e.designation_id
      LEFT JOIN team tm ON tm.id = e.team_id
    ) TO STDOUT WITH (FORMAT csv)`,
  );
  const file = path.join(outDir, `enrich_${t}.csv`);
  fs.writeFileSync(file, csv);
  const rows = csv.trim().split(/\r?\n/).filter(Boolean);
  for (const line of rows) {
    const cols = [];
    let cur = "";
    let q = false;
    for (const c of line) {
      if (c === '"') {
        q = !q;
        continue;
      }
      if (c === "," && !q) {
        cols.push(cur);
        cur = "";
        continue;
      }
      cur += c;
    }
    cols.push(cur);
    const [id, role, code, mx, clash, lead] = cols;
    const esc = (s) => (s || "").replace(/'/g, "''");
    const mxSql = mx === "" ? "NULL" : mx;
    const leadSql = lead === "" ? "NULL" : lead;
    const sql = `UPDATE employee SET
      designation_approver_role=NULLIF('${esc(role)}',''),
      team_code=NULLIF('${esc(code)}',''),
      team_max_on_leave=${mxSql},
      team_clash_action=NULLIF('${esc(clash)}',''),
      team_lead_employee_id=${leadSql}
      WHERE id=${id};`;
    execFileSync(
      "docker",
      ["exec", "microservices-demo-postgres-hrms-1", "psql", "-U", "platform", "-d", `leave_${t}`, "-c", sql],
      { stdio: "ignore" },
    );
  }
  console.log(`enriched leave_${t} (${rows.length} rows)`);
}
