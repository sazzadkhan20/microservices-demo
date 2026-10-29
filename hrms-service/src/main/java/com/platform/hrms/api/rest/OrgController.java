package com.platform.hrms.api.rest;

import com.platform.hrms.api.dto.DepartmentResponse;
import com.platform.hrms.api.dto.DesignationResponse;
import com.platform.hrms.domain.Department;
import com.platform.hrms.domain.Team;
import com.platform.hrms.infrastructure.persistence.DepartmentRepository;
import com.platform.hrms.infrastructure.persistence.DesignationRepository;
import com.platform.hrms.infrastructure.persistence.TeamRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/org")
@Tag(name = "Organization", description = "Departments, designations, org tree (tenant-scoped)")
public class OrgController {

    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;

    public OrgController(DepartmentRepository departmentRepository,
                         DesignationRepository designationRepository,
                         TeamRepository teamRepository) {
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
    }

    @GetMapping("/departments")
    @Operation(summary = "List departments")
    public List<DepartmentResponse> departments() {
        return departmentRepository.findAll().stream().map(DepartmentResponse::from).toList();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "org", action = "manage")
    @Operation(summary = "Create department")
    public DepartmentResponse createDepartment(@RequestBody Map<String, Object> body) {
        Department d = new Department();
        d.setCode(str(body, "code"));
        d.setName(str(body, "name"));
        if (d.getCode() == null || d.getName() == null) {
            throw new IllegalArgumentException("code and name are required");
        }
        return DepartmentResponse.from(departmentRepository.save(d));
    }

    @GetMapping("/designations")
    @Operation(summary = "List designations (approverRole links to leave approval chain)")
    public List<DesignationResponse> designations() {
        return designationRepository.findAll().stream().map(DesignationResponse::from).toList();
    }

    @GetMapping("/tree")
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
