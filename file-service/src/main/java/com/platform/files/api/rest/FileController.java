package com.platform.files.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.common.tenant.TenantContext;
import com.platform.files.api.dto.FileMetadataResponse;
import com.platform.files.api.dto.FileUploadResponse;
import com.platform.files.application.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Multipart upload endpoint.
     *
     * <p>Accepts: PDF, JPG, PNG, DOCX — max 10 MB.
     * Returns metadata + download URL.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file) throws IOException {

        String tenantId = TenantContext.getTenantId();
        Long empId = AuthContext.getEmployeeId();
        String uploadedBy = empId != null ? empId.toString() : null;

        FileUploadResponse response = service.upload(tenantId, uploadedBy, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Returns file metadata without downloading the bytes. */
    @GetMapping("/{id}")
    public ResponseEntity<FileMetadataResponse> metadata(@PathVariable Long id) {
        String tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(service.getMetadata(tenantId, id));
    }

    /**
     * Streams the file bytes as an attachment download.
     *
     * <p>Uses {@link InputStreamResource} so the JVM does not buffer the entire file in memory.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws IOException {
        String tenantId = TenantContext.getTenantId();
        FileMetadataResponse meta = service.getMetadata(tenantId, id);
        InputStream stream = service.openDownload(tenantId, id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(meta.originalName())
                .build());
        headers.setContentLength(meta.sizeBytes());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(meta.contentType()))
                .body(new InputStreamResource(stream));
    }
}
