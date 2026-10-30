package com.platform.hrms.domain.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tenant role with config-driven policy flags (reusable across businesses).
 * Engines honour these flags — never hard-code a role name like {@code SUPERADMIN}.
 */
@Entity
@Table(name = "app_role")
public class AppRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(nullable = false)
    private boolean active = true;

    /** Holders cannot be moved to Inactive / Resigned / Terminated. */
    @Column(name = "protect_from_offboard", nullable = false)
    private boolean protectFromOffboard;

    /** Permission matrix cannot be replaced via API. */
    @Column(name = "immutable_permissions", nullable = false)
    private boolean immutablePermissions;

    /** Holders receive {@code *:*} and widest ALL scope. */
    @Column(name = "grants_wildcard", nullable = false)
    private boolean grantsWildcard;

    /** Cannot unassign this role if active holders would fall below this count. */
    @Column(name = "min_holders", nullable = false)
    private int minHolders;

    /**
     * Comma-separated role codes allowed to assign this role.
     * Blank/null = any caller who already passed the roles:assign permission gate.
     */
    @Column(name = "assignable_by", length = 512)
    private String assignableBy;

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
    public boolean isProtectFromOffboard() { return protectFromOffboard; }
    public void setProtectFromOffboard(boolean protectFromOffboard) { this.protectFromOffboard = protectFromOffboard; }
    public boolean isImmutablePermissions() { return immutablePermissions; }
    public void setImmutablePermissions(boolean immutablePermissions) { this.immutablePermissions = immutablePermissions; }
    public boolean isGrantsWildcard() { return grantsWildcard; }
    public void setGrantsWildcard(boolean grantsWildcard) { this.grantsWildcard = grantsWildcard; }
    public int getMinHolders() { return minHolders; }
    public void setMinHolders(int minHolders) { this.minHolders = Math.max(0, minHolders); }
    public String getAssignableBy() { return assignableBy; }
    public void setAssignableBy(String assignableBy) { this.assignableBy = assignableBy; }
}
