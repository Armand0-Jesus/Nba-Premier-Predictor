package com.armandorodriguez.nba_premier_predictor.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.armandorodriguez.nba_premier_predictor.dto.FeatureGenerationResponse;
import com.armandorodriguez.nba_premier_predictor.dto.FeatureSnapshotResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.service.FeatureSnapshotQueryService;
import com.armandorodriguez.nba_premier_predictor.service.PlayerFeatureSnapshotService;
import com.armandorodriguez.nba_premier_predictor.service.TeamFeatureSnapshotService;

@RestController
@RequestMapping("/api/features")
public class FeatureController {

    private final PlayerFeatureSnapshotService playerFeatureSnapshotService;
    private final TeamFeatureSnapshotService teamFeatureSnapshotService;
    private final FeatureSnapshotQueryService featureSnapshotQueryService;

    public FeatureController(
            PlayerFeatureSnapshotService playerFeatureSnapshotService,
            TeamFeatureSnapshotService teamFeatureSnapshotService,
            FeatureSnapshotQueryService featureSnapshotQueryService) {
        this.playerFeatureSnapshotService = playerFeatureSnapshotService;
        this.teamFeatureSnapshotService = teamFeatureSnapshotService;
        this.featureSnapshotQueryService = featureSnapshotQueryService;
    }

    @GetMapping("/player-snapshots/latest")
    FeatureSnapshotResponse latestPlayerSnapshot(@RequestParam Long gameId, @RequestParam Long playerId) {
        return featureSnapshotQueryService.latestPlayerSnapshot(gameId, playerId);
    }

    @GetMapping("/game-snapshots/latest")
    FeatureSnapshotResponse latestGameSnapshot(@RequestParam Long gameId) {
        return featureSnapshotQueryService.latestGameSnapshot(gameId);
    }

    @PostMapping("/player-snapshots/ensure")
    FeatureSnapshotResponse ensurePlayerSnapshot(@RequestParam Long gameId, @RequestParam Long playerId) {
        try {
            return featureSnapshotQueryService.latestPlayerSnapshot(gameId, playerId);
        } catch (ResourceNotFoundException ex) {
            playerFeatureSnapshotService.generateForGame(gameId, playerId);
            return featureSnapshotQueryService.latestPlayerSnapshot(gameId, playerId);
        }
    }

    @PostMapping("/game-snapshots/ensure")
    FeatureSnapshotResponse ensureGameSnapshot(@RequestParam Long gameId) {
        try {
            return featureSnapshotQueryService.latestGameSnapshot(gameId);
        } catch (ResourceNotFoundException ex) {
            teamFeatureSnapshotService.generateGameForGame(gameId);
            return featureSnapshotQueryService.latestGameSnapshot(gameId);
        }
    }

    @PostMapping("/player-snapshots/generate")
    FeatureGenerationResponse generatePlayerSnapshots(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer startSeason,
            @RequestParam(required = false) Integer endSeason) {
        validateSeasonWindow(season, startSeason, endSeason);
        return playerFeatureSnapshotService.generate(season, startSeason, endSeason);
    }

    @PostMapping("/team-snapshots/generate")
    FeatureGenerationResponse generateTeamSnapshots(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer startSeason,
            @RequestParam(required = false) Integer endSeason) {
        validateSeasonWindow(season, startSeason, endSeason);
        return teamFeatureSnapshotService.generateTeam(season, startSeason, endSeason);
    }

    @PostMapping("/game-snapshots/generate")
    FeatureGenerationResponse generateGameSnapshots(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer startSeason,
            @RequestParam(required = false) Integer endSeason) {
        validateSeasonWindow(season, startSeason, endSeason);
        return teamFeatureSnapshotService.generateGame(season, startSeason, endSeason);
    }

    private static void validateSeasonWindow(Integer season, Integer startSeason, Integer endSeason) {
        if (season != null && (startSeason != null || endSeason != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use either season or startSeason/endSeason, not both");
        }
        if (startSeason != null && endSeason != null && startSeason > endSeason) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startSeason must be before or equal to endSeason");
        }
    }
}
