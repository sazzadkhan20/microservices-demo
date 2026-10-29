package com.platform.hrms.api.rest;

import com.platform.common.api.PageQuery;
import com.platform.common.api.PageResponse;
import com.platform.hrms.application.support.DynamicListSupport;
import com.platform.hrms.domain.Team;
import com.platform.hrms.infrastructure.persistence.TeamRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
