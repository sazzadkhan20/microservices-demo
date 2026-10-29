package com.platform.files.application;

import com.platform.common.tenant.TenantIds;
import com.platform.files.api.dto.FileMetadataResponse;
import com.platform.files.api.dto.FileUploadResponse;
import com.platform.files.domain.FileObject;
import com.platform.files.infrastructure.persistence.FileObjectRepository;
import com.platform.files.infrastructure.storage.ObjectStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class FileService {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final FileObjectRepository repository;
    private final ObjectStorage storage;

    public FileService(FileObjectRepository repository, ObjectStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public FileUploadResponse upload(String tenantId, String uploadedBy, MultipartFile file)
            throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "File exceeds maximum allowed size of 10 MB (got " + file.getSize() + " bytes)");
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Unsupported content type '" + file.getContentType() + "'. Allowed: PDF, JPG, PNG, DOCX");
        }

        String normalized = TenantIds.normalize(tenantId);
        String bucket = TenantIds.filesBucket(normalized);
        String objectKey = UUID.randomUUID().toString();
        storage.store(bucket, objectKey, file.getInputStream(), contentType, file.getSize());

        FileObject fo = new FileObject();
        fo.setTenantId(normalized);
        fo.setOriginalName(file.getOriginalFilename() != null
                ? file.getOriginalFilename() : objectKey);
        fo.setContentType(contentType);
        fo.setSizeBytes(file.getSize());
        fo.setStorageKey(bucket + "/" + objectKey);
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
        return getMetadata(tenantId, id, null);
    }

    @Transactional(readOnly = true)
    public FileMetadataResponse getMetadata(String tenantId, Long id, Long requesterEmployeeId) {
        FileObject fo = findByTenantId(tenantId, id);
        assertCanAccess(fo, requesterEmployeeId, false);
        return toMetadata(fo);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String tenantId, Long requesterEmployeeId, int page, int size, boolean all) {
        String t = TenantIds.normalize(tenantId);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        Page<FileObject> result;
        if (all) {
            // "all" still limited to own uploads unless caller identity missing (service hook)
            result = requesterEmployeeId == null
                    ? repository.findByTenantIdOrderByIdDesc(t, PageRequest.of(p, s))
                    : repository.findByTenantIdAndUploadedByOrderByIdDesc(
                            t, String.valueOf(requesterEmployeeId), PageRequest.of(p, s));
        } else {
            String by = requesterEmployeeId == null ? "" : String.valueOf(requesterEmployeeId);
            result = repository.findByTenantIdAndUploadedByOrderByIdDesc(t, by, PageRequest.of(p, s));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", result.getContent().stream().map(this::toMetadata).toList());
        out.put("page", p);
        out.put("size", s);
        out.put("totalElements", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        return out;
    }

    @Transactional(readOnly = true)
    public InputStream openDownload(String tenantId, Long id) throws IOException {
        return openDownload(tenantId, id, null);
    }

    @Transactional(readOnly = true)
    public InputStream openDownload(String tenantId, Long id, Long requesterEmployeeId) throws IOException {
        FileObject fo = findByTenantId(tenantId, id);
        assertCanAccess(fo, requesterEmployeeId, true);
        String[] parts = splitStorageKey(fo.getStorageKey());
        return storage.load(parts[0], parts[1]);
    }

    private void assertCanAccess(FileObject fo, Long requesterEmployeeId, boolean forDownload) {
        if (requesterEmployeeId == null) {
            return; // trusted internal / legacy
        }
        String owner = fo.getUploadedBy();
        if (owner != null && !owner.isBlank() && !owner.equals(String.valueOf(requesterEmployeeId))) {
            // Allow download if linked via HRMS documents is out of band; keep tenant files private to uploader
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    forDownload ? "Forbidden: file belongs to another user"
                            : "Forbidden: cannot view another user's file metadata");
        }
    }

    @Transactional(readOnly = true)
    public String getContentType(String tenantId, Long id) {
        return findByTenantId(tenantId, id).getContentType();
    }

    public void ensureTenantBucket(String tenantId) throws IOException {
        storage.ensureBucket(TenantIds.filesBucket(tenantId));
    }

    /** Strip charset params; map common aliases; fall back to extension when browsers send octet-stream. */
    static String normalizeContentType(String raw, String filename) {
        String ct = raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        if ("image/jpg".equals(ct)) {
            ct = "image/jpeg";
        }
        if (ct.isBlank() || "application/octet-stream".equals(ct)) {
            String name = filename == null ? "" : filename.toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".pdf")) return "application/pdf";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".docx")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
        }
        return ct.isBlank() ? null : ct;
    }

    private FileObject findByTenantId(String tenantId, Long id) {
        return repository.findByIdAndTenantId(id, TenantIds.normalize(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + id));
    }

    private static String[] splitStorageKey(String storageKey) {
        int slash = storageKey.indexOf('/');
        if (slash <= 0 || slash == storageKey.length() - 1) {
            throw new IllegalStateException("Invalid storage key: " + storageKey);
        }
        return new String[]{storageKey.substring(0, slash), storageKey.substring(slash + 1)};
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
