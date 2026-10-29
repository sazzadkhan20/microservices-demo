package com.platform.hrms.qii;

import com.platform.hrms.application.AttendanceService;
import com.platform.hrms.application.LeaveApplicationService;
import com.platform.hrms.application.RbacService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Specification-style unit checks for Qii Creative scope (QII_HRMS_PLAN / Q6).
 * Failures = missing product behavior — not optional polish.
 */
class QiiBusinessRulesUnitTest {

    @Nested
    @DisplayName("UT-ATT: Attendance + schedules + work-log BLOCK")
    class AttendanceRules {

        @Test
        @DisplayName("UT-ATT-01 AttendanceService must depend on work schedule (grace/LATE/HALF_DAY)")
        void attendance_must_use_work_schedule() {
            boolean usesSchedule = Stream.of(AttendanceService.class.getDeclaredFields())
                    .anyMatch(f -> f.getType().getSimpleName().toLowerCase().contains("schedule")
                            || f.getName().toLowerCase().contains("schedule"));
            assertTrue(usesSchedule,
                    "FAIL Q6: AttendanceService has no WorkSchedule dependency — grace_minutes=15 / LATE never applied");
        }

        @Test
        @DisplayName("UT-ATT-02 Checkout must enforce workLogRule BLOCK from tenant_settings")
        void checkout_must_read_work_log_rule() throws Exception {
            Path src = Path.of("src/main/java/com/platform/hrms/application/AttendanceService.java");
            String body = Files.readString(src);
            assertTrue(body.contains("workLog") || body.contains("work_log") || body.contains("WorkLog"),
                    "FAIL Q6: checkOut ignores workLogRule=BLOCK / workLogMinPercent");
        }

        @Test
        @DisplayName("UT-ATT-03 Attendance correction API must exist")
        void correction_endpoint_exists() throws Exception {
            try {
                Class.forName("com.platform.hrms.api.rest.AttendanceCorrectionController");
            } catch (ClassNotFoundException e) {
                boolean hasCorrect = Arrays.stream(Class.forName("com.platform.hrms.api.rest.AttendanceController")
                                .getDeclaredMethods())
                        .map(Method::getName)
                        .anyMatch(n -> n.toLowerCase().contains("correct"));
                assertTrue(hasCorrect,
                        "FAIL SRS: no attendance correction/approve flow on AttendanceController");
            }
        }
    }

    @Nested
    @DisplayName("UT-LEAVE: categories, team clash, half-day, attachments")
    class LeaveRules {

        @Test
        @DisplayName("UT-LEAVE-01 Leave engine must load leave_category (not only leave_type)")
        void leave_engine_uses_category() throws Exception {
            Path src = Path.of("src/main/java/com/platform/hrms/application/LeaveApplicationService.java");
            String body = Files.readString(src);
            assertTrue(body.contains("LeaveCategory") || body.contains("leave_category") || body.contains("leaveCategory"),
                    "FAIL: leave_category quota/halfDay/attachment/max_per_request/negative_cap never read");
        }

        @Test
        @DisplayName("UT-LEAVE-02 Team max_on_leave clash must be evaluated")
        void team_clash_checked() throws Exception {
            Path apply = Path.of("src/main/java/com/platform/hrms/application/LeaveApplicationService.java");
            Path clash = Path.of("src/main/java/com/platform/hrms/application/LeaveTeamClashService.java");
            String body = Files.readString(apply)
                    + (Files.exists(clash) ? Files.readString(clash) : "");
            assertTrue(body.contains("max_on_leave") || body.contains("maxOnLeave") || body.contains("TeamRepository")
                            || body.contains("teamClashService"),
                    "FAIL Q6: team leave clash ALERT max 1 not implemented");
        }

