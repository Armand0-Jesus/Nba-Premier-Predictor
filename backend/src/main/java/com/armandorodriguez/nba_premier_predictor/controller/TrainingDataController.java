package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerTrainingDataRow;
import com.armandorodriguez.nba_premier_predictor.service.TrainingDataService;

@Validated
@RestController
@RequestMapping("/api/training-data")
public class TrainingDataController {

    private final TrainingDataService trainingDataService;

    public TrainingDataController(TrainingDataService trainingDataService) {
        this.trainingDataService = trainingDataService;
    }

    @GetMapping("/player-stats")
    List<PlayerTrainingDataRow> playerStats(
            @RequestParam(required = false) Integer season,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(10000) int limit) {
        return trainingDataService.playerStatRows(season, limit);
    }
}
