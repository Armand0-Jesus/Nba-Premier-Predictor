package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record FeatureSnapshotResponse(
        Long snapshotId,
        String snapshotType,
        Long gameId,
        Long playerId,
        Long teamId,
        Long homeTeamId,
        Long awayTeamId,
        LocalDateTime snapshotTime,
        LocalDateTime dataCutoffTime,
        Map<String, Object> features) {
}
