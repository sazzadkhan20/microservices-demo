package com.platform.hrms.application;

import com.platform.hrms.domain.rbac.AppPermission;
import com.platform.hrms.domain.rbac.AppRole;
import com.platform.hrms.domain.rbac.AppRolePermission;
import com.platform.hrms.domain.rbac.AppUserRole;
import com.platform.hrms.infrastructure.persistence.rbac.AppPermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRolePermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRoleRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppUserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Config-driven RBAC: roles × permissions × data scope from tenant DB.
 */
@Service
public class RbacService {

    private final AppUserRoleRepository userRoleRepository;
    private final AppRoleRepository roleRepository;
    private final AppRolePermissionRepository rolePermissionRepository;
    private final AppPermissionRepository permissionRepository;

    public RbacService(
            AppUserRoleRepository userRoleRepository,
            AppRoleRepository roleRepository,
            AppRolePermissionRepository rolePermissionRepository,
            AppPermissionRepository permissionRepository) {
        this.userRoleRepository = userRoleRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public List<String> roleCodesFor(Long employeeId) {
        List<Long> roleIds = userRoleRepository.findByEmployeeId(employeeId).stream()
                .map(AppUserRole::getRoleId).toList();
        return roleRepository.findAllById(roleIds).stream()
                .filter(AppRole::isActive)
                .map(AppRole::getCode)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> permissionKeysFor(Long employeeId) {
        List<Long> roleIds = userRoleRepository.findByEmployeeId(employeeId).stream()
                .map(AppUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<AppRolePermission> rps = rolePermissionRepository.findByRoleIdIn(roleIds);
        Set<Long> permIds = rps.stream().map(AppRolePermission::getPermissionId).collect(Collectors.toSet());
        Set<String> keys = new HashSet<>();
        for (AppPermission p : permissionRepository.findAllById(permIds)) {
            keys.add(p.key().toLowerCase(Locale.ROOT));
        }
        // Super admin / admin shortcut: if role is superadmin or admin, grant wildcard
        for (String code : roleCodesFor(employeeId)) {
            if ("superadmin".equalsIgnoreCase(code) || "admin".equalsIgnoreCase(code)) {
                keys.add("*:*");
            }
        }
        // FE/BE synonym aliases: :view ↔ :read so nav gates and seed keys both work
        Set<String> withAliases = new HashSet<>(keys);
        for (String k : keys) {
            if (k.endsWith(":view")) {
                withAliases.add(k.substring(0, k.length() - 4) + "read");
            } else if (k.endsWith(":read")) {
                withAliases.add(k.substring(0, k.length() - 4) + "view");
            }
        }
        return withAliases;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long employeeId, String feature, String action) {
        Set<String> keys = permissionKeysFor(employeeId);
        if (keys.contains("*:*")) {
            return true;
        }
        return keys.contains((feature + ":" + action).toLowerCase(Locale.ROOT))
                || keys.contains((feature + ":*").toLowerCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public String widestScope(Long employeeId, String feature, String action) {
        if (hasPermission(employeeId, feature, action) && permissionKeysFor(employeeId).contains("*:*")) {
            return "ALL";
        }
        List<Long> roleIds = userRoleRepository.findByEmployeeId(employeeId).stream()
                .map(AppUserRole::getRoleId).toList();
        AppPermission perm = permissionRepository.findByFeatureAndAction(feature, action).orElse(null);
        if (perm == null || roleIds.isEmpty()) {
            return "NONE";
        }
        String widest = "NONE";
        for (AppRolePermission rp : rolePermissionRepository.findByRoleIdIn(roleIds)) {
            if (!rp.getPermissionId().equals(perm.getId())) {
                continue;
            }
            String s = rp.getDataScope() == null ? "OWN" : rp.getDataScope().toUpperCase(Locale.ROOT);
            if ("ALL".equals(s)) return "ALL";
            if ("TEAM".equals(s)) widest = "TEAM";
            else if ("OWN".equals(s) && "NONE".equals(widest)) widest = "OWN";
        }
        return widest;
    }

    /** Replace all roles for an employee with the given role codes (onboarding / role assign). */
    @Transactional
    public List<String> assignRoles(Long employeeId, List<String> roleCodes) {
        userRoleRepository.deleteByEmployeeId(employeeId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        for (String code : roleCodes) {
            if (code == null || code.isBlank()) continue;
            AppRole role = roleRepository.findByCode(code.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + code));
            if (!role.isActive()) {
                throw new IllegalStateException("Role inactive: " + code);
            }
            AppUserRole link = new AppUserRole();
            link.setEmployeeId(employeeId);
            link.setRoleId(role.getId());
            userRoleRepository.save(link);
        }
        return roleCodesFor(employeeId);
    }
}
