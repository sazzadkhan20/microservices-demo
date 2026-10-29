package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}
