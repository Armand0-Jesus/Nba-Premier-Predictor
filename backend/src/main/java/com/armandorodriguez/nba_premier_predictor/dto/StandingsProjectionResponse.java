package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.Instant;
import java.util.List;

public record StandingsProjectionResponse(
        Integer seasonStartYear,
        String seasonLabel,
        Instant generatedAt,
        boolean scheduleAvailable,
        String projectionMethod,
        List<TeamProjectionResponse> easternConference,
        List<TeamProjectionResponse> westernConference) {
}
