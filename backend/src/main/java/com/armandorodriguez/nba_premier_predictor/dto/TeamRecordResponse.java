package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record TeamRecordResponse(
        long wins,
        long losses,
        double winPercentage) {

    public static TeamRecordResponse of(long wins, long losses) {
        long games = wins + losses;
        double percentage = games == 0
                ? 0.0
                : BigDecimal.valueOf((double) wins / games).setScale(3, RoundingMode.HALF_UP).doubleValue();
        return new TeamRecordResponse(wins, losses, percentage);
    }
}
