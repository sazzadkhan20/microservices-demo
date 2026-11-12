package com.platform.leave.application;

import com.platform.leave.client.people.EmployeeView;
import com.platform.leave.client.people.PeopleEmployeeGateway;
import com.platform.leave.domain.LeaveApplication;
import com.platform.leave.domain.LeaveApproval;
import com.platform.leave.domain.LeaveCategory;
import com.platform.leave.domain.LeavePolicy;
import com.platform.leave.infrastructure.persistence.LeaveApprovalRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FR-LV-12/13 approval chain: which chain applies (category → policy → default), the reporting-manager
 * precondition, the approver preview, and materialising the steps with the skip rules. Extracted from
 * {@link LeaveApplicationService} unchanged, so that class keeps to one responsibility (SRP size budget).
 */
@Component
class LeaveApprovalChainBuilder {

    private final PeopleEmployeeGateway employeeRepository;
    private final LeaveApprovalRepository approvalRepository;
    private final ApprovalAuthorityService approvalAuthorityService;

    LeaveApprovalChainBuilder(PeopleEmployeeGateway employeeRepository, LeaveApprovalRepository approvalRepository,
                              ApprovalAuthorityService approvalAuthorityService) {
        this.employeeRepository = employeeRepository;
        this.approvalRepository = approvalRepository;
        this.approvalAuthorityService = approvalAuthorityService;
    }

