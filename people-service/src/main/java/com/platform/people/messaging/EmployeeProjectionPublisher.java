package com.platform.people.messaging;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.people.domain.Designation;
import com.platform.people.domain.Employee;
import com.platform.people.domain.Team;
import com.platform.people.infrastructure.persistence.DesignationRepository;
import com.platform.people.infrastructure.persistence.TeamRepository;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * People → Leave / Time&amp;Work employee reference projection (guide §3 / §7 outbox).
 */
@Component
@ConditionalOnProperty(prefix = "platform.employee-projection", name = "publisher", havingValue = "true")
public class EmployeeProjectionPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmployeeProjectionPublisher.class);

    private final OutboxWriter outboxWriter;
    private final ObjectProvider<OutboxRelay> outboxRelay;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;

    public EmployeeProjectionPublisher(
            OutboxWriter outboxWriter,
            ObjectProvider<OutboxRelay> outboxRelay,
            DesignationRepository designationRepository,
            TeamRepository teamRepository) {
        this.outboxWriter = outboxWriter;
        this.outboxRelay = outboxRelay;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
    }

    public void publishUpserted(Employee employee) {
        publishUpserted(employee, null);
    }

    public void publishUpserted(Employee employee, java.util.List<String> roleCodes) {
        if (employee == null || employee.getId() == null) {
            return;
        }
        Map<String, Object> payload = toPayload(employee);
        if (roleCodes != null) {
            payload.put("roleCodes", roleCodes);
        }
        PlatformEvent event = PlatformEvent.of(
                EventTypes.PEOPLE_EMPLOYEE_UPSERTED,
                String.valueOf(employee.getId()),
                employee.getUpdatedAt() == null ? 0L : employee.getUpdatedAt().toEpochMilli(),
                CorrelationContext.get(),
                payload);
        outboxWriter.enqueue(event, PlatformQueues.PEOPLE_EMPLOYEE_UPSERTED);
        flushOutboxAfterCommit();
    }

    private void flushOutboxAfterCommit() {
        OutboxRelay relay = outboxRelay.getIfAvailable();
        if (relay == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        relay.drainBatch();
                    } catch (Exception e) {
                        // Never fail the business API after a successful commit — scheduled poll retries.
                        log.warn("Outbox drain after employee projection failed: {}", e.getMessage());
                    }
                }
            });
        } else {
            try {
                relay.drainBatch();
            } catch (Exception e) {
                log.warn("Outbox drain failed: {}", e.getMessage());
            }
        }
    }

    Map<String, Object> toPayload(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeCode", e.getEmployeeCode());
        m.put("fullName", e.getFullName());
        m.put("email", e.getEmail());
        m.put("departmentId", e.getDepartmentId());
        m.put("designationId", e.getDesignationId());
        m.put("managerId", e.getManagerId());
        m.put("preferredName", e.getPreferredName());
        m.put("jobTitle", e.getJobTitle());
        m.put("locationId", e.getLocationId());
        m.put("teamId", e.getTeamId());
        m.put("employmentType", e.getEmploymentType());
        m.put("startDate", e.getStartDate() == null ? null : e.getStartDate().toString());
        m.put("probationEnd", e.getProbationEnd() == null ? null : e.getProbationEnd().toString());
        m.put("contractEnd", e.getContractEnd() == null ? null : e.getContractEnd().toString());
        m.put("payCurrency", e.getPayCurrency());
        m.put("personalEmail", e.getPersonalEmail());
        m.put("status", e.getStatus());
        m.put("phone", e.getPhone());
        m.put("workPattern", e.getWorkPattern());
        m.put("baseSalary", e.getBaseSalary());
        m.put("hourlyRate", e.getHourlyRate());
        m.put("customFields", e.getCustomFields());
        m.put("active", e.isActive());
        m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
        m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
        if (e.getDesignationId() != null) {
            designationRepository.findById(e.getDesignationId())
                    .map(Designation::getApproverRole)
                    .ifPresent(role -> m.put("designationApproverRole", role));
        }
        if (e.getTeamId() != null) {
            teamRepository.findById(e.getTeamId()).ifPresent(team -> putTeamFields(m, team));
        }
        return m;
    }

    private static void putTeamFields(Map<String, Object> m, Team team) {
        m.put("teamCode", team.getCode());
        m.put("teamMaxOnLeave", team.getMaxOnLeave());
        m.put("teamClashAction", team.getClashAction());
        m.put("teamLeadEmployeeId", team.getLeadEmployeeId());
    }
}
