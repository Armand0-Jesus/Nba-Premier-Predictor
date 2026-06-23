package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerPredictionResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PredictionHistoryResponse;
import com.armandorodriguez.nba_premier_predictor.service.PredictionService;

@Validated
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping("/player")
    PlayerPredictionResponse player(@Valid @RequestBody PlayerPredictionRequest request) {
        return predictionService.predictPlayer(request);
    }

    @PostMapping("/fantasy")
    PlayerPredictionResponse fantasy(@Valid @RequestBody PlayerPredictionRequest request) {
        return predictionService.predictFantasy(request);
    }

    @GetMapping("/history")
    List<PredictionHistoryResponse> history(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return predictionService.history(limit);
    }
}
