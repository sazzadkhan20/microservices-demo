package com.platform.hrms.api.rest;

import com.platform.hrms.api.dto.EmployeeResponse;
import com.platform.hrms.application.AuthFacadeService;
import com.platform.hrms.application.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Me", description = "Current user profile")
public class MeController {

    private final AuthFacadeService authFacadeService;
    private final EmployeeService employeeService;

    public MeController(AuthFacadeService authFacadeService, EmployeeService employeeService) {
        this.authFacadeService = authFacadeService;
        this.employeeService = employeeService;
    }

    @GetMapping
    @Operation(summary = "Alias of /api/auth/me for frontend")
    public Map<String, Object> me() {
        return authFacadeService.me();
    }

    @PatchMapping("/personal")
    @Operation(summary = "FR-EMP-06: self-edit phone, personal email, address, emergency contact")
    public EmployeeResponse updatePersonal(@RequestBody Map<String, Object> body) {
        return employeeService.updateOwnPersonal(body == null ? Map.of() : body);
    }
}
