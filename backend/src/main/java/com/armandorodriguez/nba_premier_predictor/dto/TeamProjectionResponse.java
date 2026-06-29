package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;

public record TeamProjectionResponse(
        Long teamId,
        String teamName,
        String abbreviation,
        String conference,
        Integer projectedSeed,
        Double projectedWins,
        Double projectedLosses,
        Double lowWins,
        Double medianWins,
        Double highWins,
        Double playoffProbability,
        Double strengthRating,
        Double rosterImpactScore,
        Double rosterTurnoverScore,
        Double injuryRiskScore,
        Integer sourceSeasonStartYear,
        String sourceSeasonLabel,
        List<String> topReasons,
        List<String> uncertaintyFactors) {
}
