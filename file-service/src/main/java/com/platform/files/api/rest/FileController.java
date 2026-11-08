package com.platform.files.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.files.api.dto.DownloadUrlResponse;
import com.platform.files.api.dto.FileMetadataResponse;
import com.platform.files.api.dto.FileUploadResponse;
import com.platform.files.application.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    /**
     * Multipart upload — quarantines bytes, validates signature/structure, enqueues async scan.
     * Download stays locked until scan RELEASED (guide §8 / DOC01).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "classification", required = false) String classification,
            @RequestParam(value = "employeeId", required = false) Long employeeId) throws IOException {

        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        String uploadedBy = empId != null ? empId.toString() : null;
        Long subject = employeeId != null ? employeeId : empId;

        FileUploadResponse response = service.upload(tenantId, uploadedBy, file, classification, subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all) {
        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        return ResponseEntity.ok(service.list(tenantId, empId, page, size, all));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileMetadataResponse> metadata(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        return ResponseEntity.ok(service.getMetadata(tenantId, id, empId));
    }

    /**
     * Issue a short-lived download URL after authorization + RELEASED check (guide §8).
     * Issuance is audited separately from byte access.
     */
    @PostMapping("/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        return ResponseEntity.ok(service.issueDownloadUrl(tenantId, id, empId));
    }

    /** Consume a short-lived token and stream bytes (audits actual access). */
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable Long id,
            @RequestParam("token") String token) throws IOException {
        String tenantId = TenantContext.getTenantId();
        FileService.OpenedDownload opened = service.openDownloadWithToken(tenantId, id, token);
        return streamResponse(opened.metadata(), opened.stream());
    }

    /**
     * Session download fallback — still blocked while quarantined (DOC01).
     * Prefer {@code POST /{id}/download-url} then {@code GET /{id}/content}.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        FileMetadataResponse meta = service.getMetadata(tenantId, id, empId);
        InputStream stream = service.openDownload(tenantId, id, empId);
        return streamResponse(meta, stream);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) throws IOException {
        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        service.delete(tenantId, id, empId);
    }

    private static ResponseEntity<InputStreamResource> streamResponse(
            FileMetadataResponse meta, InputStream stream) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(meta.originalName())
                .build());
        headers.setContentLength(meta.sizeBytes());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Scan-Status", meta.scanStatus() == null ? "" : meta.scanStatus());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .body(new InputStreamResource(stream));
    }
}
