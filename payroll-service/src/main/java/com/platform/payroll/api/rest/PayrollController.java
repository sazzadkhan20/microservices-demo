package com.platform.payroll.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.common.security.AuthContext;
import com.platform.payroll.api.dto.CalculatePayrollRequest;
import com.platform.payroll.api.dto.CalculatePayrollResponse;
import com.platform.payroll.application.PayrollCalculationService;
import com.platform.payroll.domain.PayrollConfigEntity;
import com.platform.payroll.domain.Payslip;
import com.platform.payroll.domain.SalaryHistory;
import com.platform.payroll.extension.config.PayrollConfigLoader;
import com.platform.payroll.infrastructure.persistence.PayrollConfigRepository;
import com.platform.payroll.infrastructure.persistence.PayslipRepository;
import com.platform.payroll.infrastructure.persistence.SalaryHistoryRepository;
import com.platform.payroll.security.PermissionSet;
import com.platform.payroll.security.RbacInterceptor;
import com.platform.payroll.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RBAC (permissions resolved from HRMS, enforced by {@code RbacInterceptor}):
 * <ul>
 *   <li>{@code payroll:run} — calculate / finalize / reopen, and read any employee's payslips;</li>
 *   <li>{@code payroll:view} — read payslips, but only the caller's own (employee id comes from the
 *       gateway-verified JWT, never from the request).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Payroll", description = "Reusable payroll calculation (tenant-routed)")
public class PayrollController {

    private final PayrollCalculationService calculationService;
    private final PayrollConfigLoader configLoader;
    private final PayslipRepository payslipRepository;
    private final SalaryHistoryRepository salaryHistoryRepository;
    private final PayrollConfigRepository payrollConfigRepository;

    public PayrollController(
            PayrollCalculationService calculationService,
            PayrollConfigLoader configLoader,
            PayslipRepository payslipRepository,
            SalaryHistoryRepository salaryHistoryRepository,
            PayrollConfigRepository payrollConfigRepository) {
        this.calculationService = calculationService;
        this.configLoader = configLoader;
        this.payslipRepository = payslipRepository;
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.payrollConfigRepository = payrollConfigRepository;
    }

    @PostMapping("/calculate")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Calculate payslip using HRMS employee + tenant payroll config (idempotent per period) — payroll:run")
    public CalculatePayrollResponse calculate(@Valid @RequestBody CalculatePayrollRequest request) {
        return calculationService.calculate(request);
    }

