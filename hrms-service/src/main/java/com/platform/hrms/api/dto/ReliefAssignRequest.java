package com.platform.hrms.api.dto;

import jakarta.validation.constraints.NotNull;

public record ReliefAssignRequest(
        @NotNull Long reliefEmployeeId
) {
}
