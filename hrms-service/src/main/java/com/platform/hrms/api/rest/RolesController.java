package com.platform.hrms.api.rest;

import com.platform.hrms.domain.rbac.AppPermission;
import com.platform.hrms.domain.rbac.AppRole;
import com.platform.hrms.domain.rbac.AppRolePermission;
import com.platform.hrms.infrastructure.persistence.rbac.AppPermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRolePermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRoleRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Tenant RBAC roles and permission matrix")
public class RolesController {

    private final AppRoleRepository roleRepository;
    private final AppRolePermissionRepository rolePermissionRepository;
    private final AppPermissionRepository permissionRepository;

    public RolesController(
            AppRoleRepository roleRepository,
            AppRolePermissionRepository rolePermissionRepository,
            AppPermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    @RequiresPermission(feature = "roles", action = "view")
    @Operation(summary = "List roles with permissions")
    public List<Map<String, Object>> list() {
        Map<Long, AppPermission> perms = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AppPermission::getId, Function.identity()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppRole role : roleRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", role.getId());
            row.put("code", role.getCode());
            row.put("name", role.getName());
            row.put("description", role.getDescription());
            row.put("systemRole", role.isSystemRole());
            row.put("active", role.isActive());
            List<Map<String, Object>> rp = new ArrayList<>();
            for (AppRolePermission link : rolePermissionRepository.findByRoleIdIn(List.of(role.getId()))) {
                AppPermission p = perms.get(link.getPermissionId());
                if (p == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("feature", p.getFeature());
                m.put("action", p.getAction());
                m.put("scope", link.getDataScope());
                m.put("key", p.key());
                rp.add(m);
            }
            row.put("permissions", rp);
            out.add(row);
        }
        return out;
    }
}
