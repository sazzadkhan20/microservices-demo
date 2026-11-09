package com.platform.files.domain;

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
 * Metadata record for an uploaded file (company documents, guide §8).
 *
 * <p>Bytes live in private object storage; {@link #storageKey} is {@code bucket/(quarantine|released)/uuid}.
 */
@Entity
@Table(
        name = "file_object",
        indexes = {
                @Index(name = "idx_file_storage_key", columnList = "storage_key", unique = true)
        }
)
public class FileObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 127)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Object location: {@code bucket/prefix/uuid}. */
    @Column(name = "storage_key", nullable = false, unique = true, length = 500)
    private String storageKey;

    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    /** QUARANTINED → SCANNING → CLEAN/INFECTED → RELEASED (or REJECTED). */
    @Column(name = "scan_status", nullable = false, length = 32)
    private String scanStatus = "QUARANTINED";

    @Column(nullable = false, length = 64)
    private String classification = "GENERAL";

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "scan_detail", columnDefinition = "TEXT")
    private String scanDetail;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // --- getters / setters ---

    public Long getId() { return id; }


    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public String getScanDetail() { return scanDetail; }
    public void setScanDetail(String scanDetail) { this.scanDetail = scanDetail; }

    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public Instant getCreatedAt() { return createdAt; }
}
