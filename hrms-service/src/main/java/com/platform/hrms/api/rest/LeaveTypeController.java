package com.platform.hrms.api.rest;

import com.platform.hrms.api.dto.LeaveTypeResponse;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
@Tag(name = "Leave Types", description = "Tenant-configured leave types (SICK/CASUAL/…)")
public class LeaveTypeController {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeController(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @GetMapping
    @Operation(summary = "List active leave types for current tenant")
    public List<LeaveTypeResponse> list() {
        return leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(LeaveTypeResponse::from)
                .toList();
    }
}
