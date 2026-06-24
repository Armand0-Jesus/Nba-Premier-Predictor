package com.armandorodriguez.nba_premier_predictor.feature;

import java.time.LocalDateTime;

public record TeamFeatureRow(
        Long gameId,
        Long teamId,
        Long opponentTeamId,
        Integer seasonStartYear,
        LocalDateTime gameDateTime,
        Boolean home,
        Integer teamScore,
        Integer opponentScore,
        Integer assists,
        Integer rebounds,
        Integer turnovers,
        Double averageTeamAge,
        Double starterAverageAge,
        Double rotationAverageAge,
        Boolean youngTeamFlag,
        Boolean veteranTeamFlag,
        Double teamRosterTurnoverScore,
        Double teamMinutesVacatedByDepartures,
        Double teamUsageVacatedByDepartures,
        Integer teamMissingStartersCount,
        Integer projectedStartersAvailableCount) {
}
