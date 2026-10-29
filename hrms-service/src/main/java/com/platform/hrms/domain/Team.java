package com.platform.hrms.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "team")
public class Team {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "lead_employee_id")
    private Long leadEmployeeId;

    @Column(name = "max_on_leave", nullable = false)
    private int maxOnLeave = 1;

    @Column(name = "clash_action", nullable = false, length = 16)
    private String clashAction = "ALERT";

    public Long getId() { return id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public Long getLeadEmployeeId() { return leadEmployeeId; }
    public void setLeadEmployeeId(Long leadEmployeeId) { this.leadEmployeeId = leadEmployeeId; }

    public int getMaxOnLeave() { return maxOnLeave; }
    public void setMaxOnLeave(int maxOnLeave) { this.maxOnLeave = maxOnLeave; }

    public String getClashAction() { return clashAction; }
    public void setClashAction(String clashAction) { this.clashAction = clashAction; }
}
