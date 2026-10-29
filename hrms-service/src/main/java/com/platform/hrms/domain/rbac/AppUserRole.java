package com.platform.hrms.domain.rbac;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user_role")
public class AppUserRole {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "role_id", nullable = false) private Long roleId;

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
