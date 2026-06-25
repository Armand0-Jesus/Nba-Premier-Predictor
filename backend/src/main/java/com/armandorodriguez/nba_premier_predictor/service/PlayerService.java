package com.armandorodriguez.nba_premier_predictor.service;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.domain.Player;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerAveragesResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerDashboardResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerDetailResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerSummaryResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.repository.PlayerGameStatsRepository;
import com.armandorodriguez.nba_premier_predictor.repository.PlayerRepository;

@Service
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerGameStatsRepository statsRepository;

    public PlayerService(PlayerRepository playerRepository, PlayerGameStatsRepository statsRepository) {
        this.playerRepository = playerRepository;
        this.statsRepository = statsRepository;
    }

    public Page<PlayerSummaryResponse> search(String query, Pageable pageable) {
        return playerRepository.search(clean(query), pageable).map(PlayerSummaryResponse::from);
    }

    @Cacheable(cacheNames = "playerDetails", key = "#playerId")
    public PlayerDetailResponse get(Long playerId) {
        return PlayerDetailResponse.from(findPlayer(playerId));
    }

    public Page<PlayerGameLogResponse> gameLogs(Long playerId, Integer season, Pageable pageable) {
        findPlayer(playerId);
        return statsRepository.findGameLogs(playerId, season, pageable).map(PlayerGameLogResponse::from);
    }

    @Cacheable(cacheNames = "playerAverages", key = "#playerId + ':' + (#season == null ? 'all' : #season)")
    public PlayerAveragesResponse averages(Long playerId, Integer season) {
        findPlayer(playerId);
        return PlayerAveragesResponse.from(playerId, season, statsRepository.findForAverages(playerId, season));
    }

    @Cacheable(cacheNames = "playerDashboards", key = "#playerId + ':' + (#season == null ? 'all' : #season)")
    public PlayerDashboardResponse dashboard(Long playerId, Integer season) {
        Player player = findPlayer(playerId);
        PlayerAveragesResponse averages = PlayerAveragesResponse.from(playerId, season, statsRepository.findForAverages(playerId, season));
        List<PlayerGameLogResponse> recentGames = statsRepository
                .findGameLogs(playerId, season, PageRequest.of(0, 10))
                .map(PlayerGameLogResponse::from)
                .toList();
        return new PlayerDashboardResponse(PlayerDetailResponse.from(player), averages, recentGames);
    }

    private Player findPlayer(Long playerId) {
        return playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player not found: " + playerId));
    }

    private static String clean(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }
}
