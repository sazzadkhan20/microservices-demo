package com.platform.files.infrastructure.persistence;

import com.platform.files.domain.FileObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Optional<FileObject> findByIdAndTenantId(Long id, String tenantId);
}
