package com.armandorodriguez.nba_premier_predictor.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.armandorodriguez.nba_premier_predictor.domain.Team;
import com.armandorodriguez.nba_premier_predictor.dto.TeamDashboardResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamGameLogResponse;
import com.armandorodriguez.nba_premier_predictor.dto.TeamResponse;
import com.armandorodriguez.nba_premier_predictor.exception.ResourceNotFoundException;
import com.armandorodriguez.nba_premier_predictor.repository.TeamGameStatsRepository;
import com.armandorodriguez.nba_premier_predictor.repository.TeamRepository;

@Service
@Transactional(readOnly = true)
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamGameStatsRepository statsRepository;

    public TeamService(TeamRepository teamRepository, TeamGameStatsRepository statsRepository) {
        this.teamRepository = teamRepository;
        this.statsRepository = statsRepository;
    }

    public Page<TeamResponse> search(String query, Pageable pageable) {
        return teamRepository.search(clean(query), pageable).map(TeamResponse::from);
    }

    public TeamResponse get(Long teamId) {
        return TeamResponse.from(findTeam(teamId));
    }

    public TeamDashboardResponse dashboard(Long teamId, Integer season) {
        Team team = findTeam(teamId);
        List<TeamGameLogResponse> recentGames = statsRepository
                .findRecent(teamId, season, PageRequest.of(0, 10))
                .stream()
                .map(TeamGameLogResponse::from)
                .toList();
        return new TeamDashboardResponse(TeamResponse.from(team), recentGames);
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
    }

    private static String clean(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }
}
