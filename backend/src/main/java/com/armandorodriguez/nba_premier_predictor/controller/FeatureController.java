package com.armandorodriguez.nba_premier_predictor.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.FeatureGenerationResponse;
import com.armandorodriguez.nba_premier_predictor.service.PlayerFeatureSnapshotService;

@RestController
@RequestMapping("/api/features")
public class FeatureController {

    private final PlayerFeatureSnapshotService playerFeatureSnapshotService;

    public FeatureController(PlayerFeatureSnapshotService playerFeatureSnapshotService) {
        this.playerFeatureSnapshotService = playerFeatureSnapshotService;
    }

    @PostMapping("/player-snapshots/generate")
    FeatureGenerationResponse generatePlayerSnapshots(@RequestParam(required = false) Integer season) {
        return playerFeatureSnapshotService.generate(season);
    }
}
