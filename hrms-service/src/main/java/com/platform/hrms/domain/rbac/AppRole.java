package com.platform.hrms.domain.rbac;

import jakarta.persistence.*;

@Entity
@Table(name = "app_role")
public class AppRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64) private String code;
    @Column(nullable = false, length = 128) private String name;
    @Column(length = 512) private String description;
    @Column(name = "system_role", nullable = false) private boolean systemRole;
    @Column(nullable = false) private boolean active = true;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
