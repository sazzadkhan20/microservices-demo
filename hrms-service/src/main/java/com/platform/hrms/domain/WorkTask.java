package com.platform.hrms.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "work_task")
public class WorkTask {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

    @Column(name = "client_name", length = 128)
    private String clientName;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "OPEN";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "external_ref", length = 64)
    private String externalRef;

    /** LOW | MEDIUM | HIGH — separate from externalRef (project-module id, FR-TSK-04). */
    @Column(name = "priority", length = 16)
    private String priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
