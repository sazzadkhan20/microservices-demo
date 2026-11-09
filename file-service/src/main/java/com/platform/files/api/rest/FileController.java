package com.platform.files.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.files.api.dto.DownloadUrlResponse;
import com.platform.files.api.dto.FileMetadataResponse;
import com.platform.files.api.dto.FileUploadResponse;
import com.platform.files.application.FileService;
import com.platform.files.security.RequiresPermission;
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
    @RequiresPermission(feature = "documents", action = "create")
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "classification", required = false) String classification,
            @RequestParam(value = "employeeId", required = false) Long employeeId) throws IOException {

        Long empId = AuthContext.getEmployeeId();
        String uploadedBy = empId != null ? empId.toString() : null;
        Long subject = employeeId != null ? employeeId : empId;

        FileUploadResponse response = service.upload(uploadedBy, file, classification, subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @RequiresPermission(feature = "documents", action = "view")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all) {
        Long empId = AuthContext.getEmployeeId();
        return ResponseEntity.ok(service.list(empId, page, size, all));
    }

    @GetMapping("/{id}")
    @RequiresPermission(feature = "documents", action = "view")
    public ResponseEntity<FileMetadataResponse> metadata(@PathVariable Long id) {
        Long empId = AuthContext.getEmployeeId();
        return ResponseEntity.ok(service.getMetadata(id, empId));
    }

    /**
     * Issue a short-lived download URL after authorization + RELEASED check (guide §8).
     * Issuance is audited separately from byte access.
     */
    @PostMapping("/{id}/download-url")
    @RequiresPermission(feature = "documents", action = "view")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(@PathVariable Long id) {
        Long empId = AuthContext.getEmployeeId();
        return ResponseEntity.ok(service.issueDownloadUrl(id, empId));
    }

    /**
     * Consume a short-lived token and stream bytes (audits actual access).
     * Auth is the token itself — no session {@code @RequiresPermission}.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable Long id,
            @RequestParam("token") String token) throws IOException {
        FileService.OpenedDownload opened = service.openDownloadWithToken(id, token);
        return streamResponse(opened.metadata(), opened.stream());
    }

    /**
     * Session download fallback — still blocked while quarantined (DOC01).
     * Prefer {@code POST /{id}/download-url} then {@code GET /{id}/content}.
     */
    @GetMapping("/{id}/download")
    @RequiresPermission(feature = "documents", action = "view")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        Long empId = AuthContext.getEmployeeId();
        FileMetadataResponse meta = service.getMetadata(id, empId);
        InputStream stream = service.openDownload(id, empId);
        return streamResponse(meta, stream);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(feature = "documents", action = "delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) throws IOException {
        Long empId = AuthContext.getEmployeeId();
        service.delete(id, empId);
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
