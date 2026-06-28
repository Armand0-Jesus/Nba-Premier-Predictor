package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDate;

import com.armandorodriguez.nba_premier_predictor.domain.Player;
import com.armandorodriguez.nba_premier_predictor.util.NbaSeasonResolver;

public record PlayerSummaryResponse(
        Long id,
        String fullName,
        String position,
        Integer fromYear,
        Integer toYear,
        boolean active) {

    public static PlayerSummaryResponse from(Player player) {
        return new PlayerSummaryResponse(
                player.getId(),
                player.getFullName(),
                player.getPosition(),
                player.getFromYear(),
                player.getToYear(),
                isActive(player));
    }

    private static boolean isActive(Player player) {
        Integer toYear = player.getToYear();
        int currentSeasonStart = currentSeasonStartYear();
        return player.isNbaFlag() && (toYear == null || toYear >= currentSeasonStart);
    }

    private static int currentSeasonStartYear() {
        return NbaSeasonResolver.seasonStartYear(LocalDate.now());
    }
}
