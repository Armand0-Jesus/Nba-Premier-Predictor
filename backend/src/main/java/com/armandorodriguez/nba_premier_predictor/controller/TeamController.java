package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.List;

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
import com.armandorodriguez.nba_premier_predictor.dto.TeamGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamProjectionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamResponse;
import com.armandorodriguez.nba_premier_predictor.dto.RosterImpactResponse;
import com.armandorodriguez.nba_premier_predictor.dto.SeasonResponse;
import com.armandorodriguez.nba_premier_predictor.service.StandingsProjectionService;
import com.armandorodriguez.nba_premier_predictor.service.TeamService;

@Validated
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teamService;
    private final StandingsProjectionService standingsProjectionService;

    public TeamController(TeamService teamService, StandingsProjectionService standingsProjectionService) {
        this.teamService = teamService;
        this.standingsProjectionService = standingsProjectionService;
    }

    @GetMapping
    Page<TeamResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "true") boolean currentOnly,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return teamService.search(query, currentOnly, pageable);
    }

    @GetMapping("/{teamId}")
    TeamResponse get(@PathVariable Long teamId) {
        return teamService.get(teamId);
    }

    @GetMapping("/{teamId}/dashboard")
    TeamDashboardResponse dashboard(@PathVariable Long teamId, @RequestParam(required = false) Integer season) {
        return teamService.dashboard(teamId, season);
    }

    @GetMapping("/{teamId}/games")
    Page<TeamGameLogResponse> games(
            @PathVariable Long teamId,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return teamService.gameLogs(teamId, season, query, pageable);
    }

    @GetMapping("/{teamId}/seasons")
    List<SeasonResponse> seasons(@PathVariable Long teamId) {
        return teamService.seasons(teamId);
    }

    @GetMapping("/{teamId}/projection")
    TeamProjectionResponse projection(@PathVariable Long teamId, @RequestParam(required = false) Integer season) {
        return standingsProjectionService.teamProjection(teamId, season);
    }

    @GetMapping("/{teamId}/roster-impact")
    RosterImpactResponse rosterImpact(@PathVariable Long teamId, @RequestParam(required = false) Integer season) {
        return standingsProjectionService.rosterImpact(teamId, season);
    }
}
