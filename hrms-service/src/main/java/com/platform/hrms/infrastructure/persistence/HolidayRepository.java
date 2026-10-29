package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
}
