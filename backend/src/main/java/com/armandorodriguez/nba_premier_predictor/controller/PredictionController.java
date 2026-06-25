package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
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
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionRequest;
import com.armandorodriguez.nba_premier_predictor.dto.TeamScorePredictionResponse;
import com.armandorodriguez.nba_premier_predictor.service.PredictionService;
import com.armandorodriguez.nba_premier_predictor.service.RateLimitService;

@Validated
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;
    private final RateLimitService rateLimitService;

    public PredictionController(PredictionService predictionService, RateLimitService rateLimitService) {
        this.predictionService = predictionService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/player")
    PlayerPredictionResponse player(@Valid @RequestBody PlayerPredictionRequest request, HttpServletRequest servletRequest) {
        rateLimitService.checkPredictionLimit(clientKey(servletRequest));
        return predictionService.predictPlayer(request);
    }

    @PostMapping("/fantasy")
    PlayerPredictionResponse fantasy(@Valid @RequestBody PlayerPredictionRequest request, HttpServletRequest servletRequest) {
        rateLimitService.checkPredictionLimit(clientKey(servletRequest));
        return predictionService.predictFantasy(request);
    }

    @PostMapping("/game-score")
    TeamScorePredictionResponse gameScore(@Valid @RequestBody TeamScorePredictionRequest request, HttpServletRequest servletRequest) {
        rateLimitService.checkPredictionLimit(clientKey(servletRequest));
        return predictionService.predictGameScore(request);
    }

    @GetMapping("/history")
    List<PredictionHistoryResponse> history(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return predictionService.history(limit);
    }

    private static String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
