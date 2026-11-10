package com.platform.files.application;

import com.platform.common.messaging.EventTypes;
import com.platform.common.messaging.PlatformEvent;
import com.platform.common.messaging.PlatformQueues;
import com.platform.common.people.PeopleDirectoryClient;
import com.platform.common.security.HrmsPermissionClient;
import com.platform.files.api.dto.DownloadUrlResponse;
import com.platform.files.api.dto.FileMetadataResponse;
import com.platform.files.api.dto.FileUploadResponse;
import com.platform.files.domain.FileObject;
import com.platform.files.infrastructure.persistence.FileObjectRepository;
import com.platform.files.infrastructure.storage.ObjectStorage;
import com.platform.messaging.correlation.CorrelationContext;
import com.platform.messaging.outbox.OutboxRelay;
import com.platform.messaging.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Document upload / quarantine / scan / release pipeline (Developer Guide §8).
 *
 * <ol>
 *   <li>Validate size, MIME+magic, and document structure</li>
 *   <li>Store privately under {@code quarantine/} and persist QUARANTINED metadata</li>
 *   <li>Enqueue scan via durable outbox → RabbitMQ (async)</li>
 *   <li>On clean: promote to {@code released/}, mark RELEASED</li>
 *   <li>Downloads require RELEASED + classification/employee scope; short-lived tokens</li>
 * </ol>
 */
