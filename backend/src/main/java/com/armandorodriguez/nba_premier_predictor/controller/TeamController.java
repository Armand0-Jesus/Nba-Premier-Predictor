package com.armandorodriguez.nba_premier_predictor.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.TeamDashboardResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamResponse;
import com.armandorodriguez.nba_premier_predictor.service.TeamService;

@Validated
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    Page<TeamResponse> search(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return teamService.search(query, pageable);
    }

    @GetMapping("/{teamId}")
    TeamResponse get(@PathVariable Long teamId) {
        return teamService.get(teamId);
    }

    @GetMapping("/{teamId}/dashboard")
    TeamDashboardResponse dashboard(@PathVariable Long teamId, @RequestParam(required = false) Integer season) {
        return teamService.dashboard(teamId, season);
    }
}
