package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;

public record PlayerGameLogResponse(
        Long gameId,
        LocalDateTime gameDateTimeEst,
        Long teamId,
        String teamName,
        Long opponentTeamId,
        Boolean home,
        Boolean win,
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
        return new PlayerGameLogResponse(
                stats.getGameId(),
                stats.getGame() == null ? null : stats.getGame().getGameDateTimeEst(),
                stats.getTeamId(),
                stats.getTeam() == null ? null : stats.getTeam().getFullName(),
                stats.getOpponentTeamId(),
                stats.getHome(),
                stats.getWin(),
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
}
