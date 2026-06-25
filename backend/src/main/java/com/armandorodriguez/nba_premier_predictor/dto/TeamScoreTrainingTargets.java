package com.armandorodriguez.nba_premier_predictor.dto;

public record TeamScoreTrainingTargets(
        Integer homeScore,
        Integer awayScore,
        Long winnerTeamId,
        Integer pointDifferential) {
}
