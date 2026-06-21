package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;

public record PlayerAveragesResponse(
        Long playerId,
        Integer seasonStartYear,
        int gamesPlayed,
        double minutes,
        double points,
        double rebounds,
        double assists,
        double steals,
        double blocks,
        double turnovers) {

    public static PlayerAveragesResponse from(Long playerId, Integer seasonStartYear, List<PlayerGameStats> stats) {
        return new PlayerAveragesResponse(
                playerId,
                seasonStartYear,
                stats.size(),
                averageDecimal(stats, PlayerGameStats::getNumMinutes),
                averageInteger(stats, PlayerGameStats::getPoints),
                averageInteger(stats, PlayerGameStats::getReboundsTotal),
                averageInteger(stats, PlayerGameStats::getAssists),
                averageInteger(stats, PlayerGameStats::getSteals),
                averageInteger(stats, PlayerGameStats::getBlocks),
                averageInteger(stats, PlayerGameStats::getTurnovers));
    }

    private static double averageInteger(List<PlayerGameStats> stats, Function<PlayerGameStats, Integer> getter) {
        return round(stats.stream().map(getter).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0));
    }

    private static double averageDecimal(List<PlayerGameStats> stats, Function<PlayerGameStats, BigDecimal> getter) {
        return round(stats.stream().map(getter).filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(0));
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
