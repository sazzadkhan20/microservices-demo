package com.platform.payroll.qii;

import com.platform.payroll.extension.engine.DeductionRuleEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class QiiPayrollRulesUnitTest {

    @Test
    @DisplayName("UT-PAY-02 DeductionRuleEngine must implement absenceCoverOrder Casual→Annual")
    void absence_cover_order() throws Exception {
        Path src = Path.of("src/main/java/com/platform/payroll/extension/engine/DeductionRuleEngine.java");
        String body = Files.readString(src);
        assertTrue(body.contains("absenceCover") || body.contains("CASUAL") || body.contains("coverOrder"),
                "FAIL Q6: payrollAbsenceCoverOrder Casual→Annual not implemented in DeductionRuleEngine");
    }

    @Test
    @DisplayName("UT-PAY-03 Payslip finalize + reopen APIs must exist")
    void finalize_reopen() {
        boolean finalize = false;
        boolean reopen = false;
        try {
            Class<?> ctrl = Class.forName("com.platform.payroll.api.rest.PayrollController");
            for (Method m : ctrl.getDeclaredMethods()) {
                String n = m.getName().toLowerCase();
                if (n.contains("finalize")) finalize = true;
                if (n.contains("reopen")) reopen = true;
            }
        } catch (ClassNotFoundException e) {
            fail("PayrollController missing");
        }
        assertTrue(finalize && reopen,
                "FAIL PLAN: payslip finalize/reopen not exposed — only calculate exists (finalize="
                        + finalize + ", reopen=" + reopen + ")");
        assertTrue(DeductionRuleEngine.class.getDeclaredMethods().length > 0);
    }

    @Test
    @DisplayName("UT-PAY-04 Unique payslip period constraint / idempotency check in service")
    void calculate_idempotent() throws Exception {
        Path src = Path.of("src/main/java/com/platform/payroll/application/PayrollCalculationService.java");
        String body = Files.readString(src);
        boolean guards = body.contains("findBy") && (body.contains("period") || body.contains("Period"));
        assertTrue(guards || body.toLowerCase().contains("idempot"),
                "FAIL: calculate() always saves — duplicate payslips on double-submit");
        // list methods for noise
        assertTrue(Arrays.stream(Class.forName("com.platform.payroll.application.PayrollCalculationService")
                .getDeclaredMethods()).count() > 0);
    }
}
