package com.platform.hrms.api.rest;

import com.platform.hrms.application.AuthFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Account picker + session identity")
public class AuthController {

    private final AuthFacadeService authFacadeService;

    public AuthController(AuthFacadeService authFacadeService) {
        this.authFacadeService = authFacadeService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List Active employees for account-picker login (public; pass tenantId)")
    public List<Map<String, Object>> accounts(@RequestParam String tenantId) {
        return authFacadeService.listAccounts(tenantId);
    }

    @GetMapping("/me")
    @Operation(summary = "Current employee profile, roles, permissions")
    public Map<String, Object> me() {
        return authFacadeService.me();
    }
}
