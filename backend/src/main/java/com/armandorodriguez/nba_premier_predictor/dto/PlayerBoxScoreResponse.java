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
        BigDecimal fieldGoalPercentage,
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
                teamId(stats),
                teamName(stats),
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
                stats.getFieldGoalsPercentage(),
                stats.getThreePointersMade(),
                stats.getThreePointersAttempted(),
                stats.getFreeThrowsMade(),
                stats.getFreeThrowsAttempted(),
                stats.getFoulsPersonal(),
                stats.getComment());
    }

    private static Long teamId(PlayerGameStats stats) {
        if (stats.getTeamId() != null) {
            return stats.getTeamId();
        }
        var game = stats.getGame();
        if (game == null) {
            return null;
        }
        if (Boolean.TRUE.equals(stats.getHome())) {
            return game.getHomeTeamId();
        }
        if (Boolean.FALSE.equals(stats.getHome())) {
            return game.getAwayTeamId();
        }
        return null;
    }

    private static String teamName(PlayerGameStats stats) {
        if (stats.getTeam() != null) {
            return stats.getTeam().getFullName();
        }
        var game = stats.getGame();
        if (game == null) {
            return null;
        }
        if (Boolean.TRUE.equals(stats.getHome())) {
            return teamName(game.getHomeTeam(), game.getHomeTeamCity(), game.getHomeTeamName());
        }
        if (Boolean.FALSE.equals(stats.getHome())) {
            return teamName(game.getAwayTeam(), game.getAwayTeamCity(), game.getAwayTeamName());
        }
        return null;
    }

    private static String teamName(com.armandorodriguez.nba_premier_predictor.domain.Team team, String city, String name) {
        return team == null ? String.join(" ", city == null ? "" : city, name == null ? "" : name).trim() : team.getFullName();
    }
}
