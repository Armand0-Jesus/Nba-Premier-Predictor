package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TeamScorePredictionRequest(
        @NotNull @Positive Long gameId,
        @NotNull @Positive Long homeTeamId,
        @NotNull @Positive Long awayTeamId,
        LocalDateTime dataCutoffTime,
        @NotEmpty Map<String, Object> features) {
}
