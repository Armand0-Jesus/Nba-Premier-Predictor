package com.armandorodriguez.nba_premier_predictor.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.armandorodriguez.nba_premier_predictor.domain.Player;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    @Query("""
            select p
            from Player p
            where :query is null
               or lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, '')))
                    like lower(concat('%', :query, '%'))
               or cast(p.id as string) = :query
            """)
    Page<Player> search(@Param("query") String query, Pageable pageable);
}
