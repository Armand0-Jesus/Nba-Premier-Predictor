package com.armandorodriguez.nba_premier_predictor.repository;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("""
            select t
            from Team t
            where (:currentOnly = false
                   or t.id in :currentTeamIds)
              and (:query is null
                   or lower(concat(coalesce(t.city, ''), ' ', coalesce(t.name, '')))
                        like lower(concat('%', :query, '%'))
                   or lower(coalesce(t.abbreviation, '')) = lower(:query)
                   or cast(t.id as string) = :query)
            order by lower(t.name), lower(t.city), t.id
            """)
    Page<Team> search(
            @Param("query") String query,
            @Param("currentOnly") boolean currentOnly,
            @Param("currentTeamIds") Collection<Long> currentTeamIds,
            Pageable pageable);
}
