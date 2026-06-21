package com.armandorodriguez.nba_premier_predictor.dto;

import com.armandorodriguez.nba_premier_predictor.domain.Player;

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
                player.isNbaFlag());
    }
}
