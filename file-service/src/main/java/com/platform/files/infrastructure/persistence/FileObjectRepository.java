package com.platform.files.infrastructure.persistence;

import com.platform.files.domain.FileObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Page<FileObject> findAllByOrderByIdDesc(Pageable pageable);

    Page<FileObject> findByUploadedByOrderByIdDesc(String uploadedBy, Pageable pageable);

    List<FileObject> findByScanStatusAndCreatedAtBefore(String scanStatus, Instant cutoff);

    List<FileObject> findByScanStatusInAndCreatedAtBefore(Collection<String> statuses, Instant cutoff);
}
