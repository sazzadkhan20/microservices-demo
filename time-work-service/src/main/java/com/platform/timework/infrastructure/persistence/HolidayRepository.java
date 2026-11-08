package com.platform.timework.infrastructure.persistence;

import com.platform.timework.domain.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {
}
