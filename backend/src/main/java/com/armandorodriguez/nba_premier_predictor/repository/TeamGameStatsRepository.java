package com.armandorodriguez.nba_premier_predictor.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.TeamGameStats;

public interface TeamGameStatsRepository extends JpaRepository<TeamGameStats, Long> {

    @EntityGraph(attributePaths = {"game", "team"})
    @Query("""
            select s
            from TeamGameStats s
            join s.game g
            where s.teamId = :teamId
              and (:season is null or g.seasonStartYear = :season)
            order by g.gameDateTimeEst desc
            """)
    List<TeamGameStats> findRecent(@Param("teamId") Long teamId, @Param("season") Integer season, Pageable pageable);
}
