package com.platform.payroll.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.payroll.application.PayrollFinalizeService;
import com.platform.payroll.application.PayrollRunService;
import com.platform.payroll.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Monthly payroll runs (docs/PAYROLL_SPEC.md §2.4). All endpoints need {@code payroll:run} unless noted. */
@RestController
@RequestMapping("/api/payroll")
@Tag(name = "Payroll runs", description = "Monthly payroll: live draft, adjustments, finalise, reopen, export")
public class PayrollRunController {

    private final PayrollRunService runs;
    private final PayrollFinalizeService finalize;

    public PayrollRunController(PayrollRunService runs, PayrollFinalizeService finalize) {
        this.runs = runs;
        this.finalize = finalize;
    }

    @GetMapping("/runs")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "List payroll runs, newest month first, with per-currency totals")
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "24") int size) {
        return runs.listRuns(page, size);
    }

    @GetMapping("/runs/{period}")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Run header, totals per currency, warnings and finalise/reopen readiness")
    public Map<String, Object> run(@PathVariable String period) {
        return runs.runView(period);
    }

    @GetMapping("/runs/{period}/lines")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Rows of a run (zero-based page, bounded size, exceptions first)")
    public Map<String, Object> lines(@PathVariable String period,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "false") boolean exceptionsOnly,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        return runs.lines(period, q, exceptionsOnly, page, size);
    }

    @GetMapping("/runs/{period}/operations")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Leave-balance sync operations for the month (zero-based page, newest first)")
    public Map<String, Object> operations(@PathVariable String period,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return runs.operations(period, page, size);
    }

    @GetMapping("/runs/{period}/lines/{employeeId}")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "One person's payslip in a run, with the calculation breakdown (FR-PAY-15)")
    public Map<String, Object> line(@PathVariable String period, @PathVariable Long employeeId) {
        return runs.lineView(period, employeeId);
    }

    @PostMapping("/runs/{period}/calculate")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Calculate / recalculate the month's live draft for everyone employed in it (FR-PAY-11)")
    public Map<String, Object> calculate(@PathVariable String period) {
        return runs.calculate(period, AuthContext.requireEmployeeId());
    }

    @PutMapping("/runs/{period}/adjustments/{employeeId}")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Set the one manual adjustment for a person this month: {amount, note} (FR-PAY-08)")
    public Map<String, Object> setAdjustment(@PathVariable String period, @PathVariable Long employeeId,
                                             @RequestBody Map<String, Object> body) {
        return runs.setAdjustment(period, employeeId, body.get("amount"), body.get("note"), AuthContext.requireEmployeeId());
    }

    @DeleteMapping("/runs/{period}/adjustments/{employeeId}")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Remove a person's adjustment for the month")
    public Map<String, Object> deleteAdjustment(@PathVariable String period, @PathVariable Long employeeId) {
        return runs.deleteAdjustment(period, employeeId, AuthContext.requireEmployeeId());
    }

    @PostMapping("/runs/{period}/finalize")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Finalise the month: {confirm:true, revision}. 202 + operation (poll statusUrl) (FR-PAY-12)")
    public ResponseEntity<Map<String, Object>> finalizeRun(@PathVariable String period,
                                                           @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Long revision = b.get("revision") == null ? null : Long.valueOf(String.valueOf(b.get("revision")));
        Map<String, Object> op = finalize.finalizeRun(period, Boolean.parseBoolean(String.valueOf(b.get("confirm"))),
                revision, AuthContext.requireEmployeeId());
        return accepted(op);
    }

    @PostMapping("/runs/{period}/reopen")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Reopen a finalised month: {confirm:true, reason}. 202 + operation (FR-PAY-14)")
    public ResponseEntity<Map<String, Object>> reopen(@PathVariable String period,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, Object> op = finalize.reopenRun(period, Boolean.parseBoolean(String.valueOf(b.get("confirm"))),
                b.get("reason") == null ? null : String.valueOf(b.get("reason")), AuthContext.requireEmployeeId());
        return accepted(op);
    }

    @PostMapping("/runs/{period}/return-to-draft")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "After Leave rejected a finalise (ACTION_REQUIRED): back to draft, nothing was deducted")
    public Map<String, Object> returnToDraft(@PathVariable String period) {
        return finalize.returnToDraft(period, AuthContext.requireEmployeeId());
    }

    @GetMapping("/runs/{period}/reconciliation")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Compare the finalised month's covered absences with the Leave ledger (guide §6)")
    public Map<String, Object> reconciliation(@PathVariable String period) {
        return finalize.reconciliation(period);
    }

    @GetMapping("/runs/{period}/export.csv")
    @RequiresPermission(feature = "payroll", action = "export")
    @Operation(summary = "UTF-8 CSV of the month for the bank or accountant (FR-PAY-16)")
    public ResponseEntity<byte[]> export(@PathVariable String period) {
        String csv = runs.exportCsv(period, AuthContext.requireEmployeeId());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payroll-" + period + ".csv\"")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/operations")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Pending finalise/reopen operations (reconciliation)")
    public List<Map<String, Object>> pending() {
        return finalize.pendingOperations();
    }

    @GetMapping("/operations/{operationId}")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Poll a finalise/reopen operation")
    public Map<String, Object> operation(@PathVariable String operationId) {
        return finalize.operation(operationId);
    }

    @PostMapping("/operations/{operationId}/resolve")
    @RequiresPermission(feature = "payroll", action = "run")
    @Operation(summary = "Resolve a stuck operation: {reason, outcome?}. Fences the key in Leave and applies the outcome Leave holds (audited)")
    public Map<String, Object> resolve(@PathVariable String operationId, @RequestBody Map<String, Object> body) {
        return finalize.resolve(operationId, body.get("outcome") == null ? null : String.valueOf(body.get("outcome")),
                body.get("reason") == null ? null : String.valueOf(body.get("reason")), AuthContext.requireEmployeeId());
    }

    /** Guide §6: finalise / reopen always answer 202 + operation + Location to poll (even when already done). */
    private static ResponseEntity<Map<String, Object>> accepted(Map<String, Object> op) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(HttpHeaders.LOCATION, String.valueOf(op.get("statusUrl"))).body(op);
    }
}
