package com.armandorodriguez.nba_premier_predictor.feature;

import java.time.LocalDateTime;

public record TeamFeatureRow(
        Long teamId,
        LocalDateTime gameDateTime,
        Integer teamScore,
        Integer opponentScore) {
}
