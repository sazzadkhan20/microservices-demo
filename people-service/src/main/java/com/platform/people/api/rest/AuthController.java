package com.platform.people.api.rest;

import com.platform.common.security.PublicEndpoint;
import com.platform.people.application.AuthFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@PublicEndpoint(reason = "Session bootstrap / account picker — gated by gateway JWT for /me")
@Tag(name = "Auth", description = "Account picker + session identity")
public class AuthController {

    private final AuthFacadeService authFacadeService;

    public AuthController(AuthFacadeService authFacadeService) {
        this.authFacadeService = authFacadeService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "List active employees for the demo account-picker login")
    public List<Map<String, Object>> accounts() {
        return authFacadeService.listAccounts();
    }

    @GetMapping("/me")
    @Operation(summary = "Current employee profile, roles, permissions")
    public Map<String, Object> me() {
        return authFacadeService.me();
    }
}
