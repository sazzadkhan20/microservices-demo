#!/usr/bin/env node
/**
 * Repackage people / leave / time-work off legacy com.platform.hrms into
 * service-owned roots, and move remote adapters under client.* packages.
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "..");

function read(p) {
  return fs.readFileSync(p, "utf8");
}
function write(p, text) {
  fs.mkdirSync(path.dirname(p), { recursive: true });
  fs.writeFileSync(p, text.replace(/\r\n/g, "\n"), "utf8");
}
function exists(p) {
  return fs.existsSync(p);
}
function walk(dir, acc = []) {
  if (!exists(dir)) return acc;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, acc);
    else acc.push(p);
  }
  return acc;
}
function rmEmptyDirs(dir) {
  if (!exists(dir)) return;
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    if (ent.isDirectory()) rmEmptyDirs(path.join(dir, ent.name));
  }
  try {
    if (fs.readdirSync(dir).length === 0) fs.rmdirSync(dir);
  } catch {}
}
function packageFromJavaFile(javaFile) {
  const parts = javaFile.split(path.sep);
  const idx = parts.lastIndexOf("java");
  return parts.slice(idx + 1, -1).join(".");
}
function rewriteContent(text, oldPkg, newPkg, protectGrpc) {
  if (protectGrpc) text = text.split("com.platform.hrms.api.grpc").join("__KEEP_HRMS_GRPC__");
  text = text.split(oldPkg).join(newPkg);
  if (protectGrpc) text = text.split("__KEEP_HRMS_GRPC__").join("com.platform.hrms.api.grpc");
  return text;
}
function setPackage(text, pkg) {
  return text.replace(/^package\s+[\w.]+;/m, `package ${pkg};`);
}
function moveTree(srcRoot, dstRoot, oldPkg, newPkg, protectGrpc) {
  if (!exists(srcRoot)) return;
  for (const src of walk(srcRoot).filter((f) => f.endsWith(".java"))) {
    const rel = path.relative(srcRoot, src);
    const dst = path.join(dstRoot, rel);
    let text = rewriteContent(read(src), oldPkg, newPkg, protectGrpc);
    text = setPackage(text, packageFromJavaFile(dst));
    write(dst, text);
    fs.unlinkSync(src);
  }
  rmEmptyDirs(srcRoot);
}
function replaceInTree(base, pairs, exts = [".java", ".yml"]) {
  for (const file of walk(base)) {
    if (!exts.some((e) => file.endsWith(e))) continue;
    let text = read(file);
    const orig = text;
    for (const [a, b] of pairs) text = text.split(a).join(b);
    if (text !== orig) write(file, text);
  }
}
function relocate(src, dst, renames = []) {
  if (!exists(src)) return;
  let text = read(src);
  text = setPackage(text, packageFromJavaFile(dst));
  for (const [a, b] of renames) {
    text = text.replace(new RegExp(`\\b${a}\\b`, "g"), b);
  }
  write(dst, text);
  fs.unlinkSync(src);
}
function wordReplace(text, from, to) {
  return text.replace(new RegExp(`\\b${from}\\b`, "g"), to);
}

const services = [
  { name: "people-service", dir: "people", pkg: "com.platform.people", protectGrpc: true },
  { name: "leave-service", dir: "leave", pkg: "com.platform.leave", protectGrpc: false },
  { name: "time-work-service", dir: "timework", pkg: "com.platform.timework", protectGrpc: false },
];

for (const svc of services) {
  const base = path.join(ROOT, svc.name);
  const javaMain = path.join(base, "src/main/java");
  const javaTest = path.join(base, "src/test/java");
  console.log(`== Moving main ${svc.name}`);
  moveTree(
    path.join(javaMain, "com/platform/hrms"),
    path.join(javaMain, "com/platform", svc.dir),
    "com.platform.hrms",
    svc.pkg,
    svc.protectGrpc
  );
  console.log(`== Moving test ${svc.name}`);
  moveTree(
    path.join(javaTest, "com/platform/hrms"),
    path.join(javaTest, "com/platform", svc.dir),
    "com.platform.hrms",
    svc.pkg,
    svc.protectGrpc
  );
  replaceInTree(base, [["com.platform.hrms", svc.pkg]]);
  if (svc.protectGrpc) {
    // restore generated grpc imports; then fix hand-written package decls
    replaceInTree(base, [["com.platform.people.api.grpc", "com.platform.hrms.api.grpc"]], [".java"]);
    const grpcDir = path.join(javaMain, "com/platform/people/api/grpc");
    for (const f of walk(grpcDir).filter((x) => x.endsWith(".java"))) {
      let text = read(f);
      text = text.replace(/^package\s+com\.platform\.hrms\.api\.grpc;/m, "package com.platform.people.api.grpc;");
      write(f, text);
    }
  }
}

// Leave adapters
const leave = path.join(ROOT, "leave-service/src/main/java/com/platform/leave");
console.log("== Leave adapters");
relocate(path.join(leave, "domain/Employee.java"), path.join(leave, "client/people/EmployeeView.java"), [
  ["Employee", "EmployeeView"],
]);
relocate(
  path.join(leave, "infrastructure/persistence/EmployeeRepository.java"),
  path.join(leave, "client/people/PeopleEmployeeGateway.java"),
  [
    ["EmployeeRepository", "PeopleEmployeeGateway"],
    ["Employee", "EmployeeView"],
  ]
);
relocate(path.join(leave, "application/RbacService.java"), path.join(leave, "client/people/PeopleAuthzClient.java"), [
  ["RbacService", "PeopleAuthzClient"],
]);
relocate(path.join(leave, "domain/WorkSchedule.java"), path.join(leave, "client/timework/ScheduleView.java"), [
  ["WorkSchedule", "ScheduleView"],
]);
relocate(
  path.join(leave, "application/WorkScheduleService.java"),
  path.join(leave, "client/timework/TimeWorkScheduleGateway.java"),
  [
    ["WorkScheduleService", "TimeWorkScheduleGateway"],
    ["WorkSchedule", "ScheduleView"],
    ["Employee", "EmployeeView"],
  ]
);

for (const file of walk(path.join(ROOT, "leave-service")).filter((f) => f.endsWith(".java"))) {
  let text = read(file);
  const orig = text;
  const pairs = [
    ["com.platform.leave.domain.Employee", "com.platform.leave.client.people.EmployeeView"],
    [
      "com.platform.leave.infrastructure.persistence.EmployeeRepository",
      "com.platform.leave.client.people.PeopleEmployeeGateway",
    ],
    ["com.platform.leave.application.RbacService", "com.platform.leave.client.people.PeopleAuthzClient"],
    ["com.platform.leave.domain.WorkSchedule", "com.platform.leave.client.timework.ScheduleView"],
    [
      "com.platform.leave.application.WorkScheduleService",
      "com.platform.leave.client.timework.TimeWorkScheduleGateway",
    ],
    ["EmployeeRepository", "PeopleEmployeeGateway"],
    ["RbacService", "PeopleAuthzClient"],
    ["WorkScheduleService", "TimeWorkScheduleGateway"],
  ];
  for (const [a, b] of pairs) text = text.split(a).join(b);
  text = wordReplace(text, "Employee", "EmployeeView");
  text = wordReplace(text, "WorkSchedule", "ScheduleView");
  text = text.split("EmployeeViewView").join("EmployeeView");
  text = text.split("ScheduleViewView").join("ScheduleView");
  text = text.split("PeopleAuthzClientAuthzClient").join("PeopleAuthzClient");
  text = text.split("PeopleEmployeeGatewayEmployeeGateway").join("PeopleEmployeeGateway");
  text = text.split("TimeWorkScheduleGatewayScheduleGateway").join("TimeWorkScheduleGateway");
  if (text !== orig) write(file, text);
}

// Timework adapters
const tw = path.join(ROOT, "time-work-service/src/main/java/com/platform/timework");
console.log("== TimeWork adapters");
relocate(path.join(tw, "domain/Employee.java"), path.join(tw, "client/people/EmployeeView.java"), [
  ["Employee", "EmployeeView"],
]);
relocate(
  path.join(tw, "infrastructure/persistence/EmployeeRepository.java"),
  path.join(tw, "client/people/PeopleEmployeeGateway.java"),
  [
    ["EmployeeRepository", "PeopleEmployeeGateway"],
    ["Employee", "EmployeeView"],
  ]
);
relocate(path.join(tw, "application/RbacService.java"), path.join(tw, "client/people/PeopleAuthzClient.java"), [
  ["RbacService", "PeopleAuthzClient"],
]);

for (const file of walk(path.join(ROOT, "time-work-service")).filter((f) => f.endsWith(".java"))) {
  let text = read(file);
  const orig = text;
  const pairs = [
    ["com.platform.timework.domain.Employee", "com.platform.timework.client.people.EmployeeView"],
    [
      "com.platform.timework.infrastructure.persistence.EmployeeRepository",
      "com.platform.timework.client.people.PeopleEmployeeGateway",
    ],
    ["com.platform.timework.application.RbacService", "com.platform.timework.client.people.PeopleAuthzClient"],
    ["EmployeeRepository", "PeopleEmployeeGateway"],
    ["RbacService", "PeopleAuthzClient"],
  ];
  for (const [a, b] of pairs) text = text.split(a).join(b);
  text = wordReplace(text, "Employee", "EmployeeView");
  text = text.split("EmployeeViewView").join("EmployeeView");
  text = text.split("PeopleAuthzClientAuthzClient").join("PeopleAuthzClient");
  text = text.split("PeopleEmployeeGatewayEmployeeGateway").join("PeopleEmployeeGateway");
  if (text !== orig) write(file, text);
}

// Rename HrmsConfig / HrmsProperties
function renameClass(src, dst, oldName, newName) {
  if (!exists(src)) return;
  let text = read(src);
  text = wordReplace(text, oldName, newName);
  text = setPackage(text, packageFromJavaFile(dst));
  write(dst, text);
  if (path.resolve(src) !== path.resolve(dst)) fs.unlinkSync(src);
}
renameClass(
  path.join(ROOT, "people-service/src/main/java/com/platform/people/config/HrmsConfig.java"),
  path.join(ROOT, "people-service/src/main/java/com/platform/people/config/PeopleConfig.java"),
  "HrmsConfig",
  "PeopleConfig"
);
renameClass(
  path.join(ROOT, "people-service/src/main/java/com/platform/people/config/HrmsProperties.java"),
  path.join(ROOT, "people-service/src/main/java/com/platform/people/config/PeopleProperties.java"),
  "HrmsProperties",
  "PeopleProperties"
);
renameClass(
  path.join(ROOT, "leave-service/src/main/java/com/platform/leave/config/HrmsConfig.java"),
  path.join(ROOT, "leave-service/src/main/java/com/platform/leave/config/LeaveAppConfig.java"),
  "HrmsConfig",
  "LeaveAppConfig"
);
renameClass(
  path.join(ROOT, "leave-service/src/main/java/com/platform/leave/config/HrmsProperties.java"),
  path.join(ROOT, "leave-service/src/main/java/com/platform/leave/config/LeaveProperties.java"),
  "HrmsProperties",
  "LeaveProperties"
);
renameClass(
  path.join(ROOT, "time-work-service/src/main/java/com/platform/timework/config/HrmsConfig.java"),
  path.join(ROOT, "time-work-service/src/main/java/com/platform/timework/config/TimeWorkAppConfig.java"),
  "HrmsConfig",
  "TimeWorkAppConfig"
);
renameClass(
  path.join(ROOT, "time-work-service/src/main/java/com/platform/timework/config/HrmsProperties.java"),
  path.join(ROOT, "time-work-service/src/main/java/com/platform/timework/config/TimeWorkProperties.java"),
  "HrmsProperties",
  "TimeWorkProperties"
);

replaceInTree(path.join(ROOT, "people-service"), [
  ["HrmsConfig", "PeopleConfig"],
  ["HrmsProperties", "PeopleProperties"],
]);
replaceInTree(path.join(ROOT, "leave-service"), [
  ["HrmsConfig", "LeaveAppConfig"],
  ["HrmsProperties", "LeaveProperties"],
]);
replaceInTree(path.join(ROOT, "time-work-service"), [
  ["HrmsConfig", "TimeWorkAppConfig"],
  ["HrmsProperties", "TimeWorkProperties"],
]);

write(
  path.join(ROOT, "people-service/src/main/java/com/platform/people/PeopleApplication.java"),
  `package com.platform.people;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * People service — employees, org, RBAC, employment (guide §3).
 * Own DB: people_&lt;tenant&gt;. Cross-service data via HTTP / RabbitMQ only.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ComponentScan(basePackages = {"com.platform.people", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.people", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.people", "com.platform.messaging.outbox"})
@EnableScheduling
public class PeopleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PeopleApplication.class, args);
    }
}
`
);
write(
  path.join(ROOT, "leave-service/src/main/java/com/platform/leave/LeaveServiceApplication.java"),
  `package com.platform.leave;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Leave service — policies, approvals, balances, ledger (guide §3).
 * Own DB: leave_&lt;tenant&gt;. Employee/RBAC via People HTTP; schedules via Time&amp;Work HTTP.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ComponentScan(basePackages = {"com.platform.leave", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.leave", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.leave", "com.platform.messaging.outbox"})
@EnableScheduling
public class LeaveServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LeaveServiceApplication.class, args);
    }
}
`
);
write(
  path.join(ROOT, "time-work-service/src/main/java/com/platform/timework/TimeWorkApplication.java"),
  `package com.platform.timework;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Time &amp; Work — schedules, attendance, tasks, work logs (guide §3).
 * Own DB: timework_&lt;tenant&gt;. Employee/RBAC via People HTTP; leave facts via Leave HTTP.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ComponentScan(basePackages = {"com.platform.timework", "com.platform.messaging"})
@EntityScan(basePackages = {"com.platform.timework", "com.platform.messaging.outbox"})
@EnableJpaRepositories(basePackages = {"com.platform.timework", "com.platform.messaging.outbox"})
@EnableScheduling
public class TimeWorkApplication {

    public static void main(String[] args) {
        SpringApplication.run(TimeWorkApplication.class, args);
    }
}
`
);

for (const [yml, pkg] of [
  ["people-service/src/main/resources/application.yml", "com.platform.people"],
  ["leave-service/src/main/resources/application.yml", "com.platform.leave"],
  ["time-work-service/src/main/resources/application.yml", "com.platform.timework"],
]) {
  const p = path.join(ROOT, yml);
  let text = read(p);
  text = text.replace(/com\.platform\.(hrms|people|leave|timework):\s*INFO/g, `${pkg}: INFO`);
  write(p, text);
}

const orphan = path.join(
  ROOT,
  "people-service/src/main/java/com/platform/people/extension/engine/WorkingDaysCalculator.java"
);
if (exists(orphan)) {
  fs.unlinkSync(orphan);
  console.log("Deleted people orphan WorkingDaysCalculator");
}

for (const d of [
  "leave-service/src/main/java/com/platform/leave/api/grpc",
  "leave-service/src/main/java/com/platform/leave/infrastructure/persistence/rbac",
  "time-work-service/src/main/java/com/platform/timework/api/grpc",
  "time-work-service/src/main/java/com/platform/timework/infrastructure/persistence/rbac",
  "people-service/src/main/java/com/platform/hrms",
  "leave-service/src/main/java/com/platform/hrms",
  "time-work-service/src/main/java/com/platform/hrms",
]) {
  const p = path.join(ROOT, d);
  if (exists(p)) fs.rmSync(p, { recursive: true, force: true });
}

console.log("DONE");
