import fs from "fs";
import path from "path";

function walk(dir, acc = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, acc);
    else if (p.endsWith(".java")) acc.push(p);
  }
  return acc;
}

function addImport(text, importLine) {
  if (text.includes(importLine)) return text;
  const lines = text.split(/\r?\n/);
  let lastImport = -1;
  for (let i = 0; i < lines.length; i++) if (lines[i].startsWith("import ")) lastImport = i;
  if (lastImport >= 0) lines.splice(lastImport + 1, 0, importLine);
  return lines.join("\n");
}

const jobs = [
  [
    "leave-service/src",
    {
      PeopleAuthzClient: "import com.platform.leave.client.people.PeopleAuthzClient;",
      PeopleEmployeeGateway: "import com.platform.leave.client.people.PeopleEmployeeGateway;",
      EmployeeView: "import com.platform.leave.client.people.EmployeeView;",
      TimeWorkScheduleGateway: "import com.platform.leave.client.timework.TimeWorkScheduleGateway;",
      ScheduleView: "import com.platform.leave.client.timework.ScheduleView;",
    },
  ],
  [
    "time-work-service/src",
    {
      PeopleAuthzClient: "import com.platform.timework.client.people.PeopleAuthzClient;",
      PeopleEmployeeGateway: "import com.platform.timework.client.people.PeopleEmployeeGateway;",
      EmployeeView: "import com.platform.timework.client.people.EmployeeView;",
    },
  ],
];

for (const [base, map] of jobs) {
  for (const f of walk(base)) {
    let t = fs.readFileSync(f, "utf8");
    let needed = [];
    for (const [sym, imp] of Object.entries(map)) {
      if (new RegExp(`(class|interface|enum)\\s+${sym}\\b`).test(t)) continue;
      if (!new RegExp(`\\b${sym}\\b`).test(t)) continue;
      if (!t.includes(imp)) needed.push(imp);
    }
    if (!needed.length) continue;
    for (const imp of needed) t = addImport(t, imp);
    fs.writeFileSync(f, t.replace(/\r\n/g, "\n"));
    console.log("updated", f, needed.length);
  }
}
