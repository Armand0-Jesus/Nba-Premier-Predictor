package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.Instant;

public record PredictionHistoryResponse(
        Long predictionId,
        String predictionType,
        Long gameId,
        Long playerId,
        Long teamId,
        String modelVersion,
        Instant requestedAt,
        Double confidenceScore,
        Double projectedPoints,
        Double projectedRebounds,
        Double projectedAssists,
        Double projectedMinutes,
        Double fantasyPoints,
        Double fantasyFloor,
        Double fantasyCeiling,
        String riskLevel) {
}
