package com.armandorodriguez.nba_premier_predictor.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.PlayerGameStats;

public interface PlayerGameStatsRepository extends JpaRepository<PlayerGameStats, Long> {

    @EntityGraph(attributePaths = {"game", "team"})
    @Query(value = """
            select s
            from PlayerGameStats s
            join s.game g
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
            order by g.gameDateTimeEst desc
            """, countQuery = """
            select count(s)
            from PlayerGameStats s
            join s.game g
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
            """)
    Page<PlayerGameStats> findGameLogs(
            @Param("playerId") Long playerId,
            @Param("season") Integer season,
            Pageable pageable);

    @EntityGraph(attributePaths = {"game", "team"})
    @Query("""
            select s
            from PlayerGameStats s
            join s.game g
            where s.playerId = :playerId
              and (:season is null or g.seasonStartYear = :season)
            order by g.gameDateTimeEst desc
            """)
    List<PlayerGameStats> findForAverages(@Param("playerId") Long playerId, @Param("season") Integer season);
}
