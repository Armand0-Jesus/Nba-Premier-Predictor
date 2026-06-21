package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.armandorodriguez.nba_premier_predictor.domain.Game;

public record GameResponse(
        Long id,
        Integer seasonStartYear,
        LocalDateTime gameDateTimeEst,
        LocalDate gameDate,
        Long homeTeamId,
        String homeTeamName,
        Long awayTeamId,
        String awayTeamName,
        Integer homeScore,
        Integer awayScore,
        Long winnerTeamId,
        String gameType,
        String gameLabel,
        String gameSubLabel,
        String arenaName,
        String arenaCity,
        String arenaState) {

    public static GameResponse from(Game game) {
        return new GameResponse(
                game.getId(),
                game.getSeasonStartYear(),
                game.getGameDateTimeEst(),
                game.getGameDate(),
                game.getHomeTeamId(),
                teamName(game.getHomeTeam(), game.getHomeTeamCity(), game.getHomeTeamName()),
                game.getAwayTeamId(),
                teamName(game.getAwayTeam(), game.getAwayTeamCity(), game.getAwayTeamName()),
                game.getHomeScore(),
                game.getAwayScore(),
                game.getWinnerTeamId(),
                game.getGameType(),
                game.getGameLabel(),
                game.getGameSubLabel(),
                game.getArenaName(),
                game.getArenaCity(),
                game.getArenaState());
    }

    private static String teamName(com.armandorodriguez.nba_premier_predictor.domain.Team team, String city, String name) {
        return team == null ? String.join(" ", city == null ? "" : city, name == null ? "" : name).trim() : team.getFullName();
    }
}
