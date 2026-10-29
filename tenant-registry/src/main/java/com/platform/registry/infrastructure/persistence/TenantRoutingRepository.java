package com.platform.registry.infrastructure.persistence;

import com.platform.registry.domain.TenantRouting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRoutingRepository extends JpaRepository<TenantRouting, Long> {

    Optional<TenantRouting> findByServiceNameAndTenantId(String serviceName, String tenantId);

    List<TenantRouting> findByTenantId(String tenantId);

    boolean existsByTenantId(String tenantId);

    List<TenantRouting> findAllByOrderByTenantIdAscServiceNameAsc();
}
