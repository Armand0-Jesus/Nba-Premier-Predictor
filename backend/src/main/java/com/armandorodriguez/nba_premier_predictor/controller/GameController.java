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

import com.armandorodriguez.nba_premier_predictor.dto.GameResponse;
import com.armandorodriguez.nba_premier_predictor.dto.GameBoxScoreResponse;
import com.armandorodriguez.nba_premier_predictor.service.GameService;

@Validated
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    Page<GameResponse> search(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return gameService.search(season, teamId, gameType, query, pageable);
    }

    @GetMapping("/{gameId}")
    GameResponse get(@PathVariable Long gameId) {
        return gameService.get(gameId);
    }

    @GetMapping("/{gameId}/box-score")
    GameBoxScoreResponse boxScore(@PathVariable Long gameId) {
        return gameService.boxScore(gameId);
    }
}
