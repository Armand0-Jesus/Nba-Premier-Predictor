package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDate;

public record SeasonResponse(
        Integer seasonStartYear,
        String label,
        long gameCount,
        LocalDate mostRecentGameDate) {

    public static SeasonResponse of(Integer seasonStartYear, long gameCount, LocalDate mostRecentGameDate) {
        return new SeasonResponse(seasonStartYear, label(seasonStartYear), gameCount, mostRecentGameDate);
    }

    public static String label(Integer seasonStartYear) {
        return seasonStartYear == null ? "Unknown" : seasonStartYear + "-" + (seasonStartYear + 1);
    }
}
