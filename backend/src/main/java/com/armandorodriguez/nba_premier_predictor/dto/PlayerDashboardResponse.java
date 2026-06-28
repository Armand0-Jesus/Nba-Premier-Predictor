package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;

public record PlayerDashboardResponse(
        PlayerDetailResponse player,
        PlayerAveragesResponse averages,
        List<PlayerSeasonTeamResponse> seasonTeams,
        List<PlayerGameLogResponse> recentGames) {
}
