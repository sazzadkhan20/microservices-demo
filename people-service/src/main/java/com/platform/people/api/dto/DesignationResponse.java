package com.platform.people.api.dto;

import com.platform.people.domain.Designation;

public record DesignationResponse(Long id, String code, String name, String approverRole) {
    public static DesignationResponse from(Designation d) {
        return new DesignationResponse(d.getId(), d.getCode(), d.getName(), d.getApproverRole());
    }
}
