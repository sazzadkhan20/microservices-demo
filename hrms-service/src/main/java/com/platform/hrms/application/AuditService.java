package com.platform.hrms.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.hrms.domain.AuditLog;
import com.platform.hrms.infrastructure.persistence.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Records immutable audit trail entries into {@code audit_log}.
 * Runs in its own REQUIRES_NEW transaction so audit writes are not rolled back
 * if the caller's transaction fails after the fact.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Write one audit entry.
     *
     * @param actorId  Employee ID of the actor performing the action (nullable for system actions).
     * @param action   Short verb: CREATE, UPDATE, DELETE, APPROVE, REJECT, …
     * @param entity   Domain entity class name, e.g. "WorkTask", "LeaveApplication".
     * @param entityId String representation of the entity primary key.
     * @param before   State before the change (null for creates); serialised to JSONB.
     * @param after    State after the change (null for deletes); serialised to JSONB.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String action, String entity, String entityId,
                       Object before, Object after) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAt(Instant.now());
            entry.setActorEmployeeId(actorId);
            entry.setAction(action);
            entry.setEntity(entity);
            entry.setEntityId(entityId);
            entry.setBeforeJson(toJson(before));
            entry.setAfterJson(toJson(after));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never fail the business transaction because of audit persistence
            log.warn("Audit write failed for {} {} {}: {}", action, entity, entityId, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("AuditService: failed to serialise object of type {}: {}", obj.getClass().getSimpleName(), e.getMessage());
            return "\"<serialisation error>\"";
        }
    }
}
