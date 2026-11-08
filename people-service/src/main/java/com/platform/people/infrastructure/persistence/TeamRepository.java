package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {
}
