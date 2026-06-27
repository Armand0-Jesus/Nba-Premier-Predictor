package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;

public record PlayerGameLogResponse(
        Long gameId,
        Integer seasonStartYear,
        String seasonLabel,
        LocalDateTime gameDateTimeEst,
        Long teamId,
        String teamName,
        Long opponentTeamId,
        String opponentTeamName,
        Boolean home,
        Boolean win,
        Integer teamScore,
        Integer opponentScore,
        String gameType,
        String gameLabel,
        String gameSubLabel,
        BigDecimal minutes,
        Integer points,
        Integer rebounds,
        Integer assists,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        Integer plusMinus,
        String startingPosition,
        String comment) {

    public static PlayerGameLogResponse from(PlayerGameStats stats) {
        var game = stats.getGame();
        return new PlayerGameLogResponse(
                stats.getGameId(),
                game == null ? null : game.getSeasonStartYear(),
                game == null ? null : SeasonResponse.label(game.getSeasonStartYear()),
                game == null ? null : game.getGameDateTimeEst(),
                stats.getTeamId(),
                stats.getTeam() == null ? null : stats.getTeam().getFullName(),
                stats.getOpponentTeamId(),
                opponentName(stats),
                stats.getHome(),
                stats.getWin(),
                teamScore(stats),
                opponentScore(stats),
                game == null ? null : game.getGameType(),
                game == null ? null : game.getGameLabel(),
                game == null ? null : game.getGameSubLabel(),
                stats.getNumMinutes(),
                stats.getPoints(),
                stats.getReboundsTotal(),
                stats.getAssists(),
                stats.getSteals(),
                stats.getBlocks(),
                stats.getTurnovers(),
                stats.getPlusMinusPoints(),
                stats.getStartingPosition(),
                stats.getComment());
    }

    private static String opponentName(PlayerGameStats stats) {
        var game = stats.getGame();
        if (game == null || stats.getOpponentTeamId() == null) {
            return null;
        }
        if (stats.getOpponentTeamId().equals(game.getHomeTeamId())) {
            return teamName(game.getHomeTeam(), game.getHomeTeamCity(), game.getHomeTeamName());
        }
        if (stats.getOpponentTeamId().equals(game.getAwayTeamId())) {
            return teamName(game.getAwayTeam(), game.getAwayTeamCity(), game.getAwayTeamName());
        }
        return null;
    }

    private static Integer teamScore(PlayerGameStats stats) {
        var game = stats.getGame();
        if (game == null) {
            return null;
        }
        if (Boolean.TRUE.equals(stats.getHome())) {
            return game.getHomeScore();
        }
        if (Boolean.FALSE.equals(stats.getHome())) {
            return game.getAwayScore();
        }
        return null;
    }

    private static Integer opponentScore(PlayerGameStats stats) {
        var game = stats.getGame();
        if (game == null) {
            return null;
        }
        if (Boolean.TRUE.equals(stats.getHome())) {
            return game.getAwayScore();
        }
        if (Boolean.FALSE.equals(stats.getHome())) {
            return game.getHomeScore();
        }
        return null;
    }

    private static String teamName(com.armandorodriguez.nba_premier_predictor.domain.Team team, String city, String name) {
        return team == null ? String.join(" ", city == null ? "" : city, name == null ? "" : name).trim() : team.getFullName();
    }
}
