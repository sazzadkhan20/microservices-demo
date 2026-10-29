package com.platform.hrms.extension.config;

import com.platform.hrms.domain.LeaveBlackout;
import com.platform.hrms.domain.LeavePolicy;
import com.platform.hrms.domain.StoreShiftCoverage;
import com.platform.hrms.domain.TenantFieldDefinition;
import com.platform.hrms.infrastructure.persistence.LeaveBlackoutRepository;
import com.platform.hrms.infrastructure.persistence.LeavePolicyRepository;
import com.platform.hrms.infrastructure.persistence.StoreShiftCoverageRepository;
import com.platform.hrms.infrastructure.persistence.TenantFieldDefinitionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads tenant business configuration from the routed tenant DB.
 * New business = new config rows. No HRMS redeploy.
 */
@Component
public class TenantConfigLoader {

    private final TenantFieldDefinitionRepository fieldDefinitionRepository;
    private final LeavePolicyRepository leavePolicyRepository;
    private final StoreShiftCoverageRepository coverageRepository;
    private final LeaveBlackoutRepository blackoutRepository;

    public TenantConfigLoader(
            TenantFieldDefinitionRepository fieldDefinitionRepository,
            LeavePolicyRepository leavePolicyRepository,
            StoreShiftCoverageRepository coverageRepository,
            LeaveBlackoutRepository blackoutRepository) {
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.leavePolicyRepository = leavePolicyRepository;
        this.coverageRepository = coverageRepository;
        this.blackoutRepository = blackoutRepository;
    }

    @Transactional(readOnly = true)
    public List<TenantFieldDefinition> loadFieldDefinitions() {
        return fieldDefinitionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public LeavePolicy loadLeavePolicyOrDefault() {
        return leavePolicyRepository.findAll().stream().findFirst().orElseGet(this::platformDefaultPolicy);
    }

    @Transactional(readOnly = true)
    public Optional<StoreShiftCoverage> findCoverage(String storeId, String shiftPattern) {
        if (storeId == null || shiftPattern == null) {
            return Optional.empty();
        }
        return coverageRepository.findByStoreIdAndShiftPattern(storeId, shiftPattern);
    }

    @Transactional(readOnly = true)
    public List<StoreShiftCoverage> allCoverageRules() {
        return coverageRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<LeaveBlackout> overlappingBlackouts(String storeId, java.time.LocalDate start, java.time.LocalDate end) {
        return blackoutRepository.findOverlapping(storeId, start, end);
    }

    private LeavePolicy platformDefaultPolicy() {
        LeavePolicy fallback = new LeavePolicy();
        fallback.setAnnualDays(12);
        fallback.setWorkWeekDays(5);
        fallback.setWorkingDays(List.of("MON", "TUE", "WED", "THU", "FRI"));
        fallback.setUnionNoticeDays(7);
        fallback.setNonUnionNoticeDays(3);
        fallback.setRequireCoverageCheck(false);
        fallback.setNoticeMode(LeavePolicy.NOTICE_NONE);
        fallback.setCoverageMode(LeavePolicy.COVERAGE_NONE);
        fallback.setRequireReliefOnCoverageFail(false);
        fallback.setApprovalChain(List.of(Map.of("step", 1, "role", "HR")));
        fallback.setUpdatedAt(Instant.now());
        return fallback;
    }
}