    @PostMapping("/payslips/{id}/finalize")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Finalize (lock) a draft payslip — payroll:run. Body: {confirm:true} required. Month must have ended.")
    public CalculatePayrollResponse finalize(@PathVariable Long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        boolean confirm = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("confirm", "false")));
        return calculationService.finalize(id, confirm);
    }

    @PostMapping("/payslips/{id}/reopen")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Reopen a finalized payslip for recalculation — payroll:run")
    public CalculatePayrollResponse reopen(@PathVariable Long id) {
        return calculationService.reopen(id);
    }

    @PostMapping("/calculate-month")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Calculate payslips for all active employees in a period — payroll:run")
    public Map<String, Object> calculateMonth(@RequestParam String period) {
        return calculationService.calculateMonth(period);
    }

    @GetMapping("/warnings")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "Warn about unfinalized payslips from previous period — payroll:view")
    public Map<String, Object> warnings(@RequestParam String period) {
        return calculationService.warnings(period);
    }

    @GetMapping("/export.csv")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Export payslips as CSV for a period — payroll:run")
    public ResponseEntity<String> exportCsv(
            @RequestParam String period,
            @RequestParam(defaultValue = "false") boolean finalizedOnly) {
        String csv = calculationService.exportCsv(period, finalizedOnly);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"payroll-" + period + ".csv\"");
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/me/current")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "Current pay, finalized payslips, and MTD estimate for calling employee — payroll:view")
    public Map<String, Object> meCurrent() {
        return calculationService.meEstimate();
    }

    @GetMapping("/config")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "View current tenant payroll config (platform defaults until customized) — payroll:view")
    public Map<String, Object> config() {
        var cfg = configLoader.loadOrDefault();
        return Map.of(
                "overtimeMultiplier", cfg.getOvertimeMultiplier(),
                "weekendMultiplier", cfg.getWeekendMultiplier(),
                "deductionRules", cfg.getDeductionRules(),
                "source", "tenant_db.payroll_config"
        );
    }

    @PutMapping("/config")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Update tenant payroll rules (config-driven multipliers + deductionRules JSON)")
    public Map<String, Object> updateConfig(@RequestBody Map<String, Object> body) {
        PayrollConfigEntity cfg = payrollConfigRepository.findAll().stream().findFirst().orElseGet(() -> {
            PayrollConfigEntity created = new PayrollConfigEntity();
            created.setOvertimeMultiplier(new BigDecimal("1.5000"));
            created.setWeekendMultiplier(new BigDecimal("2.0000"));
            created.setDeductionRules(List.of());
            return created;
        });
        if (body.get("overtimeMultiplier") != null) {
            cfg.setOvertimeMultiplier(new BigDecimal(String.valueOf(body.get("overtimeMultiplier"))));
        }
        if (body.get("weekendMultiplier") != null) {
            cfg.setWeekendMultiplier(new BigDecimal(String.valueOf(body.get("weekendMultiplier"))));
        }
        if (body.get("deductionRules") instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) list;
            cfg.setDeductionRules(rules);
        }
        cfg.setUpdatedAt(Instant.now());
        payrollConfigRepository.save(cfg);
        return config();
    }

    @GetMapping("/salary-history/employee/{employeeId}")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "Salary history — own with payroll/salary:view; any employee with salary:edit or payroll:run")
    public List<Map<String, Object>> salaryHistory(
            @PathVariable Long employeeId, HttpServletRequest http) {
        if (!canManageSalary(http) && !employeeId.equals(requireOwnEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: salary:edit or payroll:run required to view other employees' salary history");
        }
        return salaryHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId).stream()
                .map(this::salaryRow)
                .toList();
    }

    @PostMapping("/salary-history")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "Record a salary change (salary:edit or payroll:run)")
    public Map<String, Object> addSalaryHistory(@RequestBody Map<String, Object> body, HttpServletRequest http) {
        if (!canManageSalary(http)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: salary:edit or payroll:run required to record salary changes");
        }
        Long employeeId = Long.valueOf(String.valueOf(body.get("employeeId")));
        LocalDate effective = LocalDate.parse(String.valueOf(body.get("effectiveDate")));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String payType = String.valueOf(body.getOrDefault("payType", "MONTHLY")).toUpperCase(Locale.ROOT);
        String currency = String.valueOf(body.getOrDefault("currency", "BDT"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        SalaryHistory row = new SalaryHistory();
        row.setEmployeeId(employeeId);
        row.setEffectiveDate(effective);
        row.setAmount(amount);
        row.setPayType(payType);
        row.setCurrency(currency);
        row.setNote(note);
        row.setCreatedBy(AuthContext.getEmployeeId());
        row.setCreatedAt(Instant.now());
        return salaryRow(salaryHistoryRepository.save(row));
    }

    private Map<String, Object> salaryRow(SalaryHistory h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("employeeId", h.getEmployeeId());
        m.put("effectiveDate", h.getEffectiveDate() == null ? null : h.getEffectiveDate().toString());
        m.put("amount", h.getAmount());
        m.put("payType", h.getPayType());
        m.put("currency", h.getCurrency());
        m.put("note", h.getNote());
        m.put("createdBy", h.getCreatedBy());
        m.put("createdAt", h.getCreatedAt() == null ? null : h.getCreatedAt().toString());
        return m;
    }

    @GetMapping("/payslips")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "List payslips (page/size/q/filter.periodLabel/filter.employeeId) — payroll:view; "
            + "callers without payroll:run only get their own payslips")
    public PageResponse<Map<String, Object>> listPayslips(
            @RequestParam Map<String, String> params, HttpServletRequest http) {
        PageQuery query = PageQuery.fromParams(params);
        // Without payroll:run the caller only ever sees their own payslips (client employeeId is ignored).
        Long scopedEmployee = canRun(http) ? null : requireOwnEmployeeId();
        List<Payslip> all = scopedEmployee == null
                ? new ArrayList<>(payslipRepository.findAll())
                : new ArrayList<>(payslipRepository.findByEmployeeIdOrderByCreatedAtDesc(scopedEmployee));
        String period = query.filters().getOrDefault("periodLabel", params.get("periodLabel"));
        String emp = query.filters().getOrDefault("employeeId", params.get("employeeId"));
        if (period != null && !period.isBlank()) {
            all = all.stream().filter(p -> period.equalsIgnoreCase(p.getPeriodLabel())).collect(Collectors.toList());
        }
        if (scopedEmployee == null && emp != null && !emp.isBlank()) {
            Long id = Long.valueOf(emp.trim());
            all = all.stream().filter(p -> id.equals(p.getEmployeeId())).collect(Collectors.toList());
        }
        if (query.q() != null) {
            String q = query.q().toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(p -> (p.getPeriodLabel() != null && p.getPeriodLabel().toLowerCase(Locale.ROOT).contains(q))
                            || String.valueOf(p.getEmployeeId()).contains(q))
                    .collect(Collectors.toList());
        }
        all.sort(Comparator.comparing(Payslip::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        long total = all.size();
        int from = Math.min(query.offset(), all.size());
        int to = Math.min(from + query.size(), all.size());
        List<Map<String, Object>> content = all.subList(from, to).stream().map(this::toRow).toList();
        return PageResponse.of(content, query, total);
    }

    @GetMapping("/payslips/employee/{employeeId}")
    @RequiresPermission(feature = "payroll", action = "view")
    @Operation(summary = "Payslips for one employee — payroll:view (own only) or payroll:run (any)")
    public List<Map<String, Object>> employeePayslips(
            @PathVariable Long employeeId, HttpServletRequest http) {
        if (!canRun(http) && !employeeId.equals(requireOwnEmployeeId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: payroll:run required to view other employees' payslips");
        }
        return payslipRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::toRow)
                .toList();
    }

    /** True when the caller holds payroll:run (resolved by RbacInterceptor for this request). */
    private static boolean canRun(HttpServletRequest request) {
        return request.getAttribute(RbacInterceptor.PERMISSIONS_ATTR) instanceof PermissionSet p
                && p.has("payroll", "run");
    }

    /** HR salary:edit or payroll:run — manage compensation ledger for any employee. */
    private static boolean canManageSalary(HttpServletRequest request) {
        if (!(request.getAttribute(RbacInterceptor.PERMISSIONS_ATTR) instanceof PermissionSet p)) {
            return false;
        }
        return p.has("payroll", "run") || p.has("salary", "edit");
    }

    /** Employee id from the gateway-verified identity (never from request params/body). */
    private static Long requireOwnEmployeeId() {
        Long own = AuthContext.getEmployeeId();
        if (own == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing employee identity");
        }
        return own;
    }

    private Map<String, Object> toRow(Payslip p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("employeeId", p.getEmployeeId());
        m.put("periodLabel", p.getPeriodLabel());
        m.put("grossAmount", p.getGrossAmount());
        m.put("deductionAmount", p.getDeductionAmount());
        m.put("netAmount", p.getNetAmount());
        m.put("regularHours", p.getRegularHours());
        m.put("overtimeHours", p.getOvertimeHours());
        m.put("weekendHours", p.getWeekendHours());
        m.put("details", p.getDetailsJson());
        m.put("createdAt", p.getCreatedAt() == null ? null : p.getCreatedAt().toString());
        Object status = p.getDetailsJson() == null ? null : p.getDetailsJson().get("status");
        m.put("status", status == null ? "DRAFT" : status);
        return m;
    }
}
