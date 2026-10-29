package com.platform.hrms.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "attendance_record")
public class AttendanceRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "employee_code_snapshot", length = 64) private String employeeCodeSnapshot;
    @Column(name = "work_date", nullable = false) private LocalDate workDate;
    @Column(name = "check_in_at") private Instant checkInAt;
    @Column(name = "check_out_at") private Instant checkOutAt;
    @Column(name = "status_code", length = 8) private String statusCode;
    @Column(name = "status_label", length = 64) private String statusLabel;
    @Column(name = "ip_address", length = 64) private String ipAddress;
    @Column(length = 128) private String device;
    @Column(name = "device_type", length = 32) private String deviceType;
    @Column(name = "location_label", length = 256) private String locationLabel;
    @Column(name = "time_zone", length = 64) private String timeZone;
    @Column(precision = 10, scale = 7) private BigDecimal latitude;
    @Column(precision = 10, scale = 7) private BigDecimal longitude;
    @Column(length = 512) private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeCodeSnapshot() { return employeeCodeSnapshot; }
    public void setEmployeeCodeSnapshot(String employeeCodeSnapshot) { this.employeeCodeSnapshot = employeeCodeSnapshot; }
    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
    public Instant getCheckInAt() { return checkInAt; }
    public void setCheckInAt(Instant checkInAt) { this.checkInAt = checkInAt; }
    public Instant getCheckOutAt() { return checkOutAt; }
    public void setCheckOutAt(Instant checkOutAt) { this.checkOutAt = checkOutAt; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getLocationLabel() { return locationLabel; }
    public void setLocationLabel(String locationLabel) { this.locationLabel = locationLabel; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
