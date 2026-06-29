package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.Instant;
import java.util.List;

public record SeasonSimulationResponse(
        Long simulationRunId,
        Integer seasonStartYear,
        Integer runCount,
        boolean scheduleAvailable,
        Instant generatedAt,
        String notes,
        List<TeamProjectionResponse> projectedRecords) {
}
