package com.platform.files.infrastructure.persistence;

import com.platform.files.domain.FileObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Optional<FileObject> findByIdAndTenantId(Long id, String tenantId);

    Page<FileObject> findByTenantIdOrderByIdDesc(String tenantId, Pageable pageable);

    Page<FileObject> findByTenantIdAndUploadedByOrderByIdDesc(String tenantId, String uploadedBy, Pageable pageable);
}
