package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record TeamScoreTrainingDataRow(
        Long gameId,
        Long homeTeamId,
        Long awayTeamId,
        Integer seasonStartYear,
        LocalDateTime gameDateTime,
        LocalDateTime dataCutoffTime,
        Map<String, Object> features,
        TeamScoreTrainingTargets targets) {
}
