package com.armandorodriguez.nba_premier_predictor.service;

import java.util.List;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.domain.Team;
import com.armandorodriguez.nba_premier_predictor.dto.TeamDashboardResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamRecordResponse;
import com.armandorodriguez.nba_premier_predictor.dto.SeasonResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.repository.GameRepository;
import com.armandorodriguez.nba_premier_predictor.repository.TeamGameStatsRepository;
import com.armandorodriguez.nba_premier_predictor.repository.TeamRepository;

@Service
@Transactional(readOnly = true)
public class TeamService {

    private static final Set<Long> CURRENT_NBA_TEAM_IDS = Set.of(
            1610612737L, 1610612738L, 1610612739L, 1610612740L, 1610612741L,
            1610612742L, 1610612743L, 1610612744L, 1610612745L, 1610612746L,
            1610612747L, 1610612748L, 1610612749L, 1610612750L, 1610612751L,
            1610612752L, 1610612753L, 1610612754L, 1610612755L, 1610612756L,
            1610612757L, 1610612758L, 1610612759L, 1610612760L, 1610612761L,
            1610612762L, 1610612763L, 1610612764L, 1610612765L, 1610612766L);

    private final TeamRepository teamRepository;
    private final TeamGameStatsRepository statsRepository;
    private final GameRepository gameRepository;
    private final SeasonService seasonService;

    public TeamService(TeamRepository teamRepository, TeamGameStatsRepository statsRepository, GameRepository gameRepository, SeasonService seasonService) {
        this.teamRepository = teamRepository;
        this.statsRepository = statsRepository;
        this.gameRepository = gameRepository;
        this.seasonService = seasonService;
    }

    public Page<TeamResponse> search(String query, boolean currentOnly, Pageable pageable) {
        return teamRepository.search(clean(query), currentOnly, CURRENT_NBA_TEAM_IDS, pageable).map(this::teamResponse);
    }

    @Cacheable(cacheNames = "teamDetails", key = "#teamId")
    public TeamResponse get(Long teamId) {
        return teamResponse(findTeam(teamId));
    }

    @Cacheable(cacheNames = "teamDashboards", key = "'v4:' + #teamId + ':' + (#season == null ? 'all' : #season)")
    public TeamDashboardResponse dashboard(Long teamId, Integer season) {
        Team team = findTeam(teamId);
        List<TeamGameLogResponse> recentGames = statsRepository
                .findGameLogs(teamId, season, null, PageRequest.of(0, 10))
                .map(row -> withRecordAfterGame(TeamGameLogResponse.from(row), season))
                .toList();
        TeamRecordResponse record = TeamRecordResponse.of(
                gameRepository.countRegularSeasonResults(teamId, season, true),
                gameRepository.countRegularSeasonResults(teamId, season, false));
        return new TeamDashboardResponse(teamResponse(team), record, recentGames);
    }

    public Page<TeamGameLogResponse> gameLogs(Long teamId, Integer season, String query, Pageable pageable) {
        findTeam(teamId);
        return statsRepository.findGameLogs(teamId, season, searchPattern(query), pageable)
                .map(row -> withRecordAfterGame(TeamGameLogResponse.from(row), season));
    }

    public List<SeasonResponse> seasons(Long teamId) {
        findTeam(teamId);
        return seasonService.teamSeasons(teamId);
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    private TeamResponse teamResponse(Team team) {
        return TeamResponse.from(
                team,
                FranchiseMetadata.foundedYear(team),
                FranchiseMetadata.conference(team.getId()),
                gameRepository.championshipYears(team.getId()));
    }

    private TeamGameLogResponse withRecordAfterGame(TeamGameLogResponse row, Integer requestedSeason) {
        if (row.gameDateTimeEst() == null || row.teamId() == null) {
            return row;
        }
        Integer season = requestedSeason == null ? row.seasonStartYear() : requestedSeason;
        long wins = gameRepository.countRegularSeasonResultsThrough(row.teamId(), season, true, row.gameDateTimeEst());
        long losses = gameRepository.countRegularSeasonResultsThrough(row.teamId(), season, false, row.gameDateTimeEst());
        return row.withRecordAfterGame("%d-%d".formatted(wins, losses));
    }

    private static String clean(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private static String searchPattern(String query) {
        String cleaned = clean(query);
        return cleaned == null ? null : "%" + cleaned.toLowerCase() + "%";
    }
}
