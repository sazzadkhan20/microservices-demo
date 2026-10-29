package com.platform.hrms.api.rest;

import com.platform.hrms.extension.engine.LeaveCalcEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/leave-policy")
@Tag(name = "Leave Policy", description = "Read tenant leave configuration (working days, notice, coverage, approval chain)")
public class LeavePolicyController {

    private final LeaveCalcEngine leaveCalcEngine;

    public LeavePolicyController(LeaveCalcEngine leaveCalcEngine) {
        this.leaveCalcEngine = leaveCalcEngine;
    }

    @GetMapping
    @Operation(summary = "Current tenant leave policy + coverage rules from DB config")
    public Map<String, Object> get() {
        return leaveCalcEngine.currentPolicyView();
    }
}
