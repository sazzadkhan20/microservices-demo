package com.platform.files.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** Dev fallback when MinIO is unavailable — still keyed by bucket/object. */
@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local")
public class LocalObjectStorage implements ObjectStorage {

    private final String root;

    public LocalObjectStorage(@Value("${file.storage.root:./data/files}") String root) {
        this.root = root;
    }

    @Override
    public void ensureBucket(String bucket) throws IOException {
        Files.createDirectories(Paths.get(root).resolve(bucket));
    }

    @Override
    public String store(String bucket, String objectKey, InputStream inputStream, String contentType, long sizeBytes)
            throws IOException {
        ensureBucket(bucket);
        Path target = Paths.get(root).resolve(bucket).resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        return objectKey;
    }

    @Override
    public InputStream load(String bucket, String objectKey) throws IOException {
        return Files.newInputStream(Paths.get(root).resolve(bucket).resolve(objectKey));
    }

    @Override
    public void delete(String bucket, String objectKey) throws IOException {
        Files.deleteIfExists(Paths.get(root).resolve(bucket).resolve(objectKey));
    }
}
