package com.armandorodriguez.nba_premier_predictor.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.TeamGameStats;

public interface TeamGameStatsRepository extends JpaRepository<TeamGameStats, Long> {

    @EntityGraph(attributePaths = {"game", "team"})
    @Query(value = """
            select s
            from TeamGameStats s
            join s.game g
            where s.teamId = :teamId
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
            from TeamGameStats s
            join s.game g
            where s.teamId = :teamId
              and (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'playoffs', 'nba emirates cup', 'in-season tournament')
              and (:query is null or lower(concat(
                    coalesce(g.homeTeamCity, ''), ' ', coalesce(g.homeTeamName, ''), ' ',
                    coalesce(g.awayTeamCity, ''), ' ', coalesce(g.awayTeamName, ''), ' ',
                    coalesce(g.gameLabel, ''), ' ', coalesce(g.gameSubLabel, ''), ' ',
                    cast(g.id as string), ' ', cast(g.gameDate as string)
                  )) like :query)
            """)
    Page<TeamGameStats> findGameLogs(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("query") String query,
            Pageable pageable);

    @EntityGraph(attributePaths = {"game", "team"})
    @Query("""
            select s
            from TeamGameStats s
            where s.gameId = :gameId
            order by s.home desc
            """)
    List<TeamGameStats> findForGame(@Param("gameId") Long gameId);

}
