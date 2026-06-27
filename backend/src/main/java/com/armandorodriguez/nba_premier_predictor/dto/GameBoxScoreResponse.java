package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;

public record GameBoxScoreResponse(
        GameResponse game,
        TeamGameLogResponse homeTeam,
        TeamGameLogResponse awayTeam,
        List<PlayerBoxScoreResponse> homePlayers,
        List<PlayerBoxScoreResponse> awayPlayers) {
}
