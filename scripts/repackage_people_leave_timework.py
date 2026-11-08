#!/usr/bin/env python3
"""
Repackage people / leave / time-work off legacy com.platform.hrms into
service-owned roots, and move remote adapters under client.* packages.
"""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def write(p: Path, text: str) -> None:
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8", newline="\n")


def package_of(rel_under_java: Path) -> str:
    return ".".join(rel_under_java.parent.parts)


def rewrite_packages_and_imports(text: str, old: str, new: str, protect_grpc: bool = False) -> str:
    if protect_grpc:
        text = text.replace("com.platform.hrms.api.grpc", "__KEEP_HRMS_GRPC__")
    text = text.replace(old, new)
    if protect_grpc:
        text = text.replace("__KEEP_HRMS_GRPC__", "com.platform.hrms.api.grpc")
    return text


def move_tree(src_root: Path, dst_root: Path, old_pkg: str, new_pkg: str, protect_grpc: bool = False) -> list[Path]:
    moved: list[Path] = []
    if not src_root.exists():
        return moved
    for src in sorted(src_root.rglob("*.java")):
        rel = src.relative_to(src_root)
        dst = dst_root / rel
        text = read(src)
        text = rewrite_packages_and_imports(text, old_pkg, new_pkg, protect_grpc=protect_grpc)
        # Force package line from destination path
        java_root = dst_root
        # dst is under .../java/com/platform/<svc>/...
        # Find 'java' ancestor
        parts = dst.parts
        java_idx = parts.index("java")
        pkg_path = Path(*parts[java_idx + 1 : -1])
        pkg = package_of(Path(*pkg_path.parts) / "Dummy.java")
        text = re.sub(r"^package\s+[\w.]+;", f"package {pkg};", text, count=1, flags=re.M)
        write(dst, text)
        src.unlink()
        moved.append(dst)
    # cleanup empty dirs under src_root
    if src_root.exists():
        for d in sorted(src_root.rglob("*"), reverse=True):
            if d.is_dir():
                try:
                    d.rmdir()
                except OSError:
                    pass
        try:
            src_root.rmdir()
        except OSError:
            pass
    return moved


def replace_in_file(path: Path, mapping: list[tuple[str, str]]) -> None:
    if not path.exists():
        return
    text = read(path)
    orig = text
    for a, b in mapping:
        text = text.replace(a, b)
    if text != orig:
        write(path, text)


def replace_tree(base: Path, mapping: list[tuple[str, str]], globs: tuple[str, ...] = ("*.java", "*.yml")) -> None:
    for pattern in globs:
        for path in base.rglob(pattern):
            replace_in_file(path, mapping)


def relocate_file(src: Path, dst: Path, class_renames: list[tuple[str, str]] | None = None) -> None:
    text = read(src)
    pkg = package_of(dst.relative_to(dst.parents[len(dst.parts) - dst.parts.index("java") - 1]))
    # compute package from path after java/
    parts = list(dst.parts)
    java_idx = parts.index("java")
    pkg = ".".join(parts[java_idx + 1 : -1])
    text = re.sub(r"^package\s+[\w.]+;", f"package {pkg};", text, count=1, flags=re.M)
    if class_renames:
        for old, new in class_renames:
            text = re.sub(rf"\b{re.escape(old)}\b", new, text)
    write(dst, text)
    src.unlink()


