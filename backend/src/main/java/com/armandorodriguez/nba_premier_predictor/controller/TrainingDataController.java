package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerTrainingDataRow;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScoreTrainingDataRow;
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
            @RequestParam(required = false) Integer startSeason,
            @RequestParam(required = false) Integer endSeason,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(250000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int offset) {
        validateSeasonWindow(season, startSeason, endSeason);
        return trainingDataService.playerStatRows(season, startSeason, endSeason, limit, offset);
    }

    @GetMapping("/game-scores")
    List<TeamScoreTrainingDataRow> gameScores(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer startSeason,
            @RequestParam(required = false) Integer endSeason,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(250000) int limit,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int offset) {
        validateSeasonWindow(season, startSeason, endSeason);
        return trainingDataService.gameScoreRows(season, startSeason, endSeason, limit, offset);
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
