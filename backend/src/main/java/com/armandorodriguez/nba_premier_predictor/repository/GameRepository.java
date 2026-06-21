package com.armandorodriguez.nba_premier_predictor.repository;

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
              and (:gameType is null or lower(g.gameType) = lower(:gameType))
            order by g.gameDateTimeEst desc
            """)
    Page<Game> search(
            @Param("season") Integer season,
            @Param("teamId") Long teamId,
            @Param("gameType") String gameType,
            Pageable pageable);
}
