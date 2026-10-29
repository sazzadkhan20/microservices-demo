package com.platform.files.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local-filesystem storage implementation.
 *
 * <p>Files are stored under {@code {root}/{tenantId}/{uuid}}.
 * A future MinIO implementation can be swapped in without touching the service layer.
 */
@Component
public class LocalFileStorage {

    private final String root;

    public LocalFileStorage(@Value("${file.storage.root:./data/files}") String root) {
        this.root = root;
    }

    /**
     * Persists {@code inputStream} at {@code root/storageKey} and returns {@code storageKey}.
     *
     * @param storageKey relative path, e.g. {@code "tenantABC/550e8400-e29b..."}
     */
    public String store(String storageKey, InputStream inputStream) throws IOException {
        Path target = Paths.get(root).resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return storageKey;
    }

    /** Opens an {@link InputStream} for the stored object. */
    public InputStream load(String storageKey) throws IOException {
        Path target = Paths.get(root).resolve(storageKey);
        return Files.newInputStream(target);
    }

    /** Deletes the stored object; silently ignores missing files. */
    public void delete(String storageKey) throws IOException {
        Path target = Paths.get(root).resolve(storageKey);
        Files.deleteIfExists(target);
    }
}
