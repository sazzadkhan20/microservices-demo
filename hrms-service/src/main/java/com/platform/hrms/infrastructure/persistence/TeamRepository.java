package com.platform.hrms.infrastructure.persistence;

import com.platform.hrms.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
