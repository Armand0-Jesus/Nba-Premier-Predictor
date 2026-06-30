package com.armandorodriguez.nba_premier_predictor.service;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.repository.GameRepository;

@Service
public class GameLogRecordService {

    private final GameRepository gameRepository;

    public GameLogRecordService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public String recordAfterGame(Long teamId, Integer season, String gameType, LocalDate through) {
        if (teamId == null || through == null) {
            return null;
        }
        boolean playoffs = "playoffs".equals(String.valueOf(gameType).toLowerCase(Locale.ROOT));
        long wins = playoffs
                ? gameRepository.countPlayoffResultsThrough(teamId, season, true, through)
                : gameRepository.countRegularSeasonResultsThrough(teamId, season, true, through);
        long losses = playoffs
                ? gameRepository.countPlayoffResultsThrough(teamId, season, false, through)
                : gameRepository.countRegularSeasonResultsThrough(teamId, season, false, through);
        return "%d-%d".formatted(wins, losses);
    }
}
