package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;

public record PlayerBoxScoreResponse(
        Long playerId,
        String playerName,
        Long teamId,
        String teamName,
        String startingPosition,
        BigDecimal minutes,
        Integer points,
        Integer rebounds,
        Integer assists,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        Integer plusMinus,
        Integer fieldGoalsMade,
        Integer fieldGoalsAttempted,
        Integer threePointersMade,
        Integer threePointersAttempted,
        Integer freeThrowsMade,
        Integer freeThrowsAttempted,
        Integer fouls,
        String comment) {

    public static PlayerBoxScoreResponse from(PlayerGameStats stats) {
        return new PlayerBoxScoreResponse(
                stats.getPlayerId(),
                stats.getPlayer() == null ? null : stats.getPlayer().getFullName(),
                stats.getTeamId(),
                stats.getTeam() == null ? null : stats.getTeam().getFullName(),
                stats.getStartingPosition(),
                stats.getNumMinutes(),
                stats.getPoints(),
                stats.getReboundsTotal(),
                stats.getAssists(),
                stats.getSteals(),
                stats.getBlocks(),
                stats.getTurnovers(),
                stats.getPlusMinusPoints(),
                stats.getFieldGoalsMade(),
                stats.getFieldGoalsAttempted(),
                stats.getThreePointersMade(),
                stats.getThreePointersAttempted(),
                stats.getFreeThrowsMade(),
                stats.getFreeThrowsAttempted(),
                stats.getFoulsPersonal(),
                stats.getComment());
    }
}
