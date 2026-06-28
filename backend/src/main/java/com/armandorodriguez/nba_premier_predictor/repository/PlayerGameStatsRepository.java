package com.armandorodriguez.nba_premier_predictor.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;
import com.armandorodriguez.nba_premier_predictor.dto.PlayerSeasonTeamResponse;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    @EntityGraph(attributePaths = {"game", "team"})
    @Query(value = """
            select s
            from PlayerGameStats s
            join s.game g
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'playoffs', 'nba emirates cup', 'in-season tournament')
              and (:query is null or lower(concat(
                    coalesce(g.homeTeamCity, ''), ' ', coalesce(g.homeTeamName, ''), ' ',
                    coalesce(g.awayTeamCity, ''), ' ', coalesce(g.awayTeamName, ''), ' ',
                    coalesce(g.gameLabel, ''), ' ', coalesce(g.gameSubLabel, ''), ' ',
                    cast(g.id as string), ' ', cast(g.gameDate as string)
                  )) like :query)
            order by g.gameDateTimeEst desc
            """, countQuery = """
            select count(s)
            from PlayerGameStats s
            join s.game g
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'playoffs', 'nba emirates cup', 'in-season tournament')
              and (:query is null or lower(concat(
                    coalesce(g.homeTeamCity, ''), ' ', coalesce(g.homeTeamName, ''), ' ',
                    coalesce(g.awayTeamCity, ''), ' ', coalesce(g.awayTeamName, ''), ' ',
                    coalesce(g.gameLabel, ''), ' ', coalesce(g.gameSubLabel, ''), ' ',
                    cast(g.id as string), ' ', cast(g.gameDate as string)
                  )) like :query)
            """)
    Page<PlayerGameStats> findGameLogs(
            @Param("playerId") Long playerId,
            @Param("season") Integer season,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = {"game", "team"})
    @Query("""
            select s
            from PlayerGameStats s
            join s.game g
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
              and s.numMinutes is not null
              and s.numMinutes > 0
            order by g.gameDateTimeEst desc
            """)
    List<PlayerGameStats> findForAverages(@Param("playerId") Long playerId, @Param("season") Integer season);

    @Query("""
            select new com.armandorodriguez.nba_premier_predictor.dto.PlayerSeasonTeamResponse(
                s.teamId,
                trim(concat(coalesce(t.city, ''), ' ', coalesce(t.name, '')))
            )
            from PlayerGameStats s
            join s.game g
            left join s.team t
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'playoffs', 'nba emirates cup', 'in-season tournament')
              and s.teamId is not null
              and s.numMinutes is not null
              and s.numMinutes > 0
            group by s.teamId, t.city, t.name
            order by min(g.gameDateTimeEst)
            """)
    List<PlayerSeasonTeamResponse> findSeasonTeams(@Param("playerId") Long playerId, @Param("season") Integer season);

    @EntityGraph(attributePaths = {"game", "team", "player"})
    @Query("""
            select s
            from PlayerGameStats s
            join s.game g
            where s.gameId = :gameId
            order by s.home desc, s.teamId, s.startingPosition, s.numMinutes desc nulls last, s.playerId
            """)
    List<PlayerGameStats> findForGame(@Param("gameId") Long gameId);
}
