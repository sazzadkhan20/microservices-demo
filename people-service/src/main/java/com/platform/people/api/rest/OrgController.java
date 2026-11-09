package com.platform.people.api.rest;

import com.platform.people.api.dto.DepartmentResponse;
import com.platform.people.api.dto.DesignationResponse;
import com.platform.people.application.OrgChartService;
import com.platform.people.domain.Department;
import com.platform.people.domain.Team;
import com.platform.people.infrastructure.persistence.DepartmentRepository;
import com.platform.people.infrastructure.persistence.DesignationRepository;
import com.platform.people.infrastructure.persistence.TeamRepository;
import com.platform.people.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
@Tag(name = "Organization", description = "Departments, designations, org tree / chart (tenant-scoped)")
public class OrgController {

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final OrgChartService orgChartService;

    public OrgController(DepartmentRepository departmentRepository,
                         DesignationRepository designationRepository,
                         TeamRepository teamRepository,
                         OrgChartService orgChartService) {
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
        this.orgChartService = orgChartService;
    }

    @GetMapping("/chart")
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "People org chart drawn from reporting lines (as-of date; default today)")
    public List<Map<String, Object>> chart(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return orgChartService.chart(asOf, departmentId, includeInactive);
    }

    @GetMapping("/departments")
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "List departments")
    public List<DepartmentResponse> departments() {
        return departmentRepository.findAll().stream().map(DepartmentResponse::from).toList();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "hierarchy", action = "edit")
    @Operation(summary = "Create department (prototype hierarchy.edit / org structure)")
    public DepartmentResponse createDepartment(@RequestBody Map<String, Object> body) {
        Object rawCode = body.get("code");
        if (rawCode != null && !(rawCode instanceof String)) {
            throw new IllegalArgumentException("code must be a string");
        }
        Object rawName = body.get("name");
        if (rawName != null && !(rawName instanceof String)) {
            throw new IllegalArgumentException("name must be a string");
        }
        String code = str(body, "code");
        String name = str(body, "name");
        if (code == null || name == null) {
            throw new IllegalArgumentException("code and name are required");
        }
        if (code.length() > 64) {
            throw new IllegalArgumentException("code must be at most 64 characters");
        }
        if (name.length() > 128) {
            throw new IllegalArgumentException("name must be at most 128 characters");
        }
        code = code.toUpperCase(java.util.Locale.ROOT);
        if (departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalStateException("Department code already exists");
        }
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalStateException("Department name already exists");
        }
        Department d = new Department();
        d.setCode(code);
        d.setName(name);
        return DepartmentResponse.from(departmentRepository.save(d));
    }

    @GetMapping("/designations")
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "List designations (approverRole links to leave approval chain)")
    public List<DesignationResponse> designations() {
        return designationRepository.findAll().stream().map(DesignationResponse::from).toList();
    }

    @GetMapping("/tree")
    @RequiresPermission(feature = "org", action = "view")
    @Operation(summary = "Org tree: departments with nested teams")
    public List<Map<String, Object>> tree() {
        List<Team> teams = teamRepository.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Department dept : departmentRepository.findAll()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", dept.getId());
            node.put("code", dept.getCode());
            node.put("name", dept.getName());
            List<Map<String, Object>> children = new ArrayList<>();
            for (Team t : teams) {
                if (dept.getId().equals(t.getDepartmentId())) {
                    Map<String, Object> tm = new LinkedHashMap<>();
                    tm.put("id", t.getId());
                    tm.put("code", t.getCode());
                    tm.put("name", t.getName());
                    tm.put("leadEmployeeId", t.getLeadEmployeeId());
                    tm.put("maxOnLeave", t.getMaxOnLeave());
                    tm.put("clashAction", t.getClashAction());
                    children.add(tm);
                }
            }
            node.put("teams", children);
            node.put("children", children);
            out.add(node);
        }
        return out;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }
}