        @Test
        @DisplayName("UT-LEAVE-03 Apply must bind employeeId to AuthContext (no IDOR)")
        void apply_binds_auth_employee() throws Exception {
            Path src = Path.of("src/main/java/com/platform/hrms/application/LeaveApplicationService.java");
            String body = Files.readString(src);
            // crude: apply method should compare AuthContext when request employeeId present
            boolean guards = body.contains("AuthContext") && body.contains("requireEmployeeId");
            // LeaveFacadeController uses AuthContext; LeaveApplicationService.apply uses request.employeeId()
            Path applySrc = src;
            String apply = Files.readString(applySrc);
            boolean applyUsesRequestOnly = apply.contains("request.employeeId()")
                    && !apply.contains("AuthContext.requireEmployeeId()");
            assertFalse(applyUsesRequestOnly,
                    "FAIL SEC: LeaveApplicationService.apply trusts body employeeId — IDOR");
            assertTrue(guards || !applyUsesRequestOnly, "AuthContext must gate leave applicant identity");
        }

        @Test
        @DisplayName("UT-LEAVE-04 Half-day request field must exist on LeaveApplyRequest")
        void half_day_field() throws Exception {
            Class<?> req = Class.forName("com.platform.hrms.api.dto.LeaveApplyRequest");
            boolean half = Arrays.stream(req.getRecordComponents())
                    .anyMatch(c -> c.getName().toLowerCase().contains("half"));
            assertTrue(half, "FAIL: LeaveApplyRequest has no halfDay — category.half_day_allowed unused");
        }
    }

    @Nested
    @DisplayName("UT-RBAC: server enforcement required by plan")
    class RbacRules {

        @Test
        @DisplayName("UT-RBAC-01 RequiresPermission annotation or interceptor must exist")
        void permission_guard_exists() {
            boolean ann;
            try {
                Class.forName("com.platform.hrms.security.RequiresPermission");
                ann = true;
            } catch (ClassNotFoundException e) {
                ann = false;
            }
            boolean interceptor;
            try {
                Class.forName("com.platform.hrms.security.RbacInterceptor");
                interceptor = true;
            } catch (ClassNotFoundException e) {
                interceptor = false;
            }
            assertTrue(ann || interceptor,
                    "FAIL PLAN: RBAC 'always enforce on BE' — no @RequiresPermission / RbacInterceptor");
        }

        @Test
        @DisplayName("UT-RBAC-02 RbacService.hasPermission must be referenced from api.rest package")
        void controllers_call_has_permission() throws Exception {
            Path rest = Path.of("src/main/java/com/platform/hrms/api/rest");
            String joined = Files.walk(rest)
                    .filter(p -> p.toString().endsWith(".java"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .reduce("", (a, b) -> a + "\n" + b);
            assertTrue(joined.contains("hasPermission") || joined.contains("RequiresPermission"),
                    "FAIL: no controller references hasPermission — RBAC is FE-only");
            // silence unused
            assertTrue(RbacService.class.getMethods().length > 0);
        }
    }

    @Nested
    @DisplayName("UT-AUDIT / notifications / payroll product hooks")
    class CrossCutting {

        @Test
        @DisplayName("UT-AUD-01 AuditLog writer must exist")
        void audit_writer() {
            try {
                Class.forName("com.platform.hrms.application.AuditService");
            } catch (ClassNotFoundException e) {
                fail("FAIL PLAN: audit_log table exists but AuditService missing — mutations untracked");
            }
        }

        @Test
        @DisplayName("UT-PAY-01 Leave approve must emit notification hook")
        void leave_approve_notifies() throws Exception {
            Path src = Path.of("src/main/java/com/platform/hrms/application/LeaveApplicationService.java");
            String body = Files.readString(src);
            assertTrue(body.toLowerCase().contains("notif"),
                    "FAIL: leave approve never notifies notification-service");
        }

        @Test
        @DisplayName("UT-LEAVE-SVC LeaveApplicationService size budget (SRP)")
        void leave_service_not_god_class() throws Exception {
            long lines = Files.lines(Path.of("src/main/java/com/platform/hrms/application/LeaveApplicationService.java")).count();
            assertTrue(lines <= 600,
                    "FAIL CODE: LeaveApplicationService is " + lines + " lines (god class / hard to unit-test)");
            assertTrue(LeaveApplicationService.class.getDeclaredMethods().length > 0);
        }
    }
}
