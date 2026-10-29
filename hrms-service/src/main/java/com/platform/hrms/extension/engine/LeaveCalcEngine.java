package com.platform.hrms.extension.engine;

import com.platform.hrms.domain.LeavePolicy;
import com.platform.hrms.domain.LeaveType;
import com.platform.hrms.domain.StoreShiftCoverage;
import com.platform.hrms.extension.config.TenantConfigLoader;
import com.platform.hrms.infrastructure.persistence.LeaveTypeRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LeaveCalcEngine {

    private final TenantConfigLoader configLoader;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveCalcEngine(TenantConfigLoader configLoader, LeaveTypeRepository leaveTypeRepository) {
        this.configLoader = configLoader;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    public Map<String, Object> currentPolicyView() {
        LeavePolicy policy = configLoader.loadLeavePolicyOrDefault();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("annualDays", policy.getAnnualDays());
        view.put("workWeekDays", policy.getWorkWeekDays());
        view.put("workingDays", policy.getWorkingDays());
        view.put("noticeMode", policy.getNoticeMode());
        view.put("defaultNoticeDays", policy.getDefaultNoticeDays());
        view.put("unionNoticeDays", policy.getUnionNoticeDays());
        view.put("nonUnionNoticeDays", policy.getNonUnionNoticeDays());
        view.put("requireCoverageCheck", policy.isRequireCoverageCheck());
        view.put("coverageMode", policy.getCoverageMode());
        view.put("locationField", policy.getLocationField());
        view.put("shiftField", policy.getShiftField());
        view.put("requireReliefOnCoverageFail", policy.isRequireReliefOnCoverageFail());
        view.put("defaultLeaveTypeCode", policy.getDefaultLeaveTypeCode());
        view.put("approvalChain", policy.getApprovalChain());
        view.put("leaveTypes", leaveTypeRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::typeView)
                .collect(Collectors.toList()));
        view.put("coverageRules", configLoader.allCoverageRules().stream()
                .map(this::coverageView)
                .collect(Collectors.toList()));
        view.put("source", "tenant_db leave_policy + leave_type + coverage (config/data)");
        return view;
    }

    private Map<String, Object> typeView(LeaveType t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("paid", t.isPaid());
        m.put("requiresBalance", t.isRequiresBalance());
        m.put("allowWhenZeroBalance", t.isAllowWhenZeroBalance());
        m.put("allowWhenCoverageFail", t.isAllowWhenCoverageFail());
        m.put("deductBalance", t.isDeductBalance());
        return m;
    }

    private Map<String, Object> coverageView(StoreShiftCoverage c) {
        return Map.of(
                "storeId", c.getStoreId(),
                "shiftPattern", c.getShiftPattern(),
                "minStaff", c.getMinStaff()
        );
    }
}
