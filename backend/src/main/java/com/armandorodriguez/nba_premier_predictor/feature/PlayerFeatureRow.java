package com.armandorodriguez.nba_premier_predictor.feature;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlayerFeatureRow(
        Long gameId,
        Long playerId,
        Long teamId,
        Long opponentTeamId,
        Integer seasonStartYear,
        LocalDateTime gameDateTime,
        Boolean home,
        BigDecimal minutes,
        Integer points,
        Integer rebounds,
        Integer assists,
        Integer turnovers) {
}
