package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;

public record TeamDashboardResponse(
        TeamResponse team,
        TeamRecordResponse regularSeasonRecord,
        List<TeamGameLogResponse> recentGames) {
}
