package com.platform.people.api.dto;

import com.platform.people.domain.Department;

public record DepartmentResponse(Long id, String code, String name) {
    public static DepartmentResponse from(Department d) {
        return new DepartmentResponse(d.getId(), d.getCode(), d.getName());
    }
}
