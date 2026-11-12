package com.platform.people.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.people.domain.AuditLog;
import com.platform.people.infrastructure.persistence.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * Records immutable audit trail entries into {@code people_audit_log}.
 * Runs in its own REQUIRES_NEW transaction so audit writes are not rolled back
 * if the caller's transaction fails after the fact.
 * Captures client IP when a request is in scope (FR-AU-01).
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
     * @param action   Short verb: CREATE, UPDATE, DELETE, APPROVE, REJECT, SIGN_IN, …
     * @param entity   Domain entity class name, e.g. "Employee", "LeaveApplication".
     * @param entityId String representation of the entity primary key.
     * @param before   State before the change (null for creates); serialised to JSONB.
     * @param after    State after the change (null for deletes); serialised to JSONB.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String action, String entity, String entityId,
                       Object before, Object after) {
        record(actorId, action, entity, entityId, before, after, clientIp());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String action, String entity, String entityId,
                       Object before, Object after, String ip) {
        try {
            AuditLog entry = new AuditLog();
            entry.setAt(Instant.now());
            entry.setActorEmployeeId(actorId);
            entry.setAction(action);
            entry.setEntity(entity);
            entry.setEntityId(entityId);
            entry.setBeforeJson(toJson(before));
            entry.setAfterJson(toJson(after));
            if (ip != null && !ip.isBlank()) {
                entry.setIp(ip.length() > 64 ? ip.substring(0, 64) : ip);
            }
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never fail the business transaction because of audit persistence
            log.warn("Audit write failed for {} {} {}: {}", action, entity, entityId, e.getMessage());
        }
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip.length() > 64 ? ip.substring(0, 64) : ip;
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
