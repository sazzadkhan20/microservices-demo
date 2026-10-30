package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Team;
import com.platform.hrms.infrastructure.persistence.TeamRepository;
import com.platform.hrms.security.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@Tag(name = "Teams", description = "Teams within departments")
public class TeamController {

    private final TeamRepository teamRepository;

    public TeamController(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @GetMapping
    @Operation(summary = "List teams (paginated, searchable)")
    public PageResponse<Team> list(@RequestParam Map<String, String> params) {
        PageQuery query = PageQuery.fromParams(params);
        return DynamicListSupport.page(
                teamRepository.findAll(),
                query,
                team -> DynamicListSupport.containsIgnoreCase(team.getName(), query.q())
                        || DynamicListSupport.containsIgnoreCase(team.getCode(), query.q()),
                team -> Map.of(
                        "code", team.getCode() == null ? "" : team.getCode(),
                        "clashAction", team.getClashAction() == null ? "" : team.getClashAction(),
                        "departmentId", team.getDepartmentId() == null ? "" : String.valueOf(team.getDepartmentId())
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(feature = "hierarchy", action = "edit")
    @Operation(summary = "Create team (org structure — hierarchy.edit)")
    public Team create(@RequestBody Map<String, Object> body) {
        String code = requireText(body, "code");
        if (teamRepository.findAll().stream().anyMatch(t -> code.equalsIgnoreCase(t.getCode()))) {
            throw new IllegalStateException("Team code already exists: " + code);
        }
        Team team = new Team();
        team.setCode(code);
        team.setName(requireText(body, "name"));
        applyOptional(team, body);
        return teamRepository.save(team);
    }

    @PutMapping("/{id}")
    @RequiresPermission(feature = "hierarchy", action = "edit")
    @Operation(summary = "Update team (org structure — hierarchy.edit)")
    public Team update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Team not found: " + id));
        if (body.containsKey("code")) {
            String newCode = requireText(body, "code");
            if (!newCode.equals(team.getCode())) {
                if (teamRepository.findAll().stream()
                        .anyMatch(t -> !t.getId().equals(id) && newCode.equalsIgnoreCase(t.getCode()))) {
                    throw new IllegalStateException("Team code already exists: " + newCode);
                }
                team.setCode(newCode);
            }
        }
        if (body.containsKey("name")) {
            team.setName(requireText(body, "name"));
        }
        applyOptional(team, body);
        return teamRepository.save(team);
    }

    private static void applyOptional(Team team, Map<String, Object> body) {
        if (body.containsKey("departmentId")) {
            team.setDepartmentId(optionalLong(body.get("departmentId"), "departmentId"));
        }
        if (body.containsKey("leadEmployeeId")) {
            team.setLeadEmployeeId(optionalLong(body.get("leadEmployeeId"), "leadEmployeeId"));
        }
        Object maxObj = body.get("maxOnLeave");
        if (maxObj != null && !String.valueOf(maxObj).isBlank()) {
            int max;
            try {
                max = Integer.parseInt(String.valueOf(maxObj).trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("maxOnLeave must be an integer");
            }
            if (max < 0) {
                throw new IllegalArgumentException("maxOnLeave must be >= 0");
            }
            team.setMaxOnLeave(max);
        }
        Object clash = body.get("clashAction");
        if (clash != null && !String.valueOf(clash).isBlank()) {
            String action = String.valueOf(clash).trim().toUpperCase(Locale.ROOT);
            if (!"ALERT".equals(action) && !"BLOCK".equals(action)) {
                throw new IllegalArgumentException("clashAction must be ALERT or BLOCK");
            }
            team.setClashAction(action);
        }
    }

    private static String requireText(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return String.valueOf(v).trim();
    }

    private static Long optionalLong(Object v, String key) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }
}
