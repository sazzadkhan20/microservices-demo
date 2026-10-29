package com.platform.files;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * File service — multipart upload, metadata storage, and streaming download.
 *
 * <p>Layers:
 * <ul>
 *   <li>{@code api}            — REST adapters + DTOs</li>
 *   <li>{@code application}    — use cases</li>
 *   <li>{@code domain}         — JPA entity</li>
 *   <li>{@code infrastructure} — local-FS storage + persistence + tenant filter</li>
 *   <li>{@code config}         — OpenAPI + properties</li>
 * </ul>
 *
 * <p>Storage defaults to {@code ./data/files/{tenantId}/{uuid}}.
 * Override {@code FILE_STORAGE_ROOT} for a different base path.
 * MinIO can be wired later via a {@code StorageStrategy} implementation swap.
 */
@SpringBootApplication
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
