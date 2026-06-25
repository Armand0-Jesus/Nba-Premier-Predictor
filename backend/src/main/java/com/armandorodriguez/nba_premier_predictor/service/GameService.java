package com.armandorodriguez.nba_premier_predictor.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.dto.GameResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.repository.GameRepository;

@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public Page<GameResponse> search(Integer season, Long teamId, String gameType, Pageable pageable) {
        return gameRepository.search(season, teamId, clean(gameType), pageable).map(GameResponse::from);
    }

    @Cacheable(cacheNames = "gameDetails", key = "#gameId")
    public GameResponse get(Long gameId) {
        return gameRepository.findById(gameId)
                .map(GameResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found: " + gameId));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
