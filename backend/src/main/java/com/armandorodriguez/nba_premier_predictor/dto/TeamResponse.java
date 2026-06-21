package com.armandorodriguez.nba_premier_predictor.dto;

import com.armandorodriguez.nba_premier_predictor.domain.Team;

public record TeamResponse(
        Long id,
        String city,
        String name,
        String fullName,
        String abbreviation,
        Integer seasonFounded,
        Integer seasonActiveTill,
        String league) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getCity(),
                team.getName(),
                team.getFullName(),
                team.getAbbreviation(),
                team.getSeasonFounded(),
                team.getSeasonActiveTill(),
                team.getLeague());
    }
}
