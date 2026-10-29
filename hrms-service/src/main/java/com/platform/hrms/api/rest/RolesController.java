package com.platform.hrms.api.rest;

import com.platform.common.security.AuthContext;
import com.platform.hrms.application.AuditService;
import com.platform.hrms.domain.rbac.AppPermission;
import com.platform.hrms.domain.rbac.AppRole;
import com.platform.hrms.domain.rbac.AppRolePermission;
import com.platform.hrms.infrastructure.persistence.rbac.AppPermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRolePermissionRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppRoleRepository;
import com.platform.hrms.infrastructure.persistence.rbac.AppUserRoleRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "Tenant RBAC roles and permission matrix")
public class RolesController {

    private static final Set<String> SCOPES = Set.of("ALL", "TEAM", "OWN");

    private final AppRoleRepository roleRepository;
    private final AppRolePermissionRepository rolePermissionRepository;
    private final AppPermissionRepository permissionRepository;
    private final AppUserRoleRepository userRoleRepository;
    private final AuditService auditService;

    public RolesController(
            AppRoleRepository roleRepository,
            AppRolePermissionRepository rolePermissionRepository,
            AppPermissionRepository permissionRepository,
            AppUserRoleRepository userRoleRepository,
            AuditService auditService) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @RequiresPermission(feature = "roles", action = "view")
    @Operation(summary = "List roles with permissions")
    public List<Map<String, Object>> list() {
        Map<Long, AppPermission> perms = permissionsById();
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppRole role : roleRepository.findAll()) {
            out.add(toView(role, perms));
        }
        return out;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "roles", action = "manage")
    @Operation(summary = "Create role (code, name, description)")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String code = requireText(body, "code");
        String name = requireText(body, "name");
        ensureCodeFree(code);
        AppRole role = new AppRole();
        role.setCode(code);
        role.setName(name);
        role.setDescription(optionalText(body, "description"));
        role.setSystemRole(false);
        role.setActive(true);
        AppRole saved = roleRepository.save(role);
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "AppRole",
                String.valueOf(saved.getId()), null, Map.of("code", saved.getCode(), "name", saved.getName()));
        return toView(saved, permissionsById());
    }

    @PutMapping("/{id}")
    @Transactional
    @RequiresPermission(feature = "roles", action = "manage")
    @Operation(summary = "Update role name / description / active")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AppRole role = requireRole(id);
        Map<String, Object> before = Map.of(
                "name", role.getName(),
                "description", role.getDescription() == null ? "" : role.getDescription(),
                "active", role.isActive());
        if (body.containsKey("name")) {
            role.setName(requireText(body, "name"));
        }
        if (body.containsKey("description")) {
            role.setDescription(optionalText(body, "description"));
        }
        if (body.get("active") != null) {
            boolean active = Boolean.parseBoolean(String.valueOf(body.get("active")));
            if (!active && role.isSystemRole()) {
                throw new IllegalStateException("Not allowed: system role cannot be deactivated");
            }
            role.setActive(active);
        }
        AppRole saved = roleRepository.save(role);
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "AppRole",
                String.valueOf(saved.getId()), before, Map.of(
                        "name", saved.getName(),
                        "description", saved.getDescription() == null ? "" : saved.getDescription(),
                        "active", saved.isActive()));
        return toView(saved, permissionsById());
    }

    @PostMapping("/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @RequiresPermission(feature = "roles", action = "manage")
    @Operation(summary = "Copy role + permission matrix to a new role (code, name, description?)")
    public Map<String, Object> copy(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AppRole source = requireRole(id);
        String code = requireText(body, "code");
        String name = requireText(body, "name");
        ensureCodeFree(code);
        AppRole role = new AppRole();
        role.setCode(code);
        role.setName(name);
        role.setDescription(body.containsKey("description")
                ? optionalText(body, "description") : source.getDescription());
        role.setSystemRole(false);
        role.setActive(true);
        AppRole saved = roleRepository.save(role);
        for (AppRolePermission link : rolePermissionRepository.findByRoleIdIn(List.of(source.getId()))) {
            AppRolePermission copy = new AppRolePermission();
            copy.setRoleId(saved.getId());
            copy.setPermissionId(link.getPermissionId());
            copy.setDataScope(link.getDataScope());
            rolePermissionRepository.save(copy);
        }
        auditService.record(AuthContext.getEmployeeId(), "CREATE", "AppRole",
                String.valueOf(saved.getId()), null, Map.of(
                        "code", saved.getCode(), "name", saved.getName(), "copiedFromRoleId", source.getId()));
        return toView(saved, permissionsById());
    }

    @PutMapping("/{id}/permissions")
    @Transactional
    @RequiresPermission(feature = "roles", action = "manage")
    @Operation(summary = "Replace permission matrix for role; body = list of {feature, action, scope}")
    public Map<String, Object> replacePermissions(@PathVariable Long id,
                                                  @RequestBody List<Map<String, Object>> matrix) {
        AppRole role = requireRole(id);
        Map<Long, AppRolePermission> next = new LinkedHashMap<>();
        for (Map<String, Object> entry : matrix) {
            String feature = requireText(entry, "feature");
            String action = requireText(entry, "action");
            String rawScope = optionalText(entry, "scope");
            String scope = rawScope == null ? "OWN" : rawScope.toUpperCase(Locale.ROOT);
            if (!SCOPES.contains(scope)) {
                throw new IllegalArgumentException("scope must be one of " + SCOPES + " for " + feature + ":" + action);
            }
            AppPermission perm = permissionRepository.findByFeatureAndAction(feature, action)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown permission: " + feature + ":" + action));
            AppRolePermission link = new AppRolePermission();
            link.setRoleId(role.getId());
            link.setPermissionId(perm.getId());
            link.setDataScope(scope);
            next.put(perm.getId(), link);
        }
        int before = rolePermissionRepository.findByRoleIdIn(List.of(role.getId())).size();
        rolePermissionRepository.deleteByRoleId(role.getId());
        rolePermissionRepository.flush();
        rolePermissionRepository.saveAll(next.values());
        auditService.record(AuthContext.getEmployeeId(), "UPDATE", "AppRolePermission",
                String.valueOf(role.getId()), Map.of("count", before), Map.of("count", next.size()));
        return toView(role, permissionsById());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @RequiresPermission(feature = "roles", action = "manage")
    @Operation(summary = "Delete role (only non-system roles with no user assignments)")
    public void delete(@PathVariable Long id) {
        AppRole role = requireRole(id);
        if (role.isSystemRole()) {
            throw new IllegalStateException("Not allowed: system role cannot be deleted");
        }
        if (userRoleRepository.existsByRoleId(id)) {
            throw new IllegalStateException("Role is assigned to employees; unassign before deleting");
        }
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
        auditService.record(AuthContext.getEmployeeId(), "DELETE", "AppRole",
                String.valueOf(id), Map.of("code", role.getCode(), "name", role.getName()), null);
    }

    private AppRole requireRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    private void ensureCodeFree(String code) {
        if (roleRepository.findByCode(code).isPresent()) {
            throw new IllegalStateException("Role code already exists: " + code);
        }
    }

    private Map<Long, AppPermission> permissionsById() {
        return permissionRepository.findAll().stream()
                .collect(Collectors.toMap(AppPermission::getId, Function.identity()));
    }

    private Map<String, Object> toView(AppRole role, Map<Long, AppPermission> perms) {
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
        return row;
    }

    private static String requireText(Map<String, Object> body, String key) {
        String v = optionalText(body, key);
        if (v == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return v;
    }

    private static String optionalText(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v).trim();
    }
}
