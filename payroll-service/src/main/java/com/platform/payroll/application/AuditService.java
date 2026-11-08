package com.platform.payroll.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payroll.domain.AuditLog;
import com.platform.payroll.infrastructure.persistence.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Records payroll mutations into {@code payroll_audit_log} (guide §3). */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

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
            log.warn("Audit write failed for {} {} {}: {}", action, entity, entityId, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "\"<serialisation error>\"";
        }
    }
}
