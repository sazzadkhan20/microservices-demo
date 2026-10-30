package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.domain.LeaveApprovalDelegate;
import com.platform.hrms.infrastructure.persistence.LeaveApprovalDelegateRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * CRUD for the caller's own approval delegates.
 * An approval delegate can approve leave on behalf of the delegating employee.
 */
@RestController
@RequestMapping("/api/leave-delegates")
@Tag(name = "Leave Approval Delegates", description = "Manage approval delegation (own delegates only)")
public class LeaveDelegateController {

    private final LeaveApprovalDelegateRepository repository;

    public LeaveDelegateController(LeaveApprovalDelegateRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @RequiresPermission(feature = "leave", action = "view")
    @Operation(summary = "List own active delegates")
    public List<LeaveApprovalDelegate> list() {
        Long empId = AuthContext.requireEmployeeId();
        return repository.findByEmployeeId(empId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "Create a delegate for the caller")
    public LeaveApprovalDelegate create(@RequestBody LeaveApprovalDelegate body) {
        Long empId = AuthContext.requireEmployeeId();
        if (empId.equals(body.getDelegateEmployeeId())) {
            throw new IllegalArgumentException("Cannot delegate to yourself");
        }
        body.setEmployeeId(empId);
        body.setActive(true);
        body.setCreatedAt(Instant.now());
        return repository.save(body);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "Update a delegate (own only)")
    public LeaveApprovalDelegate update(@PathVariable Long id, @RequestBody LeaveApprovalDelegate body) {
        Long empId = AuthContext.requireEmployeeId();
        LeaveApprovalDelegate d = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delegate not found: " + id));
        if (!empId.equals(d.getEmployeeId())) {
            throw new IllegalStateException("Forbidden: not your delegate");
        }
        if (body.getDelegateEmployeeId() != null) {
            if (empId.equals(body.getDelegateEmployeeId())) {
                throw new IllegalArgumentException("Cannot delegate to yourself");
            }
            d.setDelegateEmployeeId(body.getDelegateEmployeeId());
        }
        if (body.getFromDate() != null) d.setFromDate(body.getFromDate());
        if (body.getToDate() != null) d.setToDate(body.getToDate());
        d.setActive(body.isActive());
        return repository.save(d);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(feature = "leave", action = "request")
    @Operation(summary = "Deactivate a delegate (own only)")
    public void deactivate(@PathVariable Long id) {
        Long empId = AuthContext.requireEmployeeId();
        LeaveApprovalDelegate d = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delegate not found: " + id));
        if (!empId.equals(d.getEmployeeId())) {
            throw new IllegalStateException("Forbidden: not your delegate");
        }
        d.setActive(false);
        repository.save(d);
    }
}
