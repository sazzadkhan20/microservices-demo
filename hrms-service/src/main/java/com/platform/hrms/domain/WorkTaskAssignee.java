package com.platform.hrms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "work_task_assignee")
@IdClass(WorkTaskAssignee.Pk.class)
public class WorkTaskAssignee {

    @Id
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Id
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    public WorkTaskAssignee() {}

    public WorkTaskAssignee(Long taskId, Long employeeId) {
        this.taskId = taskId;
        this.employeeId = employeeId;
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public static class Pk implements Serializable {
        private Long taskId;
        private Long employeeId;

        public Pk() {}

        public Pk(Long taskId, Long employeeId) {
            this.taskId = taskId;
            this.employeeId = employeeId;
        }

        public Long getTaskId() { return taskId; }
        public void setTaskId(Long taskId) { this.taskId = taskId; }
        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(taskId, pk.taskId) && Objects.equals(employeeId, pk.employeeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taskId, employeeId);
        }
    }
}
