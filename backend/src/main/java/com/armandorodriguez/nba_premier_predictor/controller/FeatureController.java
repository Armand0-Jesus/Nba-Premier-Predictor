package com.armandorodriguez.nba_premier_predictor.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.FeatureGenerationResponse;
import com.armandorodriguez.nba_premier_predictor.service.PlayerFeatureSnapshotService;
import com.armandorodriguez.nba_premier_predictor.service.TeamFeatureSnapshotService;

@RestController
@RequestMapping("/api/features")
public class FeatureController {

    private final PlayerFeatureSnapshotService playerFeatureSnapshotService;
    private final TeamFeatureSnapshotService teamFeatureSnapshotService;

    public FeatureController(
            PlayerFeatureSnapshotService playerFeatureSnapshotService,
            TeamFeatureSnapshotService teamFeatureSnapshotService) {
        this.playerFeatureSnapshotService = playerFeatureSnapshotService;
        this.teamFeatureSnapshotService = teamFeatureSnapshotService;
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
