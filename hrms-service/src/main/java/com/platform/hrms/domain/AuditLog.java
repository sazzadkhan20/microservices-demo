package com.platform.hrms.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "entity", length = 64)
    private String entity;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json", columnDefinition = "jsonb")
    private String afterJson;

    @Column(name = "ip", length = 64)
    private String ip;

    public Long getId() { return id; }

    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }

    public Long getActorEmployeeId() { return actorEmployeeId; }
    public void setActorEmployeeId(Long actorEmployeeId) { this.actorEmployeeId = actorEmployeeId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }

    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
}
