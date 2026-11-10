package com.platform.payroll.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.payroll.domain.AuditLog;
import com.platform.payroll.infrastructure.persistence.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;

/**
 * Payroll audit trail (FR-AU-01: time, actor, record, before/after, IP). Written in the SAME
 * transaction as the change it records, so a payroll change and its audit entry commit or roll back
 * together. Actor is always derived server-side (guide §11).
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Long actorId, String action, String entity, String entityId, Object before, Object after) {
        AuditLog entry = new AuditLog();
        entry.setAt(Instant.now());
        entry.setActorEmployeeId(actorId);
        entry.setAction(action);
        entry.setEntity(entity);
        entry.setEntityId(entityId);
        entry.setBeforeJson(toJson(before));
        entry.setAfterJson(toJson(after));
        entry.setIp(clientIp());
        auditLogRepository.save(entry);
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
        return ip == null ? null : ip.substring(0, Math.min(64, ip.length()));
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit payload not serialisable", e);
        }
    }
}
