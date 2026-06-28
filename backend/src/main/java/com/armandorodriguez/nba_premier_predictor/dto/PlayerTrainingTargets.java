package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;

public record PlayerTrainingTargets(
        Integer points,
        Integer rebounds,
        Integer assists,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        Integer fieldGoalsMade,
        Integer fieldGoalsAttempted,
        BigDecimal minutes,
        Double fantasyPoints) {
}
