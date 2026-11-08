package com.platform.files.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.common.tenant.TenantContext;
import com.platform.files.application.FileService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * S2S file existence / scan-state checks (People document attach — guide §8).
 * Callers must present {@code X-Internal-Key}; gateway blocks {@code /api/internal/**}.
 */
@RestController
@RequestMapping("/api/internal/files")
@Hidden
public class InternalFileController {

    private final FileService fileService;
    private final String internalKey;

    public InternalFileController(
            FileService fileService,
            @Value("${platform.security.internal-key}") String internalKey) {
        this.fileService = fileService;
        this.internalKey = internalKey;
    }

    @GetMapping("/{id}")
    public Map<String, Object> describe(@PathVariable Long id, HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal key required");
        }
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Tenant-ID required");
        }
        return fileService.internalDescribe(tenantId, id);
    }
}
