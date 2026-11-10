package com.platform.files.api.rest;

import com.platform.common.security.InternalCalls;
import com.platform.files.application.FileService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        assertInternal(request);
        return fileService.internalDescribe(id);
    }

    @PostMapping("/{id}/bind-employee")
    public Map<String, Object> bindEmployee(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        assertInternal(request);
        Object raw = body == null ? null : body.get("employeeId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeId is required");
        }
        Long employeeId;
        try {
            employeeId = Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "employeeId must be numeric");
        }
        return fileService.bindEmployee(id, employeeId);
    }

    private void assertInternal(HttpServletRequest request) {
        if (!InternalCalls.isTrusted(request.getHeader(InternalCalls.HEADER), internalKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal key required");
        }
    }
}
