package com.platform.hrms.api.dto;

import com.platform.hrms.domain.Designation;

public record DesignationResponse(Long id, String code, String name, String approverRole) {
    public static DesignationResponse from(Designation d) {
        return new DesignationResponse(d.getId(), d.getCode(), d.getName(), d.getApproverRole());
    }
}
