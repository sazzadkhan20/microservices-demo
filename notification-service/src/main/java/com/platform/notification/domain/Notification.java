package com.platform.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent in-app notification scoped to a tenant + employee.
 *
 * <p>Deduplication: when {@code dedupeKey} is non-null the service checks
 * for an existing (tenantId, dedupeKey) row before inserting — preventing
 * duplicate delivery for the same logical event.
 */
@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_tenant_emp", columnList = "tenant_id, employee_id"),
                @Index(name = "idx_notif_tenant_dedupe", columnList = "tenant_id, dedupe_key")
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(length = 100)
    private String category;

    @Column(name = "read_flag", nullable = false)
    private boolean readFlag;

    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (!readFlag) {
            readFlag = false;
        }
    }

    // --- getters / setters (no Lombok) ---

    public Long getId() { return id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isReadFlag() { return readFlag; }
    public void setReadFlag(boolean readFlag) { this.readFlag = readFlag; }

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }

    public Instant getCreatedAt() { return createdAt; }
}
