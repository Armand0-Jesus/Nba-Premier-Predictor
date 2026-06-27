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

import com.armandorodriguez.nba_premier_predictor.dto.PlayerAveragesResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerDashboardResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerDetailResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerSummaryResponse;
import com.armandorodriguez.nba_premier_predictor.dto.SeasonResponse;
import com.armandorodriguez.nba_premier_predictor.service.PlayerService;

@Validated
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    Page<PlayerSummaryResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return playerService.search(query, activeOnly, pageable);
    }

    @GetMapping("/{playerId}")
    PlayerDetailResponse get(@PathVariable Long playerId) {
        return playerService.get(playerId);
    }

    @GetMapping("/{playerId}/games")
    Page<PlayerGameLogResponse> games(
            @PathVariable Long playerId,
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return playerService.gameLogs(playerId, season, query, pageable);
    }

    @GetMapping("/{playerId}/seasons")
    List<SeasonResponse> seasons(@PathVariable Long playerId) {
        return playerService.seasons(playerId);
    }

    @GetMapping("/{playerId}/averages")
    PlayerAveragesResponse averages(@PathVariable Long playerId, @RequestParam(required = false) Integer season) {
        return playerService.averages(playerId, season);
    }

    @GetMapping("/{playerId}/dashboard")
    PlayerDashboardResponse dashboard(@PathVariable Long playerId, @RequestParam(required = false) Integer season) {
        return playerService.dashboard(playerId, season);
    }
}
