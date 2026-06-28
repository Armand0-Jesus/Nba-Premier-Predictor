package com.armandorodriguez.nba_premier_predictor.service;

import java.util.Map;

import com.armandorodriguez.nba_premier_predictor.domain.Team;

final class FranchiseMetadata {

    private static final Map<Long, Integer> FOUNDED_YEARS = Map.ofEntries(
            Map.entry(1610612737L, 1949),
            Map.entry(1610612738L, 1946),
            Map.entry(1610612739L, 1970),
            Map.entry(1610612740L, 2002),
            Map.entry(1610612741L, 1966),
            Map.entry(1610612742L, 1980),
            Map.entry(1610612743L, 1967),
            Map.entry(1610612744L, 1946),
            Map.entry(1610612745L, 1967),
            Map.entry(1610612746L, 1970),
            Map.entry(1610612747L, 1947),
            Map.entry(1610612748L, 1988),
            Map.entry(1610612749L, 1968),
            Map.entry(1610612750L, 1989),
            Map.entry(1610612751L, 1967),
            Map.entry(1610612752L, 1946),
            Map.entry(1610612753L, 1989),
            Map.entry(1610612754L, 1967),
            Map.entry(1610612755L, 1946),
            Map.entry(1610612756L, 1968),
            Map.entry(1610612757L, 1970),
            Map.entry(1610612758L, 1945),
            Map.entry(1610612759L, 1967),
            Map.entry(1610612760L, 1967),
            Map.entry(1610612761L, 1995),
            Map.entry(1610612762L, 1974),
            Map.entry(1610612763L, 1995),
            Map.entry(1610612764L, 1961),
            Map.entry(1610612765L, 1941),
            Map.entry(1610612766L, 1988));

    private static final Map<Long, String> CONFERENCES = Map.ofEntries(
            Map.entry(1610612737L, "Eastern"),
            Map.entry(1610612738L, "Eastern"),
            Map.entry(1610612739L, "Eastern"),
            Map.entry(1610612740L, "Western"),
            Map.entry(1610612741L, "Eastern"),
            Map.entry(1610612742L, "Western"),
            Map.entry(1610612743L, "Western"),
            Map.entry(1610612744L, "Western"),
            Map.entry(1610612745L, "Western"),
            Map.entry(1610612746L, "Western"),
            Map.entry(1610612747L, "Western"),
            Map.entry(1610612748L, "Eastern"),
            Map.entry(1610612749L, "Eastern"),
            Map.entry(1610612750L, "Western"),
            Map.entry(1610612751L, "Eastern"),
            Map.entry(1610612752L, "Eastern"),
            Map.entry(1610612753L, "Eastern"),
            Map.entry(1610612754L, "Eastern"),
            Map.entry(1610612755L, "Eastern"),
            Map.entry(1610612756L, "Western"),
            Map.entry(1610612757L, "Western"),
            Map.entry(1610612758L, "Western"),
            Map.entry(1610612759L, "Western"),
            Map.entry(1610612760L, "Western"),
            Map.entry(1610612761L, "Eastern"),
            Map.entry(1610612762L, "Western"),
            Map.entry(1610612763L, "Western"),
            Map.entry(1610612764L, "Eastern"),
            Map.entry(1610612765L, "Eastern"),
            Map.entry(1610612766L, "Eastern"));

    private FranchiseMetadata() {
    }

    static Integer foundedYear(Team team) {
        return FOUNDED_YEARS.getOrDefault(team.getId(), team.getSeasonFounded());
    }

    static String conference(Long teamId) {
        return CONFERENCES.get(teamId);
    }
}
