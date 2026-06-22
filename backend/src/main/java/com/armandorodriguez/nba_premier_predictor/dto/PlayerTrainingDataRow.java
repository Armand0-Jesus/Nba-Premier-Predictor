package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record PlayerTrainingDataRow(
        Long gameId,
        Long playerId,
        Long teamId,
        Integer seasonStartYear,
        LocalDateTime gameDateTime,
        LocalDateTime dataCutoffTime,
        Map<String, Object> features,
        PlayerTrainingTargets targets) {
}