    List<Map<String, Object>> previewApprovers(EmployeeView employee, List<Map<String, Object>> chain) {
        List<Map<String, Object>> approvers = new ArrayList<>();
        Long requesterId = employee == null ? null : employee.getId();
        Long prevExpectedId = null;
        String prevRoleKey = null;
        int stepNum = 1;
        for (Map<String, Object> item : chain) {
            if (stepNum > ApprovalChainSupport.MAX_STEPS) break;
            String resolver = item.get("resolver") != null
                    ? String.valueOf(item.get("resolver")).trim().toUpperCase(Locale.ROOT) : "";
            String role = item.get("role") != null ? String.valueOf(item.get("role")).trim() : "ADMIN";
            if (resolver.isEmpty()) resolver = ApprovalChainSupport.defaultResolverForRole(role);
            List<String> roleCodes = roleCodesFrom(item.get("roleCodes"), role);
            Long expectedId = resolveExpectedApprover(employee, item);
            Long namedId = null;
            Object nid = item.get("namedEmployeeId");
            if (nid instanceof Number n) namedId = n.longValue();
            String roleKey = role.toUpperCase(Locale.ROOT)
                    + (namedId != null ? ":" + namedId : "")
                    + ":" + resolver;
            boolean rolePool = ApprovalAuthorityService.RESOLVER_RBAC_ROLE.equals(resolver)
                    || ApprovalAuthorityService.RESOLVER_DESIGNATION_OR_RBAC.equals(resolver);
            if (rolePool) {
                if (requesterId != null
                        && approvalAuthorityService.requesterHoldsStepRole(requesterId, role, roleCodes)) {
                    continue;
                }
                if (prevExpectedId != null
                        && approvalAuthorityService.employeeHoldsStepRole(prevExpectedId, role, roleCodes)) {
                    continue;
                }
                if (roleKey.equals(prevRoleKey)) {
                    continue;
                }
            }
            // Skip: holds no one / inactive / requester / same as previous step
            if (isPersonBoundResolver(resolver, role)) {
                if (expectedId == null) continue;
                EmployeeView exp = employeeRepository.findById(expectedId).orElse(null);
                if (exp == null || !exp.isActive()) continue;
                if (expectedId.equals(requesterId)) continue;
                if (expectedId.equals(prevExpectedId)) continue;
            } else if (expectedId != null) {
                EmployeeView exp = employeeRepository.findById(expectedId).orElse(null);
                if (exp == null || !exp.isActive()) continue;
                if (expectedId.equals(requesterId)) continue;
                if (expectedId.equals(prevExpectedId)) continue;
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("role", role);
            a.put("resolver", resolver);
            a.put("step", stepNum);
            a.put("expectedEmployeeId", expectedId);
            if (expectedId != null) {
                a.put("expectedName", employeeRepository.findById(expectedId).map(EmployeeView::getFullName).orElse(null));
            } else {
                a.put("expectedName", role.isBlank() ? "Approver" : role);
            }
            approvers.add(a);
            prevExpectedId = expectedId;
            prevRoleKey = roleKey;
            stepNum++;
        }
        return approvers;
    }

    /** Resolvers that require a concrete person — empty/inactive means skip the step. */
    private static boolean isPersonBoundResolver(String resolver, String role) {
        String r = resolver == null ? "" : resolver.trim().toUpperCase(Locale.ROOT);
        String roleU = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER.equals(r)
                || ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION.equals(r)
                || ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER.equals(r)
                || ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE.equals(r)
                || "MANAGER".equals(roleU)
                || "STORE_MANAGER".equals(roleU)
                || "MANAGER_OF_MANAGER".equals(roleU)
                || "NAMED_EMPLOYEE".equals(roleU);
    }

    /**
     * Category chain wins when present (per-type config); else leave_policy.approval_chain;
     * else production default Manager → Admin.
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> resolveApprovalChain(LeaveCategory category, LeavePolicy policy) {
        if (category != null && category.getApprovalChain() != null && !category.getApprovalChain().isBlank()) {
            try {
                return ApprovalChainSupport.parseAndValidateJson(category.getApprovalChain());
            } catch (IllegalArgumentException ex) {
                // Corrupt stored JSON — fall through rather than blocking historic applications.
            }
        }
        List<Map<String, Object>> chain = policy.getApprovalChain();
        if (chain == null || chain.isEmpty()) {
            return ApprovalChainSupport.defaultChain();
        }
        try {
            return ApprovalChainSupport.normalizeAndValidate(chain);
        } catch (IllegalArgumentException ex) {
            return ApprovalChainSupport.defaultChain();
        }
    }

    void assertReportingManagerPresent(EmployeeView employee, List<Map<String, Object>> chain) {
        boolean needsManager = false;
        for (Map<String, Object> item : chain) {
            String resolver = item.get("resolver") == null ? "" : String.valueOf(item.get("resolver")).trim().toUpperCase(Locale.ROOT);
            String role = item.get("role") == null ? "" : String.valueOf(item.get("role")).trim().toUpperCase(Locale.ROOT);
            if (ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER.equals(resolver)
                    || ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION.equals(resolver)
                    || "MANAGER".equals(role)
                    || "STORE_MANAGER".equals(role)) {
                needsManager = true;
                break;
            }
        }
        // Role defaults may still map MANAGER → REPORTING_MANAGER when chain omits resolver.
        if (!needsManager) {
            return;
        }
        if (employee.getManagerId() == null || employee.getManagerId() <= 0) {
            throw new IllegalStateException(
                    "Cannot request leave: employee has no reporting manager. "
                            + "Assign a manager before applying (required for the configured approval chain).");
        }
    }

    /**
     * FR-LV-12/13 — materialize approval chain with skip logic.
     * Skip when: empty/inactive person, approver is requester, same as previous step,
     * or requester holds a role-pool step (example: Fayaz's own request → Asman only).
     * Max 4 steps; throws if no steps remain.
     */
    void createApprovalSteps(LeaveApplication app, List<Map<String, Object>> chain, EmployeeView employee) {
        // Idempotent rebuild — never leave duplicate (application_id, step_order) rows.
        approvalRepository.deleteByApplicationId(app.getId());

        Long requesterId = app.getEmployeeId();
        List<LeaveApproval> steps = new ArrayList<>();
        Long prevExpectedId = null;
        String prevRoleKey = null;
        int stepNum = 1;

        for (Map<String, Object> item : chain) {
            if (stepNum > ApprovalChainSupport.MAX_STEPS) break;
            String resolver = item.get("resolver") != null
                    ? String.valueOf(item.get("resolver")).trim().toUpperCase(Locale.ROOT) : "";
            String role = item.get("role") != null ? String.valueOf(item.get("role")).trim() : "ADMIN";
            if (resolver.isEmpty()) {
                resolver = ApprovalChainSupport.defaultResolverForRole(role);
            }
            @SuppressWarnings("unchecked")
            List<String> roleCodes = roleCodesFrom(item.get("roleCodes"), role);

            Long expectedId = null;
            Long namedId = null;

            if (resolver.equals(ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE)) {
                Object nid = item.get("namedEmployeeId");
                if (nid instanceof Number n) namedId = n.longValue();
                expectedId = namedId;
            } else if (resolver.equals(ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER)
                    || resolver.equals(ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION)
                    || "MANAGER".equalsIgnoreCase(role) || "STORE_MANAGER".equalsIgnoreCase(role)) {
                expectedId = employee != null ? employee.getManagerId() : null;
            } else if (resolver.equals(ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER)) {
                expectedId = resolveManagerOfManager(employee);
            }

            String roleKey = role.toUpperCase(Locale.ROOT)
                    + (namedId != null ? ":" + namedId : "")
                    + ":" + resolver;

            // Role / designation pools — skip when requester (or previous expected person) holds the role.
            boolean rolePool = ApprovalAuthorityService.RESOLVER_RBAC_ROLE.equals(resolver)
                    || ApprovalAuthorityService.RESOLVER_DESIGNATION_OR_RBAC.equals(resolver);
            if (rolePool) {
                if (approvalAuthorityService.requesterHoldsStepRole(requesterId, role, roleCodes)) {
                    continue;
                }
                if (prevExpectedId != null
                        && approvalAuthorityService.employeeHoldsStepRole(prevExpectedId, role, roleCodes)) {
                    continue;
                }
                if (roleKey.equals(prevRoleKey)) {
                    continue;
                }
            }

            // Person-bound — skip empty / inactive / requester / same as previous.
            if (isPersonBoundResolver(resolver, role)) {
                if (expectedId == null) continue;
                EmployeeView exp = employeeRepository.findById(expectedId).orElse(null);
                if (exp == null || !exp.isActive()) continue;
                if (expectedId.equals(requesterId)) continue;
                if (expectedId.equals(prevExpectedId)) continue;
            } else if (expectedId != null) {
                EmployeeView exp = employeeRepository.findById(expectedId).orElse(null);
                if (exp == null || !exp.isActive()) continue;
                if (expectedId.equals(requesterId)) continue;
                if (expectedId.equals(prevExpectedId)) continue;
            }

            LeaveApproval a = new LeaveApproval();
            a.setApplicationId(app.getId());
            a.setStepOrder(stepNum);
            a.setApproverRole(role);
            a.setStatus(LeaveApproval.STATUS_PENDING);
            a.setResolver(resolver.isEmpty() ? null : resolver);
            a.setNamedEmployeeId(namedId);
            a.setExpectedApproverId(expectedId);
            a.setActedOnBehalf(false);
            approvalRepository.save(a);
            steps.add(a);
            prevExpectedId = expectedId;
            prevRoleKey = roleKey;
            stepNum++;
        }

        if (steps.isEmpty()) {
            throw new IllegalStateException("No valid approval steps could be created for this leave request. "
                    + "Check approval chain configuration and employee manager assignments.");
        }
    }

    private static List<String> roleCodesFrom(Object raw, String role) {
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim().toUpperCase(Locale.ROOT));
                }
            }
            if (!out.isEmpty()) return out;
        }
        if (role == null || role.isBlank()) return List.of();
        String r = role.toUpperCase(Locale.ROOT);
        return switch (r) {
            case "ADMIN" -> List.of("ADMIN", "SUPERADMIN");
            case "SUPER_ADMIN", "SUPERADMIN" -> List.of("SUPERADMIN");
            case "HR" -> List.of("HR");
            default -> List.of(r);
        };
    }

    private Long resolveManagerOfManager(EmployeeView employee) {
        if (employee == null || employee.getManagerId() == null) return null;
        return employeeRepository.findById(employee.getManagerId())
                .map(EmployeeView::getManagerId)
                .orElse(null);
    }

    /** Resolve expected approver for preview (non-persisted). */
    private Long resolveExpectedApprover(EmployeeView employee, Map<String, Object> step) {
        String resolver = step.get("resolver") != null ? String.valueOf(step.get("resolver")).trim().toUpperCase(Locale.ROOT) : "";
        String role = step.get("role") != null ? String.valueOf(step.get("role")).trim() : "ADMIN";
        if (resolver.isEmpty()) resolver = ApprovalChainSupport.defaultResolverForRole(role);
        return switch (resolver) {
            case ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER,
                    ApprovalAuthorityService.RESOLVER_REPORTING_MANAGER_AND_DESIGNATION -> employee.getManagerId();
            case ApprovalAuthorityService.RESOLVER_MANAGER_OF_MANAGER -> resolveManagerOfManager(employee);
            case ApprovalAuthorityService.RESOLVER_NAMED_EMPLOYEE -> {
                Object nid = step.get("namedEmployeeId");
                yield nid instanceof Number n ? n.longValue() : null;
            }
            default -> null;
        };
    }
}
