package com.platform.leave.qii;

import com.platform.leave.application.LeaveApplicationService;
import com.platform.leave.client.people.PeopleAuthzClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Leave-owned Qii specification checks (attendance checks live in time-work-service).
 */
class QiiBusinessRulesUnitTest {

    private static Path src(String relativeUnderMainJava) {
        Path local = Path.of("src/main/java").resolve(relativeUnderMainJava);
        if (Files.exists(local)) {
            return local;
        }
        return Path.of("leave-service/src/main/java").resolve(relativeUnderMainJava);
    }

    @Nested
    @DisplayName("UT-LEAVE: categories, team clash, half-day, attachments")
    class LeaveRules {

        @Test
        @DisplayName("UT-LEAVE-01 Leave engine must load leave_category (not only leave_type)")
        void leave_engine_uses_category() throws Exception {
            String body = Files.readString(src("com/platform/leave/application/LeaveApplicationService.java"));
            assertTrue(body.contains("LeaveCategory") || body.contains("leave_category") || body.contains("leaveCategory"),
                    "FAIL: leave_category quota/halfDay/attachment/max_per_request/negative_cap never read");
        }

        @Test
        @DisplayName("UT-LEAVE-02 Team max_on_leave clash must be evaluated")
        void team_clash_checked() throws Exception {
            Path apply = src("com/platform/leave/application/LeaveApplicationService.java");
            Path clash = src("com/platform/leave/application/LeaveTeamClashService.java");
            String body = Files.readString(apply)
                    + (Files.exists(clash) ? Files.readString(clash) : "");
            assertTrue(body.contains("max_on_leave") || body.contains("maxOnLeave") || body.contains("TeamRepository")
                            || body.contains("teamClashService"),
                    "FAIL Q6: team leave clash ALERT max 1 not implemented");
        }

        @Test
        @DisplayName("UT-LEAVE-03 Apply must bind employeeId to AuthContext (no IDOR)")
        void apply_binds_auth_employee() throws Exception {
            Path path = src("com/platform/leave/application/LeaveApplicationService.java");
            String body = Files.readString(path);
            boolean guards = body.contains("AuthContext") && body.contains("requireEmployeeId");
            boolean applyUsesRequestOnly = body.contains("request.employeeId()")
                    && !body.contains("AuthContext.requireEmployeeId()");
            assertFalse(applyUsesRequestOnly,
                    "FAIL SEC: LeaveApplicationService.apply trusts body employeeId — IDOR");
            assertTrue(guards || !applyUsesRequestOnly, "AuthContext must gate leave applicant identity");
        }

        @Test
        @DisplayName("UT-LEAVE-04 Half-day request field must exist on LeaveApplyRequest")
        void half_day_field() throws Exception {
            Class<?> req = Class.forName("com.platform.leave.api.dto.LeaveApplyRequest");
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
                Class.forName("com.platform.leave.security.RequiresPermission");
                ann = true;
            } catch (ClassNotFoundException e) {
                ann = false;
            }
            boolean interceptor;
            try {
                Class.forName("com.platform.leave.security.RbacInterceptor");
                interceptor = true;
            } catch (ClassNotFoundException e) {
                interceptor = false;
            }
            assertTrue(ann || interceptor,
                    "FAIL PLAN: RBAC 'always enforce on BE' — no @RequiresPermission / RbacInterceptor");
        }

        @Test
        @DisplayName("UT-RBAC-02 PeopleAuthzClient.hasPermission must be referenced from api.rest package")
        void controllers_call_has_permission() throws Exception {
            Path rest = src("com/platform/leave/api/rest");
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
            assertTrue(PeopleAuthzClient.class.getMethods().length > 0);
        }
    }

    @Nested
    @DisplayName("UT-AUDIT / notifications / payroll product hooks")
    class CrossCutting {

        @Test
        @DisplayName("UT-AUD-01 AuditLog writer must exist")
        void audit_writer() {
            try {
                Class.forName("com.platform.leave.application.AuditService");
            } catch (ClassNotFoundException e) {
                fail("FAIL PLAN: audit_log table exists but AuditService missing — mutations untracked");
            }
        }

        @Test
        @DisplayName("UT-PAY-01 Leave approve must emit notification hook")
        void leave_approve_notifies() throws Exception {
            String body = Files.readString(src("com/platform/leave/application/LeaveApplicationService.java"));
            assertTrue(body.toLowerCase().contains("notif"),
                    "FAIL: leave approve never notifies notification-service");
        }

        @Test
        @DisplayName("UT-LEAVE-SVC LeaveApplicationService size budget (SRP)")
        void leave_service_not_god_class() throws Exception {
            long lines = Files.lines(src("com/platform/leave/application/LeaveApplicationService.java")).count();
            assertTrue(lines <= 1350,
                    "FAIL CODE: LeaveApplicationService is " + lines + " lines (god class / hard to unit-test)");
            assertTrue(LeaveApplicationService.class.getDeclaredMethods().length > 0);
        }
    }
}
