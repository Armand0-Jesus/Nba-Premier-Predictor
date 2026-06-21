package com.armandorodriguez.nba_premier_predictor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.armandorodriguez.nba_premier_predictor.domain.TeamGameStats;

public record TeamGameLogResponse(
        Long gameId,
        LocalDateTime gameDateTimeEst,
        Long teamId,
        Long opponentTeamId,
        Boolean home,
        Boolean win,
        Integer teamScore,
        Integer opponentScore,
        Integer assists,
        Integer rebounds,
        Integer steals,
        Integer blocks,
        Integer turnovers,
        BigDecimal minutes) {

    public static TeamGameLogResponse from(TeamGameStats stats) {
        return new TeamGameLogResponse(
                stats.getGameId(),
                stats.getGame() == null ? null : stats.getGame().getGameDateTimeEst(),
                stats.getTeamId(),
                stats.getOpponentTeamId(),
                stats.getHome(),
                stats.getWin(),
                stats.getTeamScore(),
                stats.getOpponentScore(),
                stats.getAssists(),
                stats.getReboundsTotal(),
                stats.getSteals(),
                stats.getBlocks(),
                stats.getTurnovers(),
                stats.getNumMinutes());
    }
}
