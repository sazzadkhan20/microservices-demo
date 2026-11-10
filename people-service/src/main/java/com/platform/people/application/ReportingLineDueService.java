package com.platform.people.application;

import com.platform.people.client.LeaveClient;
import com.platform.people.domain.Employee;
import com.platform.people.infrastructure.persistence.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies due future-dated reporting-line changes: flip {@code manager_id} from history and
 * move that employee's pending leave approvals from the old manager to the new one.
 */
@Service
public class ReportingLineDueService {

    private static final Logger log = LoggerFactory.getLogger(ReportingLineDueService.class);

    private final EmployeeRepository employeeRepository;
    private final ReportingLineService reportingLineService;
    private final EmploymentHistoryService employmentHistoryService;
    private final LeaveClient leaveClient;
    private final ObjectProvider<ReportingLineDueService> self;

    public ReportingLineDueService(
            EmployeeRepository employeeRepository,
            ReportingLineService reportingLineService,
            EmploymentHistoryService employmentHistoryService,
            LeaveClient leaveClient,
            ObjectProvider<ReportingLineDueService> self) {
        this.employeeRepository = employeeRepository;
        this.reportingLineService = reportingLineService;
        this.employmentHistoryService = employmentHistoryService;
        this.leaveClient = leaveClient;
        this.self = self;
    }

    @Transactional(readOnly = true)
    public List<Long> findDueEmployeeIds() {
        LocalDate today = reportingLineService.businessToday();
        List<Long> due = new ArrayList<>();
        for (Employee e : employeeRepository.findAll()) {
            if (e.getId() == null) {
                continue;
            }
            Long expected = reportingLineService.managerOn(e.getId(), today);
            if (!Objects.equals(expected, e.getManagerId())) {
                due.add(e.getId());
            }
        }
        return due;
    }

    public int applyDueManagerChanges() {
        ReportingLineDueService proxy = self != null ? self.getIfAvailable() : null;
        if (proxy == null) {
            proxy = this;
        }
        int applied = 0;
        for (Long id : findDueEmployeeIds()) {
            try {
                if (proxy.applyOneDueManagerChange(id)) {
                    applied++;
                }
            } catch (RuntimeException ex) {
                log.warn("Due manager change failed for employeeId={}: {}", id, ex.getMessage());
            }
        }
        return applied;
    }

    /**
     * Flip one employee's manager pointer to the history line active today and move their
     * pending leave approvals from the previous manager to the new one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyOneDueManagerChange(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        if (employee == null) {
            return false;
        }
        LocalDate today = reportingLineService.businessToday();
        Long previous = employee.getManagerId();
        Long due = reportingLineService.managerOn(employeeId, today);
        if (Objects.equals(previous, due)) {
            return false;
        }
        if (due != null && reportingLineService.wouldCycle(employeeId, due)) {
            log.error("Skipping due manager flip for employeeId={} → {} (would create reporting loop)",
                    employeeId, due);
            return false;
        }

        employee.setManagerId(due);
        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);
        employmentHistoryService.record(employeeId, "managerId", previous, due);

        int moved = 0;
        if (previous != null && due != null) {
            moved = leaveClient.reassignApprovalsForApplicant(employeeId, previous, due);
        }
        log.info("Applied due manager change employeeId={} {} → {} (approvalsMoved={})",
                employeeId, previous, due, moved);
        return true;
    }

    /**
     * Immediate path after a same-day manager change: move that applicant's pending approvals.
     */
    public int moveApprovalsAfterManagerChange(Long employeeId, Long previousManagerId, Long newManagerId) {
        if (employeeId == null || previousManagerId == null || newManagerId == null) {
            return 0;
        }
        if (Objects.equals(previousManagerId, newManagerId)) {
            return 0;
        }
        return leaveClient.reassignApprovalsForApplicant(employeeId, previousManagerId, newManagerId);
    }

    public LocalDate today() {
        return reportingLineService.businessToday();
    }
}
