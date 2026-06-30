package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;

public record PlayerGameLogResponse(
        Long gameId,
        Integer seasonStartYear,
        String seasonLabel,
        LocalDateTime gameDateTimeEst,
        LocalDate gameDate,
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
        String recordAfterGame,
        BigDecimal minutes,
        Integer points,
        Integer rebounds,
        Integer assists,
        BigDecimal fieldGoalPercentage,
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
                game == null ? null : game.getGameDate(),
                teamId(stats),
                teamName(stats),
                opponentTeamId(stats),
                opponentName(stats),
                stats.getHome(),
                stats.getWin(),
                teamScore(stats),
                opponentScore(stats),
                game == null ? null : game.getGameType(),
                game == null ? null : game.getGameLabel(),
                game == null ? null : game.getGameSubLabel(),
                null,
                stats.getNumMinutes(),
                stats.getPoints(),
                stats.getReboundsTotal(),
                stats.getAssists(),
                stats.getFieldGoalsPercentage(),
                stats.getSteals(),
                stats.getBlocks(),
                stats.getTurnovers(),
                stats.getPlusMinusPoints(),
                stats.getStartingPosition(),
                stats.getComment());
    }

    public PlayerGameLogResponse withRecordAfterGame(String recordAfterGame) {
        return new PlayerGameLogResponse(
                gameId,
                seasonStartYear,
                seasonLabel,
                gameDateTimeEst,
                gameDate,
                teamId,
                teamName,
                opponentTeamId,
                opponentTeamName,
                home,
                win,
                teamScore,
                opponentScore,
                gameType,
                gameLabel,
                gameSubLabel,
                recordAfterGame,
                minutes,
                points,
                rebounds,
                assists,
                fieldGoalPercentage,
                steals,
                blocks,
                turnovers,
                plusMinus,
                startingPosition,
                comment);
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

    private static String opponentName(PlayerGameStats stats) {
        var game = stats.getGame();
        Long opponentTeamId = opponentTeamId(stats);
        if (game == null || opponentTeamId == null) {
            return null;
        }
        if (opponentTeamId.equals(game.getHomeTeamId())) {
            return teamName(game.getHomeTeam(), game.getHomeTeamCity(), game.getHomeTeamName());
        }
        if (opponentTeamId.equals(game.getAwayTeamId())) {
            return teamName(game.getAwayTeam(), game.getAwayTeamCity(), game.getAwayTeamName());
        }
        return null;
    }

    private static Long opponentTeamId(PlayerGameStats stats) {
        if (stats.getOpponentTeamId() != null) {
            return stats.getOpponentTeamId();
        }
        var game = stats.getGame();
        if (game == null) {
            return null;
        }
        if (stats.getTeamId() != null && stats.getTeamId().equals(game.getHomeTeamId())) {
            return game.getAwayTeamId();
        }
        if (stats.getTeamId() != null && stats.getTeamId().equals(game.getAwayTeamId())) {
            return game.getHomeTeamId();
        }
        if (Boolean.TRUE.equals(stats.getHome())) {
            return game.getAwayTeamId();
        }
        if (Boolean.FALSE.equals(stats.getHome())) {
            return game.getHomeTeamId();
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