@Service
@Transactional
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    public static final String SCAN_QUARANTINED = "QUARANTINED";
    public static final String SCAN_SCANNING = "SCANNING";
    public static final String SCAN_CLEAN = "CLEAN";
    public static final String SCAN_INFECTED = "INFECTED";
    public static final String SCAN_RELEASED = "RELEASED";
    public static final String SCAN_REJECTED = "REJECTED";
    public static final String SCAN_FAILED = "FAILED";

    /** Guide §8 / FR-EMP-08 — HR documents. */
    public static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    /** Employee profile photos — tighter than general documents. */
    public static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;
    private static final String PREFIX_QUARANTINE = "quarantine/";
    private static final String PREFIX_RELEASED = "released/";

    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private static final Set<String> PHOTO_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png"
    );

    private final FileObjectRepository repository;
    private final ObjectStorage storage;
    private final ObjectProvider<OutboxWriter> outboxWriter;
    private final ObjectProvider<OutboxRelay> outboxRelay;
    private final AuditService auditService;
    private final DownloadTokenService downloadTokens;
    private final ObjectProvider<PeopleDirectoryClient> peopleDirectory;
    private final long rejectedRetentionDays;
    private final String bucket;

    public FileService(
            FileObjectRepository repository,
            ObjectStorage storage,
            ObjectProvider<OutboxWriter> outboxWriter,
            ObjectProvider<OutboxRelay> outboxRelay,
            AuditService auditService,
            DownloadTokenService downloadTokens,
            ObjectProvider<PeopleDirectoryClient> peopleDirectory,
            @org.springframework.beans.factory.annotation.Value("${file.retention.rejected-days:30}")
            long rejectedRetentionDays,
            @org.springframework.beans.factory.annotation.Value("${file.storage.bucket:qii-hrms-documents}")
            String bucket) {
        this.repository = repository;
        this.storage = storage;
        this.outboxWriter = outboxWriter;
        this.outboxRelay = outboxRelay;
        this.auditService = auditService;
        this.downloadTokens = downloadTokens;
        this.peopleDirectory = peopleDirectory;
        this.rejectedRetentionDays = Math.max(1L, rejectedRetentionDays);
        this.bucket = bucket == null || bucket.isBlank() ? "qii-hrms-documents" : bucket.trim();
    }

    public FileUploadResponse upload(String uploadedBy, MultipartFile file)
            throws IOException {
        return upload(uploadedBy, file, "GENERAL", null);
    }

    public FileUploadResponse upload(
            String uploadedBy,
            MultipartFile file,
            String classification,
            Long subjectEmployeeId) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }
        String resolvedClassification = normalizeClassification(classification);
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        assertUploadConstraints(resolvedClassification, contentType, file.getSize(), file.getContentType());
        byte[] bytes = file.getBytes();
        // Defense in depth: re-check against actual byte length (client Content-Length can lie).
        assertUploadConstraints(resolvedClassification, contentType, bytes.length, file.getContentType());
        assertMagicMatches(contentType, bytes);
        assertDocumentStructure(contentType, bytes);

        Long actorId = null;
        if (uploadedBy != null && !uploadedBy.isBlank()) {
            try {
                actorId = Long.parseLong(uploadedBy.trim());
            } catch (NumberFormatException ignored) {
                // non-numeric uploader identity is not a valid employee actor
            }
        }
        assertCanUpload(actorId, subjectEmployeeId, resolvedClassification);

        String objectUuid = UUID.randomUUID().toString();
        String objectKey = PREFIX_QUARANTINE + objectUuid;

        // Store first; compensate delete if metadata commit fails (guide §8 orphan rule).
        storage.store(bucket, objectKey, new ByteArrayInputStream(bytes), contentType, bytes.length);

        FileObject saved;
        try {
            FileObject fo = new FileObject();
            fo.setOriginalName(file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : objectUuid);
            fo.setContentType(contentType);
            fo.setSizeBytes(bytes.length);
            fo.setStorageKey(bucket + "/" + objectKey);
            fo.setUploadedBy(uploadedBy);
            fo.setScanStatus(SCAN_QUARANTINED);
            fo.setClassification(resolvedClassification);
            Long emp = subjectEmployeeId;
            if (emp == null && uploadedBy != null && !uploadedBy.isBlank()) {
                try {
                    emp = Long.parseLong(uploadedBy.trim());
                } catch (NumberFormatException ignored) {
                    // non-numeric uploader identity is fine
                }
            }
            fo.setEmployeeId(emp);

            if (outboxWriter.getIfAvailable() == null) {
                // Messaging disabled — still require structure checks then auto-release locally.
                promoteToReleased(fo);
                fo.setScanStatus(SCAN_RELEASED);
                fo.setScanDetail("messaging-disabled-auto-release");
                fo.setScannedAt(Instant.now());
                fo.setReleasedAt(Instant.now());
            }

            saved = repository.save(fo);
            Long actor = fo.getEmployeeId();
            auditService.record(actor, "UPLOAD", "FileObject", String.valueOf(saved.getId()), null,
                    Map.of("originalName", saved.getOriginalName(), "contentType", saved.getContentType(),
                            "sizeBytes", saved.getSizeBytes(), "scanStatus", saved.getScanStatus(),
                            "classification", saved.getClassification()));
            if (outboxWriter.getIfAvailable() != null) {
                enqueueScan(saved);
                drainOutboxAfterCommit();
            }
        } catch (RuntimeException ex) {
            try {
                storage.delete(bucket, objectKey);
            } catch (Exception cleanup) {
                log.warn("Orphan object left after metadata failure bucket={} key={}: {}",
                        bucket, objectKey, cleanup.getMessage());
            }
            throw ex;
        }

        return new FileUploadResponse(
                saved.getId(),
                saved.getOriginalName(),
                saved.getContentType(),
                saved.getSizeBytes(),
                saved.getScanStatus(),
                "/api/files/" + saved.getId() + "/download-url"
        );
    }

    @Transactional(readOnly = true)
    public FileMetadataResponse getMetadata(Long id, Long requesterEmployeeId) {
        FileObject fo = findFile(id);
        assertCanAccess(fo, requesterEmployeeId, false);
        return toMetadata(fo);
    }

    /** Internal S2S existence/scan check (People document attach). */
    @Transactional(readOnly = true)
    public Map<String, Object> internalDescribe(Long id) {
        FileObject fo = findFile(id);
        boolean objectPresent;
        try {
            String[] parts = splitStorageKey(fo.getStorageKey());
            objectPresent = storage.exists(parts[0], parts[1]);
        } catch (Exception e) {
            objectPresent = false;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", fo.getId());
        out.put("scanStatus", fo.getScanStatus());
        out.put("classification", fo.getClassification());
        out.put("employeeId", fo.getEmployeeId());
        out.put("originalName", fo.getOriginalName());
        out.put("contentType", fo.getContentType());
        out.put("sizeBytes", fo.getSizeBytes());
        out.put("objectPresent", objectPresent);
        out.put("downloadable", isDownloadable(fo.getScanStatus()) && objectPresent);
        out.put("scanDetail", fo.getScanDetail());
        out.put("scannedAt", fo.getScannedAt() == null ? null : fo.getScannedAt().toString());
        out.put("releasedAt", fo.getReleasedAt() == null ? null : fo.getReleasedAt().toString());
        // S2S only — People stores photo_object_key / document links from this key.
        out.put("storageKey", fo.getStorageKey());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long requesterEmployeeId, int page, int size, boolean all) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        Page<FileObject> result;
        if (all && canViewDocuments(requesterEmployeeId)) {
            result = repository.findAllByOrderByIdDesc(PageRequest.of(p, s));
        } else {
            String by = requesterEmployeeId == null ? "" : String.valueOf(requesterEmployeeId);
            result = repository.findByUploadedByOrderByIdDesc(by, PageRequest.of(p, s));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", result.getContent().stream().map(this::toMetadata).toList());
        out.put("page", p);
        out.put("size", s);
        out.put("totalElements", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        return out;
    }

    /** Authorize + issue short-lived download URL (guide §8). */
    public DownloadUrlResponse issueDownloadUrl(Long id, Long requesterEmployeeId) {
        FileObject fo = findFile(id);
        assertCanAccess(fo, requesterEmployeeId, true);
        assertReleased(fo);
        DownloadTokenService.IssuedToken issued =
                downloadTokens.issue(fo.getId(), requesterEmployeeId);
        auditService.record(requesterEmployeeId, "DOWNLOAD_URL", "FileObject", String.valueOf(id), null,
                Map.of("expiresAt", issued.expiresAt().toString(), "scanStatus", fo.getScanStatus(),
                        "classification", fo.getClassification()));
        String url = "/api/files/" + id + "/content?token="
                + java.net.URLEncoder.encode(issued.token(), java.nio.charset.StandardCharsets.UTF_8);
        return new DownloadUrlResponse(id, url, issued.token(), issued.expiresAt());
    }

    /**
     * Verify short-lived token, audit access, and open the released object stream (guide §8).
     */
    @Transactional(readOnly = true)
    public OpenedDownload openDownloadWithToken(Long id, String token) throws IOException {
        FileObject fo = findFile(id);
        DownloadTokenService.Claims claims = downloadTokens.verify(token, id);
        assertReleased(fo);
        auditService.record(claims.requesterEmployeeId(), "DOWNLOAD", "FileObject", String.valueOf(id), null,
                Map.of("storageKey", fo.getStorageKey(), "scanStatus", fo.getScanStatus(),
                        "via", "short-lived-token"));
        String[] parts = splitStorageKey(fo.getStorageKey());
        return new OpenedDownload(toMetadata(fo), storage.load(parts[0], parts[1]));
    }

    public record OpenedDownload(FileMetadataResponse metadata, InputStream stream) {}

    /** Legacy session download — still gated by quarantine + ACL; prefers short-lived URL path. */
    @Transactional(readOnly = true)
    public InputStream openDownload(Long id, Long requesterEmployeeId) throws IOException {
        FileObject fo = findFile(id);
        assertCanAccess(fo, requesterEmployeeId, true);
        assertReleased(fo);
        auditService.record(requesterEmployeeId, "DOWNLOAD", "FileObject", String.valueOf(id), null,
                Map.of("storageKey", fo.getStorageKey(), "scanStatus", fo.getScanStatus(),
                        "via", "session"));
        String[] parts = splitStorageKey(fo.getStorageKey());
        return storage.load(parts[0], parts[1]);
    }

    /**
     * Async scan worker. Persists REJECTED durably and does <b>not</b> rethrow after that
     * (inbox can ack — guide §7 durable processing).
     *
     * @return terminal scan status
     */
    public String processScan(Long fileId) {
        FileObject fo = repository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        if (SCAN_RELEASED.equals(fo.getScanStatus()) || SCAN_REJECTED.equals(fo.getScanStatus())) {
            return fo.getScanStatus();
        }
        fo.setScanStatus(SCAN_SCANNING);
        repository.save(fo);

        try {
            String[] parts = splitStorageKey(fo.getStorageKey());
            byte[] bytes;
            try (InputStream in = storage.load(parts[0], parts[1])) {
                bytes = in.readAllBytes();
            }
            if (bytes.length != fo.getSizeBytes() && fo.getSizeBytes() > 0) {
                // allow minor variance only if size was unknown historically
                log.debug("Scan size mismatch fileId={} meta={} actual={}",
                        fileId, fo.getSizeBytes(), bytes.length);
            }
            assertMagicMatches(fo.getContentType(), bytes);
            assertDocumentStructure(fo.getContentType(), bytes);
            // Heuristic malware markers (demo scanner — replace with ClamAV/ICAP in hardened deploys).
            assertNoMalwareMarkers(bytes);

            fo.setScanStatus(SCAN_CLEAN);
            fo.setScanDetail("heuristic-clean");
            fo.setScannedAt(Instant.now());
            promoteToReleased(fo);
            fo.setScanStatus(SCAN_RELEASED);
            fo.setReleasedAt(Instant.now());
            repository.save(fo);
            auditService.record(fo.getEmployeeId(), "SCAN_RELEASE", "FileObject", String.valueOf(fileId),
                    SCAN_SCANNING, fo.getScanStatus());
            return SCAN_RELEASED;
        } catch (Exception e) {
            fo.setScanStatus(SCAN_REJECTED);
            fo.setScanDetail(truncate(e.getMessage(), 2000));
            fo.setScannedAt(Instant.now());
            repository.save(fo);
            auditService.record(fo.getEmployeeId(), "SCAN_REJECT", "FileObject", String.valueOf(fileId),
                    SCAN_SCANNING, fo.getScanStatus());
            log.warn("File scan rejected id={}: {}", fileId, e.getMessage());
            return SCAN_REJECTED;
        }
    }

    public void delete(Long id, Long requesterEmployeeId) throws IOException {
        FileObject fo = findFile(id);
        assertCanAccess(fo, requesterEmployeeId, false);
        if (!isOwner(fo, requesterEmployeeId) && !canManageDocuments(requesterEmployeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden: cannot delete this file");
        }
        String[] parts = splitStorageKey(fo.getStorageKey());
        try {
            storage.delete(parts[0], parts[1]);
        } catch (Exception e) {
            log.warn("Storage delete failed for {}: {}", fo.getStorageKey(), e.getMessage());
        }
        repository.delete(fo);
        auditService.record(requesterEmployeeId, "DELETE", "FileObject", String.valueOf(id),
                Map.of("storageKey", fo.getStorageKey(), "scanStatus", fo.getScanStatus()), null);
    }

    /** Retention: remove REJECTED rows older than configured days + their objects. */
    public int purgeRejectedOlderThanRetention() {
        Instant cutoff = Instant.now().minus(rejectedRetentionDays, ChronoUnit.DAYS);
        List<FileObject> stale = repository.findByScanStatusAndCreatedAtBefore(SCAN_REJECTED, cutoff);
        int n = 0;
        for (FileObject fo : stale) {
            try {
                String[] parts = splitStorageKey(fo.getStorageKey());
                storage.delete(parts[0], parts[1]);
            } catch (Exception e) {
                log.warn("Retention delete object failed {}: {}", fo.getStorageKey(), e.getMessage());
            }
            repository.delete(fo);
            n++;
        }
        return n;
    }

    /** Re-enqueue scans stuck in QUARANTINED/SCANNING (broker/worker recovery). */
    public int requeueStuckScans(Instant olderThan) {
        List<FileObject> stuck = repository.findByScanStatusInAndCreatedAtBefore(
                List.of(SCAN_QUARANTINED, SCAN_SCANNING), olderThan);
        int n = 0;
        for (FileObject fo : stuck) {
            if (outboxWriter.getIfAvailable() == null) {
                processScan(fo.getId());
            } else {
                enqueueScan(fo);
            }
            n++;
        }
        if (n > 0) {
            drainOutboxAfterCommit();
        }
        return n;
    }

    private void enqueueScan(FileObject saved) {
        OutboxWriter writer = outboxWriter.getIfAvailable();
        if (writer == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fileId", saved.getId());
        payload.put("storageKey", saved.getStorageKey());
        PlatformEvent event = PlatformEvent.of(
                EventTypes.FILE_SCAN_COMMAND,
                String.valueOf(saved.getId()),
                1L,
                CorrelationContext.getOrCreate(),
                payload);
        writer.enqueue(event, PlatformQueues.FILE_SCAN);
    }

    private void drainOutboxAfterCommit() {
        OutboxRelay relay = outboxRelay.getIfAvailable();
        if (relay == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        relay.drainBatch();
                    } catch (Exception e) {
                        log.debug("Outbox drain after file upload: {}", e.getMessage());
                    }
                }
            });
        } else {
            relay.drainBatch();
        }
    }

    private void promoteToReleased(FileObject fo) throws IOException {
        String[] parts = splitStorageKey(fo.getStorageKey());
        String bucket = parts[0];
        String currentKey = parts[1];
        if (currentKey.startsWith(PREFIX_RELEASED)) {
            return;
        }
        String uuid = currentKey.startsWith(PREFIX_QUARANTINE)
                ? currentKey.substring(PREFIX_QUARANTINE.length())
                : currentKey;
        String releasedKey = PREFIX_RELEASED + uuid;
        storage.copy(bucket, currentKey, releasedKey, fo.getContentType(), fo.getSizeBytes());
        try {
            storage.delete(bucket, currentKey);
        } catch (Exception e) {
            log.warn("Failed to delete quarantine object {}/{}: {}", bucket, currentKey, e.getMessage());
        }
        fo.setStorageKey(bucket + "/" + releasedKey);
    }

    private static void assertReleased(FileObject fo) {
        String status = fo.getScanStatus() == null ? SCAN_QUARANTINED : fo.getScanStatus();
        if (isDownloadable(status)) {
            return;
        }
        if (SCAN_REJECTED.equals(status) || SCAN_INFECTED.equals(status) || SCAN_FAILED.equals(status)) {
            throw new ResponseStatusException(HttpStatus.GONE, "File rejected by content scan");
        }
        throw new ResponseStatusException(HttpStatus.LOCKED, "File still in quarantine/scan");
    }

    private static boolean isDownloadable(String status) {
        return SCAN_RELEASED.equals(status) || SCAN_CLEAN.equals(status);
    }

    private void assertCanUpload(Long actorId, Long subjectEmployeeId, String classification) {
        if (actorId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Employee identity required");
        }
        String c = normalizeClassification(classification);
        boolean docsCreate = hasPermission(actorId, "documents", "create");
        boolean empEdit = hasPermission(actorId, "employees", "edit")
                || hasPermission(actorId, "employees", "create");
        boolean selfPhoto = isPhotoClassification(c)
                && (subjectEmployeeId == null || subjectEmployeeId.equals(actorId))
                && hasPermission(actorId, "profile_personal", "edit");
        if (isSensitiveClassification(c) && !docsCreate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: documents:create required to upload " + c + " files");
        }
        if (isPhotoClassification(c)) {
            if (!docsCreate && !empEdit && !selfPhoto) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Forbidden: cannot upload employee photo");
            }
        } else if (!docsCreate && !empEdit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: documents:create or employees:edit required to upload files");
        }
        if (subjectEmployeeId != null && !subjectEmployeeId.equals(actorId)
                && !empEdit && !docsCreate) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Forbidden: cannot upload documents for another employee");
        }
    }

    private static boolean isSensitiveClassification(String classification) {
        return "CONFIDENTIAL".equals(classification)
                || "HR_ONLY".equals(classification)
                || "SENSITIVE".equals(classification);
    }

    private void assertCanAccess(FileObject fo, Long requesterEmployeeId, boolean forDownload) {
        // DOC-02: null identity is deny (no "trusted legacy" bypass).
        if (requesterEmployeeId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Employee identity required");
        }
        if (isOwner(fo, requesterEmployeeId)) {
            return;
        }
        if (fo.getEmployeeId() != null && fo.getEmployeeId().equals(requesterEmployeeId)) {
            return;
        }
        String classification = fo.getClassification() == null ? "GENERAL" : fo.getClassification();
        // Profile photos are identity assets — anyone who can view employees may see them in
        // header / directory / details (unlike confidential HR documents).
        if (isPhotoClassification(classification)
                && (hasPermission(requesterEmployeeId, "employees", "view")
                || hasPermission(requesterEmployeeId, "employees", "edit"))) {
            return;
        }
        // DOC-01: employees:view alone must NOT unlock all documents.
        if ("PUBLIC".equalsIgnoreCase(classification) && hasPermission(requesterEmployeeId, "documents", "view")) {
            return;
        }
        if (isHrDocumentsViewer(requesterEmployeeId, classification)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                forDownload ? "Forbidden: file belongs to another user"
                        : "Forbidden: cannot view another user's file metadata");
    }

    /**
     * People attach — bind a PHOTO (or document) to the employee record so the subject
     * can always download even when HR uploaded before the employee id existed.
     */
    @Transactional
    public Map<String, Object> bindEmployee(Long fileId, Long employeeId) {
        if (employeeId == null || employeeId <= 0) {
            throw new IllegalArgumentException("employeeId is required");
        }
        FileObject fo = findFile(fileId);
        fo.setEmployeeId(employeeId);
        repository.save(fo);
        return internalDescribe(fileId);
    }

    /** HR / confidential document access requires documents:view (not employees:view). */
    private boolean isHrDocumentsViewer(Long employeeId, String classification) {
        if (!hasPermission(employeeId, "documents", "view")) {
            return false;
        }
        String c = classification == null ? "GENERAL" : classification.toUpperCase();
        // Sensitive classifications need documents:view; PUBLIC already handled above.
        return switch (c) {
            case "CONFIDENTIAL", "HR_ONLY", "SENSITIVE", "PAYROLL" ->
                    hasPermission(employeeId, "documents", "view");
            default -> hasPermission(employeeId, "documents", "view");
        };
    }

    private boolean isOwner(FileObject fo, Long requesterEmployeeId) {
        if (requesterEmployeeId == null) {
            return false;
        }
        String owner = fo.getUploadedBy();
        return owner != null && owner.equals(String.valueOf(requesterEmployeeId));
    }

    private boolean canViewDocuments(Long employeeId) {
        return hasPermission(employeeId, "documents", "view");
    }

    private boolean canManageDocuments(Long employeeId) {
        return hasPermission(employeeId, "documents", "delete")
                || hasPermission(employeeId, "documents", "create")
                || hasPermission(employeeId, "employees", "edit");
    }

    private boolean hasPermission(Long employeeId, String feature, String action) {
        if (employeeId == null) {
            return false;
        }
        PeopleDirectoryClient people = peopleDirectory.getIfAvailable();
        if (people == null) {
            return false;
        }
        try {
            Object raw = people.authz(employeeId).get("permissions");
            Set<String> keys = new HashSet<>();
            if (raw instanceof java.util.Collection<?> col) {
                for (Object o : col) {
                    if (o != null) {
                        keys.add(String.valueOf(o).toLowerCase(Locale.ROOT));
                    }
                }
            }
            Object wild = people.authz(employeeId).get("grantsWildcard");
            if (wild instanceof Boolean b && b) {
                return true;
            }
            return HrmsPermissionClient.grants(keys, feature, action);
        } catch (Exception e) {
            log.debug("People authz lookup failed for emp={}: {}", employeeId, e.getMessage());
            return false;
        }
    }

    @Transactional(readOnly = true)
    public String getContentType(Long id) {
        return findFile(id).getContentType();
    }

    public void ensureBucket() throws IOException {
        storage.ensureBucket(bucket);
    }

    /** Reject client-labelled types that do not match file magic bytes (e.g. MZ exe as PDF). */
    static void assertMagicMatches(String contentType, byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            throw new IllegalArgumentException("File content too short to validate");
        }
        if (bytes[0] == 'M' && bytes[1] == 'Z') {
            throw new IllegalArgumentException("Executable content is not allowed");
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 64), java.nio.charset.StandardCharsets.ISO_8859_1)
                .trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<script")
                || head.startsWith("<?xml")) {
            throw new IllegalArgumentException("HTML/script content is not allowed for this upload type");
        }
        if ("application/pdf".equals(contentType) && !(bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F')) {
            throw new IllegalArgumentException("Content does not look like a PDF");
        }
        if ("image/png".equals(contentType) && !(bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G')) {
            throw new IllegalArgumentException("Content does not look like a PNG");
        }
        if ("image/jpeg".equals(contentType) && !(bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8)) {
            throw new IllegalArgumentException("Content does not look like a JPEG");
        }
        if (contentType != null && contentType.contains("wordprocessingml")
                && !(bytes[0] == 'P' && bytes[1] == 'K')) {
            throw new IllegalArgumentException("Content does not look like a DOCX (ZIP) archive");
        }
    }

    /** Deeper structure checks beyond MIME + magic (guide §8). */
    static void assertDocumentStructure(String contentType, byte[] bytes) {
        if ("application/pdf".equals(contentType)) {
            String tail = new String(
                    bytes,
                    Math.max(0, bytes.length - 1024),
                    Math.min(1024, bytes.length),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            if (!tail.contains("%%EOF") && !tail.contains("%%eof")) {
                throw new IllegalArgumentException("PDF structure invalid (missing %%EOF)");
            }
            return;
        }
        if (contentType != null && contentType.contains("wordprocessingml")) {
            boolean hasContentTypes = false;
            try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                int checked = 0;
                while ((entry = zis.getNextEntry()) != null && checked < 64) {
                    checked++;
                    if ("[Content_Types].xml".equals(entry.getName())) {
                        hasContentTypes = true;
                        break;
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("DOCX structure invalid (not a readable ZIP)", e);
            }
            if (!hasContentTypes) {
                throw new IllegalArgumentException("DOCX structure invalid (missing [Content_Types].xml)");
            }
        }
    }

    static void assertNoMalwareMarkers(byte[] bytes) {
        // Lightweight demo heuristics — production should call ClamAV/ICAP.
        String sample = new String(bytes, 0, Math.min(bytes.length, 8192), java.nio.charset.StandardCharsets.ISO_8859_1)
                .toLowerCase(Locale.ROOT);
        if (sample.contains("x5o!p%@ap") || sample.contains("eicar-standard-antivirus-test-file")) {
            throw new IllegalArgumentException("Malware signature detected (EICAR)");
        }
    }

    static String normalizeContentType(String raw, String filename) {
        String ct = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        if ("image/jpg".equals(ct)) {
            ct = "image/jpeg";
        }
        if (ct.isBlank() || "application/octet-stream".equals(ct)) {
            String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
            if (name.endsWith(".pdf")) return "application/pdf";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".docx")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
        }
        return ct.isBlank() ? null : ct;
    }

    /**
     * Guide §8 upload gates — size + MIME by classification.
     * Documents: PDF/JPG/PNG/DOCX ≤ 10 MB. Photos: JPG/PNG ≤ 2 MB.
     */
    static void assertUploadConstraints(
            String classification, String contentType, long sizeBytes, String rawContentType) {
        boolean photo = isPhotoClassification(classification);
        long max = photo ? MAX_PHOTO_BYTES : MAX_DOCUMENT_BYTES;
        if (sizeBytes > max) {
            throw new IllegalArgumentException(photo
                    ? "Photo exceeds maximum allowed size of 2 MB (got " + sizeBytes + " bytes)"
                    : "File exceeds maximum allowed size of 10 MB (got " + sizeBytes + " bytes)");
        }
        Set<String> allowed = photo ? PHOTO_CONTENT_TYPES : DOCUMENT_CONTENT_TYPES;
        if (contentType == null || !allowed.contains(contentType)) {
            String shown = rawContentType == null || rawContentType.isBlank() ? contentType : rawContentType;
            throw new IllegalArgumentException(photo
                    ? "Unsupported photo type '" + shown + "'. Allowed: JPG, PNG (max 2 MB)"
                    : "Unsupported content type '" + shown + "'. Allowed: PDF, JPG, PNG, DOCX (max 10 MB)");
        }
    }

    static boolean isPhotoClassification(String classification) {
        String c = classification == null ? "" : classification.trim().toUpperCase(Locale.ROOT);
        return "PHOTO".equals(c) || "AVATAR".equals(c) || "PROFILE_PHOTO".equals(c);
    }

    private static String normalizeClassification(String raw) {
        if (raw == null || raw.isBlank()) {
            return "GENERAL";
        }
        String c = raw.trim().toUpperCase(Locale.ROOT);
        return switch (c) {
            case "GENERAL", "PUBLIC", "INTERNAL", "CONFIDENTIAL", "HR_ONLY", "SENSITIVE", "PAYROLL" -> c;
            case "PHOTO", "AVATAR", "PROFILE_PHOTO" -> "PHOTO";
            default -> "GENERAL";
        };
    }

    private FileObject findFile(Long id) {
        return repository.findById(id)
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
                fo.getOriginalName(),
                fo.getContentType(),
                fo.getSizeBytes(),
                fo.getUploadedBy(),
                fo.getEmployeeId(),
                fo.getScanStatus(),
                fo.getClassification(),
                fo.getScanDetail(),
                fo.getScannedAt(),
                fo.getReleasedAt(),
                fo.getCreatedAt(),
                "/api/files/" + fo.getId() + "/download-url"
        );
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
