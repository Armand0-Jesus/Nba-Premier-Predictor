package com.armandorodriguez.nba_premier_predictor.util;

import java.time.LocalDate;

public final class NbaSeasonResolver {

    private static final LocalDate BUBBLE_START = LocalDate.of(2020, 7, 1);
    private static final LocalDate BUBBLE_END = LocalDate.of(2020, 10, 13);

    private NbaSeasonResolver() {
    }

    public static Integer seasonStartYear(LocalDate gameDate) {
        if (gameDate == null) {
            return null;
        }
        if (!gameDate.isBefore(BUBBLE_START) && !gameDate.isAfter(BUBBLE_END)) {
            return 2019;
        }
        return gameDate.getMonthValue() >= 10 ? gameDate.getYear() : gameDate.getYear() - 1;
    }
}
