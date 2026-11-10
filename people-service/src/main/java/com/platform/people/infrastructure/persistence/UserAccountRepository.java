package com.platform.people.infrastructure.persistence;

import com.platform.people.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmployeeId(Long employeeId);

    Optional<UserAccount> findByEmailIgnoreCase(String email);

    Optional<UserAccount> findByGoogleSub(String googleSub);

    boolean existsByEmployeeId(Long employeeId);
}
