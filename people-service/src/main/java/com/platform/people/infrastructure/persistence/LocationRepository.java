package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}
