package com.platform.leave.extension.config;

import com.platform.leave.domain.LeaveBlackout;
import com.platform.leave.domain.LeavePolicy;
import com.platform.leave.domain.StoreShiftCoverage;
import com.platform.leave.domain.TenantSettings;
import com.platform.leave.infrastructure.persistence.LeaveBlackoutRepository;
import com.platform.leave.infrastructure.persistence.LeavePolicyRepository;
import com.platform.leave.infrastructure.persistence.StoreShiftCoverageRepository;
import com.platform.leave.infrastructure.persistence.TenantSettingsRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads Leave-owned {@code leave_settings} (approval / leave notification knobs). */
@Component
public class TenantConfigLoader {

    private final LeavePolicyRepository leavePolicyRepository;
    private final StoreShiftCoverageRepository coverageRepository;
    private final LeaveBlackoutRepository blackoutRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    public TenantConfigLoader(
            LeavePolicyRepository leavePolicyRepository,
            StoreShiftCoverageRepository coverageRepository,
            LeaveBlackoutRepository blackoutRepository,
            TenantSettingsRepository tenantSettingsRepository) {
        this.leavePolicyRepository = leavePolicyRepository;
        this.coverageRepository = coverageRepository;
        this.blackoutRepository = blackoutRepository;
        this.tenantSettingsRepository = tenantSettingsRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> loadSettingsMap() {
        return tenantSettingsRepository.findAll().stream()
                .findFirst()
                .map(TenantSettings::getSettings)
                .map(s -> s == null ? Map.<String, Object>of() : new HashMap<>(s))
                .orElseGet(Map::of);
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
        fallback.setApprovalChain(com.platform.leave.application.ApprovalChainSupport.defaultChain());
        fallback.setUpdatedAt(Instant.now());
        return fallback;
    }
}
