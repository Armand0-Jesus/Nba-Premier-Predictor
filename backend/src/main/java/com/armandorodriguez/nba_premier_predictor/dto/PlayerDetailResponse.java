package com.armandorodriguez.nba_premier_predictor.dto;

import java.time.LocalDate;

import com.armandorodriguez.nba_premier_predictor.domain.Player;

public record PlayerDetailResponse(
        Long id,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        String school,
        String country,
        Integer heightInches,
        Integer bodyWeightLbs,
        String jersey,
        String position,
        Integer draftYear,
        Integer draftRound,
        Integer draftNumber,
        Integer fromYear,
        Integer toYear,
        boolean active) {

    public static PlayerDetailResponse from(Player player) {
        return new PlayerDetailResponse(
                player.getId(),
                player.getFirstName(),
                player.getLastName(),
                player.getFullName(),
                player.getBirthDate(),
                player.getSchool(),
                player.getCountry(),
                player.getHeightInches(),
                player.getBodyWeightLbs(),
                player.getJersey(),
                player.getPosition(),
                player.getDraftYear(),
                player.getDraftRound(),
                player.getDraftNumber(),
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
        LocalDate today = LocalDate.now();
        return today.getMonthValue() >= 10 ? today.getYear() : today.getYear() - 1;
    }
}
