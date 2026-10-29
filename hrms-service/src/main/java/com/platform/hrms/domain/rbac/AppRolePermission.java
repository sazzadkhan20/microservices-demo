package com.platform.hrms.domain.rbac;

import jakarta.persistence.*;

@Entity
@Table(name = "app_role_permission")
public class AppRolePermission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "role_id", nullable = false) private Long roleId;
    @Column(name = "permission_id", nullable = false) private Long permissionId;
    @Column(name = "data_scope", nullable = false, length = 16) private String dataScope = "OWN";

    public Long getId() { return id; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
}
