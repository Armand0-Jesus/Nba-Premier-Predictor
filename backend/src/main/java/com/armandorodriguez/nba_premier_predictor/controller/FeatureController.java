package com.armandorodriguez.nba_premier_predictor.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            playerFeatureSnapshotService.generate(featureSnapshotQueryService.seasonForGame(gameId));
            return featureSnapshotQueryService.latestPlayerSnapshot(gameId, playerId);
        }
    }

    @PostMapping("/game-snapshots/ensure")
    FeatureSnapshotResponse ensureGameSnapshot(@RequestParam Long gameId) {
        try {
            return featureSnapshotQueryService.latestGameSnapshot(gameId);
        } catch (ResourceNotFoundException ex) {
            teamFeatureSnapshotService.generateGame(featureSnapshotQueryService.seasonForGame(gameId));
            return featureSnapshotQueryService.latestGameSnapshot(gameId);
        }
    }

    @PostMapping("/player-snapshots/generate")
    FeatureGenerationResponse generatePlayerSnapshots(@RequestParam(required = false) Integer season) {
        return playerFeatureSnapshotService.generate(season);
    }

    @PostMapping("/team-snapshots/generate")
    FeatureGenerationResponse generateTeamSnapshots(@RequestParam(required = false) Integer season) {
        return teamFeatureSnapshotService.generateTeam(season);
    }

    @PostMapping("/game-snapshots/generate")
    FeatureGenerationResponse generateGameSnapshots(@RequestParam(required = false) Integer season) {
        return teamFeatureSnapshotService.generateGame(season);
    }
}
