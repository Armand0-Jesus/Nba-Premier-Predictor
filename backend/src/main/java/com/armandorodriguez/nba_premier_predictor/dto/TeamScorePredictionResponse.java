package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TeamScorePredictionResponse(
        Long predictionId,
        @JsonAlias("model_version") String modelVersion,
        @JsonAlias("trained_rows") int trainedRows,
        @JsonAlias("game_id") Long gameId,
        @JsonAlias("home_team_id") Long homeTeamId,
        @JsonAlias("away_team_id") Long awayTeamId,
        @JsonAlias("home_team_score") Double homeTeamScore,
        @JsonAlias("away_team_score") Double awayTeamScore,
        @JsonAlias("predicted_winner_team_id") Long predictedWinnerTeamId,
        @JsonAlias("point_differential") Double pointDifferential,
        @JsonAlias("confidence_score") Double confidenceScore,
        List<Map<String, Object>> factors) {

    public TeamScorePredictionResponse withPredictionId(Long predictionId) {
        return new TeamScorePredictionResponse(
                predictionId,
                modelVersion,
                trainedRows,
                gameId,
                homeTeamId,
                awayTeamId,
                homeTeamScore,
                awayTeamScore,
                predictedWinnerTeamId,
                pointDifferential,
                confidenceScore,
                factors);
    }
}
