package com.armandorodriguez.nba_premier_predictor.feature;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        Integer turnovers,
        Integer steals,
        Integer blocks,
        LocalDate birthDate,
        Integer fromYear,
        Integer careerGamesPlayedBeforeGame,
        BigDecimal careerMinutesPlayedBeforeGame,
        Integer injuryHistoryCountBeforeGame,
        Boolean projectedStarter,
        Boolean playerChangedTeamBeforeGame,
        Integer samePositionCompetition,
        Integer teamMissingStartersCount,
        Double teamRosterTurnoverScore,
        Double teamMinutesVacatedByDepartures,
        Double teamUsageVacatedByDepartures,
        Double teammateInjuryUsageBoost,
        Double teammateInjuryMinutesBoost) {
}