def main() -> None:
    services = [
        {
            "name": "people-service",
            "svc_pkg": "com.platform.people",
            "svc_dir": "people",
            "protect_grpc": True,
        },
        {
            "name": "leave-service",
            "svc_pkg": "com.platform.leave",
            "svc_dir": "leave",
            "protect_grpc": False,
        },
        {
            "name": "time-work-service",
            "svc_pkg": "com.platform.timework",
            "svc_dir": "timework",
            "protect_grpc": False,
        },
    ]

    for svc in services:
        base = ROOT / svc["name"]
        java_main = base / "src/main/java"
        java_test = base / "src/test/java"
        old_root = java_main / "com/platform/hrms"
        new_root = java_main / "com/platform" / svc["svc_dir"]
        print(f"== Moving main {svc['name']}: hrms -> {svc['svc_dir']}")
        move_tree(old_root, new_root, "com.platform.hrms", svc["svc_pkg"], protect_grpc=svc["protect_grpc"])

        old_test = java_test / "com/platform/hrms"
        new_test = java_test / "com/platform" / svc["svc_dir"]
        if old_test.exists():
            print(f"== Moving test {svc['name']}")
            move_tree(old_test, new_test, "com.platform.hrms", svc["svc_pkg"], protect_grpc=svc["protect_grpc"])

        # Fix any remaining hrms refs in resources / already-owned packages
        replace_tree(
            base,
            [
                ("com.platform.hrms", svc["svc_pkg"]),
            ],
        )
        if svc["protect_grpc"]:
            # restore generated grpc package accidentally rewritten in yml/etc
            replace_tree(
                base,
                [("com.platform.people.api.grpc", "com.platform.hrms.api.grpc")],
                globs=("*.java",),
            )
            # but hand-written grpc classes must stay under people.api.grpc package
            for grpc_file in (new_root / "api/grpc").glob("*.java") if (new_root / "api/grpc").exists() else []:
                text = read(grpc_file)
                text = re.sub(
                    r"^package\s+com\.platform\.hrms\.api\.grpc;",
                    "package com.platform.people.api.grpc;",
                    text,
                    count=1,
                    flags=re.M,
                )
                # imports of generated types stay hrms
                write(grpc_file, text)

    # --- Leave: relocate People / Time&Work adapters under client.* ---
    leave = ROOT / "leave-service/src/main/java/com/platform/leave"
    print("== Leave: relocate remote adapters")
    leave_moves = [
        (
            leave / "domain/Employee.java",
            leave / "client/people/EmployeeView.java",
            [("Employee", "EmployeeView")],
        ),
        (
            leave / "infrastructure/persistence/EmployeeRepository.java",
            leave / "client/people/PeopleEmployeeGateway.java",
            [("EmployeeRepository", "PeopleEmployeeGateway"), ("Employee", "EmployeeView")],
        ),
        (
            leave / "application/RbacService.java",
            leave / "client/people/PeopleAuthzClient.java",
            [("RbacService", "PeopleAuthzClient")],
        ),
        (
            leave / "domain/WorkSchedule.java",
            leave / "client/timework/ScheduleView.java",
            [("WorkSchedule", "ScheduleView")],
        ),
        (
            leave / "application/WorkScheduleService.java",
            leave / "client/timework/TimeWorkScheduleGateway.java",
            [
                ("WorkScheduleService", "TimeWorkScheduleGateway"),
                ("WorkSchedule", "ScheduleView"),
                ("Employee", "EmployeeView"),
            ],
        ),
    ]
    for src, dst, renames in leave_moves:
        if src.exists():
            relocate_file(src, dst, renames)

    # Update leave imports/usages for relocated types
    leave_replacements = [
        ("com.platform.leave.domain.Employee", "com.platform.leave.client.people.EmployeeView"),
        ("com.platform.leave.infrastructure.persistence.EmployeeRepository", "com.platform.leave.client.people.PeopleEmployeeGateway"),
        ("com.platform.leave.application.RbacService", "com.platform.leave.client.people.PeopleAuthzClient"),
        ("com.platform.leave.domain.WorkSchedule", "com.platform.leave.client.timework.ScheduleView"),
        ("com.platform.leave.application.WorkScheduleService", "com.platform.leave.client.timework.TimeWorkScheduleGateway"),
        ("EmployeeRepository", "PeopleEmployeeGateway"),
        ("RbacService", "PeopleAuthzClient"),
        ("WorkScheduleService", "TimeWorkScheduleGateway"),
    ]
    # Careful with bare Employee / WorkSchedule — only in import and type contexts after package move.
    # Apply FQCN and class-name renames across leave-service java.
    for path in (ROOT / "leave-service").rglob("*.java"):
        text = read(path)
        orig = text
        for a, b in leave_replacements:
            text = text.replace(a, b)
        # Rename remaining Employee type refs that were domain Employee view
        # Avoid touching strings/comments carelessly: replace common patterns
        text = re.sub(r"\bEmployee\b", "EmployeeView", text)
        text = re.sub(r"\bWorkSchedule\b", "ScheduleView", text)
        # Undo accidental renames of unrelated words if any (EmployeeViewView)
        text = text.replace("EmployeeViewView", "EmployeeView")
        text = text.replace("ScheduleViewView", "ScheduleView")
        # PeopleAuthzClientAuthzClient etc.
        text = text.replace("PeopleAuthzClientAuthzClient", "PeopleAuthzClient")
        text = text.replace("PeopleEmployeeGatewayEmployeeGateway", "PeopleEmployeeGateway")
        text = text.replace("TimeWorkScheduleGatewayScheduleGateway", "TimeWorkScheduleGateway")
        if text != orig:
            write(path, text)

    # --- Time&Work: relocate People adapters ---
    tw = ROOT / "time-work-service/src/main/java/com/platform/timework"
    print("== TimeWork: relocate remote adapters")
    tw_moves = [
        (
            tw / "domain/Employee.java",
            tw / "client/people/EmployeeView.java",
            [("Employee", "EmployeeView")],
        ),
        (
            tw / "infrastructure/persistence/EmployeeRepository.java",
            tw / "client/people/PeopleEmployeeGateway.java",
            [("EmployeeRepository", "PeopleEmployeeGateway"), ("Employee", "EmployeeView")],
        ),
        (
            tw / "application/RbacService.java",
            tw / "client/people/PeopleAuthzClient.java",
            [("RbacService", "PeopleAuthzClient")],
        ),
    ]
    for src, dst, renames in tw_moves:
        if src.exists():
            relocate_file(src, dst, renames)

    tw_replacements = [
        ("com.platform.timework.domain.Employee", "com.platform.timework.client.people.EmployeeView"),
        ("com.platform.timework.infrastructure.persistence.EmployeeRepository", "com.platform.timework.client.people.PeopleEmployeeGateway"),
        ("com.platform.timework.application.RbacService", "com.platform.timework.client.people.PeopleAuthzClient"),
        ("EmployeeRepository", "PeopleEmployeeGateway"),
        ("RbacService", "PeopleAuthzClient"),
    ]
    for path in (ROOT / "time-work-service").rglob("*.java"):
        text = read(path)
        orig = text
        for a, b in tw_replacements:
            text = text.replace(a, b)
        text = re.sub(r"\bEmployee\b", "EmployeeView", text)
        text = text.replace("EmployeeViewView", "EmployeeView")
        text = text.replace("PeopleAuthzClientAuthzClient", "PeopleAuthzClient")
        text = text.replace("PeopleEmployeeGatewayEmployeeGateway", "PeopleEmployeeGateway")
        if text != orig:
            write(path, text)

    # --- Rename HrmsConfig / HrmsProperties per service ---
    renames_cfg = [
        (
            ROOT / "people-service/src/main/java/com/platform/people/config/HrmsConfig.java",
            ROOT / "people-service/src/main/java/com/platform/people/config/PeopleConfig.java",
            "HrmsConfig",
            "PeopleConfig",
            "com.platform.people",
        ),
        (
            ROOT / "people-service/src/main/java/com/platform/people/config/HrmsProperties.java",
            ROOT / "people-service/src/main/java/com/platform/people/config/PeopleProperties.java",
            "HrmsProperties",
            "PeopleProperties",
            "com.platform.people",
        ),
        (
            ROOT / "leave-service/src/main/java/com/platform/leave/config/HrmsConfig.java",
            ROOT / "leave-service/src/main/java/com/platform/leave/config/LeaveConfig.java",
            "HrmsConfig",
            "LeaveConfig",
            "com.platform.leave",
        ),
        (
            ROOT / "leave-service/src/main/java/com/platform/leave/config/HrmsProperties.java",
            ROOT / "leave-service/src/main/java/com/platform/leave/config/LeaveProperties.java",
            "HrmsProperties",
            "LeaveProperties",
            "com.platform.leave",
        ),
        (
            ROOT / "time-work-service/src/main/java/com/platform/timework/config/HrmsConfig.java",
            ROOT / "time-work-service/src/main/java/com/platform/timework/config/TimeWorkConfig.java",
            "HrmsConfig",
            "TimeWorkConfig",
            "com.platform.timework",
        ),
        (
            ROOT / "time-work-service/src/main/java/com/platform/timework/config/HrmsProperties.java",
            ROOT / "time-work-service/src/main/java/com/platform/timework/config/TimeWorkProperties.java",
            "HrmsProperties",
            "TimeWorkProperties",
            "com.platform.timework",
        ),
    ]
    for src, dst, old_c, new_c, pkg_root in renames_cfg:
        if not src.exists():
            continue
        text = read(src)
        text = text.replace(old_c, new_c)
        parts = list(dst.parts)
        java_idx = parts.index("java")
        pkg = ".".join(parts[java_idx + 1 : -1])
        text = re.sub(r"^package\s+[\w.]+;", f"package {pkg};", text, count=1, flags=re.M)
        write(dst, text)
        if src != dst:
            src.unlink()
        replace_tree(ROOT / pkg_root.split(".")[-1].replace("timework", "time-work-service") if False else ROOT, [])

    # Fix config class references in each service
    replace_tree(ROOT / "people-service", [("HrmsConfig", "PeopleConfig"), ("HrmsProperties", "PeopleProperties")])
    replace_tree(ROOT / "leave-service", [("HrmsConfig", "LeaveConfig"), ("HrmsProperties", "LeaveProperties")])
    replace_tree(ROOT / "time-work-service", [("HrmsConfig", "TimeWorkConfig"), ("HrmsProperties", "TimeWorkProperties")])

    # --- Application scan bases ---
    write(
        ROOT / "people-service/src/main/java/com/platform/people/PeopleApplication.java",
        """package com.platform.people;

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
""",
    )
    write(
        ROOT / "leave-service/src/main/java/com/platform/leave/LeaveServiceApplication.java",
        """package com.platform.leave;

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
""",
    )
    write(
        ROOT / "time-work-service/src/main/java/com/platform/timework/TimeWorkApplication.java",
        """package com.platform.timework;

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
""",
    )

    # logging package names
    for yml, pkg in [
        (ROOT / "people-service/src/main/resources/application.yml", "com.platform.people"),
        (ROOT / "leave-service/src/main/resources/application.yml", "com.platform.leave"),
        (ROOT / "time-work-service/src/main/resources/application.yml", "com.platform.timework"),
    ]:
        replace_in_file(yml, [("com.platform.hrms", pkg), ("com.platform.people", pkg) if "people" in str(yml) else ("com.platform.hrms", pkg)])
        # ensure correct logging key
        text = read(yml)
        text = re.sub(r"com\.platform\.(hrms|people|leave|timework): INFO", f"{pkg}: INFO", text)
        write(yml, text)

    # Delete people orphan WorkingDaysCalculator if present
    orphan = ROOT / "people-service/src/main/java/com/platform/people/extension/engine/WorkingDaysCalculator.java"
    if orphan.exists():
        orphan.unlink()
        print("Deleted people orphan WorkingDaysCalculator")

    # Remove empty leftover dirs
    for empty in [
        ROOT / "leave-service/src/main/java/com/platform/leave/api/grpc",
        ROOT / "leave-service/src/main/java/com/platform/leave/infrastructure/persistence/rbac",
        ROOT / "time-work-service/src/main/java/com/platform/timework/api/grpc",
        ROOT / "time-work-service/src/main/java/com/platform/timework/infrastructure/persistence/rbac",
        ROOT / "people-service/src/main/java/com/platform/hrms",
        ROOT / "leave-service/src/main/java/com/platform/hrms",
        ROOT / "time-work-service/src/main/java/com/platform/hrms",
    ]:
        if empty.exists():
            shutil.rmtree(empty, ignore_errors=True)

    print("DONE")


if __name__ == "__main__":
    main()
