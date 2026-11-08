package com.platform.timework.qii;

import com.platform.timework.application.AttendanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QiiAttendanceRulesUnitTest {

    private static Path src(String relativeUnderMainJava) {
        Path local = Path.of("src/main/java").resolve(relativeUnderMainJava);
        if (Files.exists(local)) {
            return local;
        }
        return Path.of("time-work-service/src/main/java").resolve(relativeUnderMainJava);
    }

    @Test
    @DisplayName("UT-ATT-01 AttendanceService must depend on work schedule")
    void attendance_must_use_work_schedule() {
        boolean usesSchedule = Stream.of(AttendanceService.class.getDeclaredFields())
                .anyMatch(f -> f.getType().getSimpleName().toLowerCase().contains("schedule")
                        || f.getName().toLowerCase().contains("schedule"));
        assertTrue(usesSchedule,
                "FAIL Q6: AttendanceService has no WorkSchedule dependency");
    }

    @Test
    @DisplayName("UT-ATT-02 Checkout must enforce workLogRule BLOCK")
    void checkout_must_read_work_log_rule() throws Exception {
        Path path = src("com/platform/timework/application/AttendanceService.java");
        String body = Files.readString(path);
        assertTrue(body.contains("workLog") || body.contains("work_log") || body.contains("WorkLog"),
                "FAIL Q6: checkOut ignores workLogRule");
    }

    @Test
    @DisplayName("UT-ATT-03 Attendance correction API must exist")
    void correction_endpoint_exists() throws Exception {
        try {
            Class.forName("com.platform.timework.api.rest.AttendanceCorrectionController");
        } catch (ClassNotFoundException e) {
            boolean hasCorrect = Arrays.stream(Class.forName("com.platform.timework.api.rest.AttendanceController")
                            .getDeclaredMethods())
                    .map(Method::getName)
                    .anyMatch(n -> n.toLowerCase().contains("correct"));
            assertTrue(hasCorrect, "FAIL SRS: no attendance correction flow");
        }
    }
}
