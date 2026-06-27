package com.armandorodriguez.nba_premier_predictor.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.armandorodriguez.nba_premier_predictor.dto.GameResponse;
import com.armandorodriguez.nba_premier_predictor.dto.GameBoxScoreResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerBoxScoreResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.repository.GameRepository;
import com.armandorodriguez.nba_premier_predictor.repository.PlayerGameStatsRepository;
import com.armandorodriguez.nba_premier_predictor.repository.TeamGameStatsRepository;

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;
    private final TeamGameStatsRepository teamStatsRepository;
    private final PlayerGameStatsRepository playerStatsRepository;

    public GameService(
            GameRepository gameRepository,
            TeamGameStatsRepository teamStatsRepository,
            PlayerGameStatsRepository playerStatsRepository) {
        this.gameRepository = gameRepository;
        this.teamStatsRepository = teamStatsRepository;
        this.playerStatsRepository = playerStatsRepository;
    }

    public Page<GameResponse> search(Integer season, Long teamId, String gameType, String query, Pageable pageable) {
        return gameRepository.search(season, teamId, clean(gameType), searchPattern(query), pageable).map(GameResponse::from);
    }

    @Cacheable(cacheNames = "gameDetails", key = "#gameId")
    public GameResponse get(Long gameId) {
        return gameRepository.findById(gameId)
                .map(GameResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
    }

    public GameBoxScoreResponse boxScore(Long gameId) {
        var game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
        List<TeamGameLogResponse> teamRows = teamStatsRepository.findForGame(gameId).stream()
                .map(TeamGameLogResponse::from)
                .toList();
        List<PlayerBoxScoreResponse> playerRows = playerStatsRepository.findForGame(gameId).stream()
                .map(PlayerBoxScoreResponse::from)
                .toList();
        return new GameBoxScoreResponse(
                GameResponse.from(game),
                teamRows.stream().filter(row -> game.getHomeTeamId() != null && game.getHomeTeamId().equals(row.teamId())).findFirst().orElse(null),
                teamRows.stream().filter(row -> game.getAwayTeamId() != null && game.getAwayTeamId().equals(row.teamId())).findFirst().orElse(null),
                playerRows.stream().filter(row -> game.getHomeTeamId() != null && game.getHomeTeamId().equals(row.teamId())).toList(),
                playerRows.stream().filter(row -> game.getAwayTeamId() != null && game.getAwayTeamId().equals(row.teamId())).toList());
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private static String searchPattern(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : "%" + cleaned + "%";
    }
}
