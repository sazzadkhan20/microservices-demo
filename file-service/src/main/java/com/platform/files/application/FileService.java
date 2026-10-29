package com.platform.files.application;

import com.platform.files.api.dto.FileMetadataResponse;
import com.platform.files.api.dto.FileUploadResponse;
import com.platform.files.domain.FileObject;
import com.platform.files.infrastructure.persistence.FileObjectRepository;
import com.platform.files.infrastructure.storage.LocalFileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FileService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final FileObjectRepository repository;
    private final LocalFileStorage storage;

    public FileService(FileObjectRepository repository, LocalFileStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /**
     * Validates, stores, and records metadata for an uploaded file.
     *
     * @param tenantId   current tenant from TenantContext
     * @param uploadedBy identifier of the uploader (may be null)
     * @param file       multipart file from the request
     * @return upload response with download URL
     */
    public FileUploadResponse upload(String tenantId, String uploadedBy, MultipartFile file)
            throws IOException {

        // Validation
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "File exceeds maximum allowed size of 10 MB (got " + file.getSize() + " bytes)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported content type '" + contentType + "'. Allowed: PDF, JPG, PNG, DOCX");
        }

        // Generate storage key
        String uuid = UUID.randomUUID().toString();
        String storageKey = tenantId + "/" + uuid;

        // Store bytes
        storage.store(storageKey, file.getInputStream());

        // Persist metadata
        FileObject fo = new FileObject();
        fo.setTenantId(tenantId);
        fo.setOriginalName(file.getOriginalFilename() != null
                ? file.getOriginalFilename() : uuid);
        fo.setContentType(contentType);
        fo.setSizeBytes(file.getSize());
        fo.setStorageKey(storageKey);
        fo.setUploadedBy(uploadedBy);

        FileObject saved = repository.save(fo);
        return new FileUploadResponse(
                saved.getId(),
                saved.getOriginalName(),
                saved.getContentType(),
                saved.getSizeBytes(),
                "/api/files/" + saved.getId() + "/download"
        );
    }

    @Transactional(readOnly = true)
    public FileMetadataResponse getMetadata(String tenantId, Long id) {
        FileObject fo = findByTenantId(tenantId, id);
        return toMetadata(fo);
    }

    /**
     * Opens an {@link InputStream} for the stored file bytes.
     * Caller is responsible for closing the stream.
     */
    @Transactional(readOnly = true)
    public InputStream openDownload(String tenantId, Long id) throws IOException {
        FileObject fo = findByTenantId(tenantId, id);
        return storage.load(fo.getStorageKey());
    }

    @Transactional(readOnly = true)
    public String getContentType(String tenantId, Long id) {
        return findByTenantId(tenantId, id).getContentType();
    }

    // --- helpers ---

    private FileObject findByTenantId(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + id));
    }

    private FileMetadataResponse toMetadata(FileObject fo) {
        return new FileMetadataResponse(
                fo.getId(),
                fo.getTenantId(),
                fo.getOriginalName(),
                fo.getContentType(),
                fo.getSizeBytes(),
                fo.getUploadedBy(),
                fo.getCreatedAt(),
                "/api/files/" + fo.getId() + "/download"
        );
    }
}
