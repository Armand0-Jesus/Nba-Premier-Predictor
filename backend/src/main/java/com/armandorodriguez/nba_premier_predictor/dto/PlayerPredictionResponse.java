package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PlayerPredictionResponse(
        Long predictionId,
        @JsonAlias("model_version") String modelVersion,
        @JsonAlias("trained_rows") int trainedRows,
        @JsonAlias("game_id") Long gameId,
        @JsonAlias("player_id") Long playerId,
        @JsonAlias("team_id") Long teamId,
        @JsonAlias("projected_points") Double projectedPoints,
        @JsonAlias("projected_rebounds") Double projectedRebounds,
        @JsonAlias("projected_assists") Double projectedAssists,
        @JsonAlias("projected_minutes") Double projectedMinutes,
        @JsonAlias("projected_steals") Double projectedSteals,
        @JsonAlias("projected_blocks") Double projectedBlocks,
        @JsonAlias("projected_turnovers") Double projectedTurnovers,
        @JsonAlias("projected_field_goals_made") Double projectedFieldGoalsMade,
        @JsonAlias("projected_field_goals_attempted") Double projectedFieldGoalsAttempted,
        @JsonAlias("projected_field_goal_percentage") Double projectedFieldGoalPercentage,
        @JsonAlias("fantasy_points") Double fantasyPoints,
        @JsonAlias("fantasy_floor") Double fantasyFloor,
        @JsonAlias("fantasy_ceiling") Double fantasyCeiling,
        @JsonAlias("confidence_score") Double confidenceScore,
        @JsonAlias("risk_level") String riskLevel,
        List<Map<String, Object>> factors) {

    public PlayerPredictionResponse withPredictionId(Long predictionId) {
        return new PlayerPredictionResponse(
                predictionId,
                modelVersion,
                trainedRows,
                gameId,
                playerId,
                teamId,
                projectedPoints,
                projectedRebounds,
                projectedAssists,
                projectedMinutes,
                projectedSteals,
                projectedBlocks,
                projectedTurnovers,
                projectedFieldGoalsMade,
                projectedFieldGoalsAttempted,
                projectedFieldGoalPercentage,
                fantasyPoints,
                fantasyFloor,
                fantasyCeiling,
                confidenceScore,
                riskLevel,
                factors);
    }
}
