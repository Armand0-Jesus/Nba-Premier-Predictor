package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;

public record RosterImpactResponse(
        Integer seasonStartYear,
        Long teamId,
        String teamName,
        Integer playersAdded,
        Integer playersLost,
        Integer rookieCount,
        Integer injuryFlagCount,
        Double incomingMinutes,
        Double outgoingMinutes,
        Double rosterImpactScore,
        Double rosterTurnoverScore,
        List<String> explanations) {
}
