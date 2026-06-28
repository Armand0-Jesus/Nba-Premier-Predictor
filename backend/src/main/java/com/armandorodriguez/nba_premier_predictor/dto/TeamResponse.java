package com.armandorodriguez.nba_premier_predictor.dto;

import java.util.List;

import com.armandorodriguez.nba_premier_predictor.domain.Team;

public record TeamResponse(
        Long id,
        String city,
        String name,
        String fullName,
        String abbreviation,
        Integer seasonFounded,
        Integer seasonActiveTill,
        String league,
        String conference,
        List<Integer> championshipYears) {

    public static TeamResponse from(Team team) {
        return from(team, team.getSeasonFounded(), null, List.of());
    }

    public static TeamResponse from(Team team, Integer seasonFounded, String conference, List<Integer> championshipYears) {
        return new TeamResponse(
                team.getId(),
                team.getCity(),
                team.getName(),
                team.getFullName(),
                team.getAbbreviation(),
                seasonFounded,
                team.getSeasonActiveTill(),
                team.getLeague(),
                conference,
                championshipYears == null ? List.of() : championshipYears);
    }
}
