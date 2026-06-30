package com.armandorodriguez.nba_premier_predictor.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("""
            select g
            from Game g
            where (:season is null or g.seasonStartYear = :season)
              and (:teamId is null or g.homeTeamId = :teamId or g.awayTeamId = :teamId)
              and (:gameType is null or lower(g.gameType) = :gameType)
              and (:gameType is not null or lower(coalesce(g.gameType, '')) in ('regular season', 'playoffs', 'nba emirates cup', 'in-season tournament'))
              and (:query is null or lower(concat(
                    coalesce(g.homeTeamCity, ''), ' ', coalesce(g.homeTeamName, ''), ' ',
                    coalesce(g.awayTeamCity, ''), ' ', coalesce(g.awayTeamName, ''), ' ',
                    coalesce(g.gameLabel, ''), ' ', coalesce(g.gameSubLabel, ''), ' ',
                    coalesce(g.arenaName, ''), ' ', coalesce(g.arenaCity, ''), ' ',
                    cast(g.id as string), ' ', cast(g.gameDate as string)
                  )) like :query)
            order by g.gameDateTimeEst desc
            """)
    Page<Game> search(
            @Param("season") Integer season,
            @Param("teamId") Long teamId,
            @Param("gameType") String gameType,
            @Param("query") String query,
            Pageable pageable);

    @Query("""
            select count(g)
            from Game g
            where (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
              and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)
              and g.winnerTeamId is not null
              and ((:win = true and g.winnerTeamId = :teamId)
                or (:win = false and g.winnerTeamId <> :teamId))
            """)
    long countRegularSeasonResults(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("win") boolean win);

    @Query("""
            select count(g)
            from Game g
            where (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) in ('regular season', 'nba emirates cup', 'in-season tournament')
              and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)
              and g.gameDate <= :through
              and g.winnerTeamId is not null
              and ((:win = true and g.winnerTeamId = :teamId)
                or (:win = false and g.winnerTeamId <> :teamId))
            """)
    long countRegularSeasonResultsThrough(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("win") boolean win,
            @Param("through") LocalDate through);

    @Query("""
            select count(g)
            from Game g
            where (:season is null or g.seasonStartYear = :season)
              and lower(coalesce(g.gameType, '')) = 'playoffs'
              and (g.homeTeamId = :teamId or g.awayTeamId = :teamId)
              and g.gameDate <= :through
              and g.winnerTeamId is not null
              and ((:win = true and g.winnerTeamId = :teamId)
                or (:win = false and g.winnerTeamId <> :teamId))
            """)
    long countPlayoffResultsThrough(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("win") boolean win,
            @Param("through") LocalDate through);

    @Query("""
            select distinct g.seasonStartYear + 1
            from Game g
            where g.winnerTeamId = :teamId
              and lower(coalesce(g.gameLabel, '')) = 'nba finals'
              and g.gameDate = (
                  select max(finalGame.gameDate)
                  from Game finalGame
                  where finalGame.seasonStartYear = g.seasonStartYear
                    and lower(coalesce(finalGame.gameLabel, '')) = 'nba finals'
              )
            order by g.seasonStartYear + 1
            """)
    List<Integer> championshipYears(@Param("teamId") Long teamId);
}
