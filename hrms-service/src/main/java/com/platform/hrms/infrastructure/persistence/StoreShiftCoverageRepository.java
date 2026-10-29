package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.StoreShiftCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreShiftCoverageRepository extends JpaRepository<StoreShiftCoverage, Long> {
    Optional<StoreShiftCoverage> findByStoreIdAndShiftPattern(String storeId, String shiftPattern);

    Optional<StoreShiftCoverage> findFirstByStoreId(String storeId);
}
