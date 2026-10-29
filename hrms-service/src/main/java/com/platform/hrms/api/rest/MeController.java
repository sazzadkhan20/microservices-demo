package com.platform.hrms.api.rest;

import com.platform.hrms.application.AuthFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Me", description = "Current user profile")
public class MeController {

    private final AuthFacadeService authFacadeService;

    public MeController(AuthFacadeService authFacadeService) {
        this.authFacadeService = authFacadeService;
    }

    @GetMapping
    @Operation(summary = "Alias of /api/auth/me for frontend")
    public Map<String, Object> me() {
        return authFacadeService.me();
    }
}
