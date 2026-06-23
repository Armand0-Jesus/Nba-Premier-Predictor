package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlayerPredictionRequest(
        Long gameId,
        @NotNull @Positive Long playerId,
        Long teamId,
        LocalDateTime dataCutoffTime,
        @NotEmpty Map<String, Object> features) {
}
