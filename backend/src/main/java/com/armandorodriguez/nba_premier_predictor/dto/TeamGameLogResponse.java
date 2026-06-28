package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.armandorodriguez.nba_premier_predictor.domain.TeamGameStats;

public record TeamGameLogResponse(
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
        String recordAfterGame,
        Integer assists,
        Integer rebounds,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        Integer fieldGoalsMade,
        Integer fieldGoalsAttempted,
        BigDecimal fieldGoalPercentage,
        BigDecimal minutes) {

    public static TeamGameLogResponse from(TeamGameStats stats) {
        var game = stats.getGame();
        return new TeamGameLogResponse(
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
                stats.getTeamScore(),
                stats.getOpponentScore(),
                game == null ? null : game.getGameType(),
                game == null ? null : game.getGameLabel(),
                game == null ? null : game.getGameSubLabel(),
                null,
                stats.getAssists(),
                stats.getReboundsTotal(),
                stats.getSteals(),
                stats.getBlocks(),
                stats.getTurnovers(),
                stats.getFieldGoalsMade(),
                stats.getFieldGoalsAttempted(),
                stats.getFieldGoalsPercentage(),
                stats.getNumMinutes());
    }

    public TeamGameLogResponse withRecordAfterGame(String recordAfterGame) {
        return new TeamGameLogResponse(
                gameId,
                seasonStartYear,
                seasonLabel,
                gameDateTimeEst,
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
                assists,
                rebounds,
                steals,
                blocks,
                turnovers,
                fieldGoalsMade,
                fieldGoalsAttempted,
                fieldGoalPercentage,
                minutes);
    }

    private static String opponentName(TeamGameStats stats) {
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

    private static String teamName(com.armandorodriguez.nba_premier_predictor.domain.Team team, String city, String name) {
        return team == null ? String.join(" ", city == null ? "" : city, name == null ? "" : name).trim() : team.getFullName();
    }
}
