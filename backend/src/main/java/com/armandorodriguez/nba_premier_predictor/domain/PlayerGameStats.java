package com.armandorodriguez.nba_premier_predictor.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "player_game_stats")
public class PlayerGameStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id")
    private Long gameId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", insertable = false, updatable = false)
    private Game game;

    @Column(name = "player_id")
    private Long playerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", insertable = false, updatable = false)
    private Player player;

    @Column(name = "team_id")
    private Long teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", insertable = false, updatable = false)
    private Team team;

    @Column(name = "opponent_team_id")
    private Long opponentTeamId;

    private Boolean win;
    private Boolean home;

    @Column(name = "num_minutes", precision = 6, scale = 2)
    private BigDecimal numMinutes;

    private Integer points;
    private Integer assists;
    private Integer blocks;
    private Integer steals;

    @Column(name = "field_goals_attempted")
    private Integer fieldGoalsAttempted;

    @Column(name = "field_goals_made")
    private Integer fieldGoalsMade;

    @Column(name = "field_goals_percentage", precision = 5, scale = 3)
    private BigDecimal fieldGoalsPercentage;

    @Column(name = "three_pointers_attempted")
    private Integer threePointersAttempted;

    @Column(name = "three_pointers_made")
    private Integer threePointersMade;

    @Column(name = "three_pointers_percentage", precision = 5, scale = 3)
    private BigDecimal threePointersPercentage;

    @Column(name = "free_throws_attempted")
    private Integer freeThrowsAttempted;

    @Column(name = "free_throws_made")
    private Integer freeThrowsMade;

    @Column(name = "free_throws_percentage", precision = 5, scale = 3)
    private BigDecimal freeThrowsPercentage;

    @Column(name = "rebounds_defensive")
    private Integer reboundsDefensive;

    @Column(name = "rebounds_offensive")
    private Integer reboundsOffensive;

    @Column(name = "rebounds_total")
    private Integer reboundsTotal;

    @Column(name = "fouls_personal")
    private Integer foulsPersonal;

    private Integer turnovers;

    @Column(name = "plus_minus_points")
    private Integer plusMinusPoints;

    private String comment;

    @Column(name = "starting_position")
    private String startingPosition;

    protected PlayerGameStats() {
    }

    public Long getId() {
        return id;
    }

    public Long getGameId() {
        return gameId;
    }

    public Game getGame() {
        return game;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public Player getPlayer() {
        return player;
    }

    public Long getTeamId() {
        return teamId;
    }

    public Team getTeam() {
        return team;
    }

    public Long getOpponentTeamId() {
        return opponentTeamId;
    }

    public Boolean getWin() {
        return win;
    }

    public Boolean getHome() {
        return home;
    }

    public BigDecimal getNumMinutes() {
        return numMinutes;
    }

    public Integer getPoints() {
        return points;
    }

    public Integer getAssists() {
        return assists;
    }

    public Integer getBlocks() {
        return blocks;
    }

    public Integer getSteals() {
        return steals;
    }

    public Integer getFieldGoalsAttempted() {
        return fieldGoalsAttempted;
    }

    public Integer getFieldGoalsMade() {
        return fieldGoalsMade;
    }

    public BigDecimal getFieldGoalsPercentage() {
        return fieldGoalsPercentage;
    }

    public Integer getThreePointersAttempted() {
        return threePointersAttempted;
    }

    public Integer getThreePointersMade() {
        return threePointersMade;
    }

    public BigDecimal getThreePointersPercentage() {
        return threePointersPercentage;
    }

    public Integer getFreeThrowsAttempted() {
        return freeThrowsAttempted;
    }

    public Integer getFreeThrowsMade() {
        return freeThrowsMade;
    }

    public BigDecimal getFreeThrowsPercentage() {
        return freeThrowsPercentage;
    }

    public Integer getReboundsDefensive() {
        return reboundsDefensive;
    }

    public Integer getReboundsOffensive() {
        return reboundsOffensive;
    }

    public Integer getReboundsTotal() {
        return reboundsTotal;
    }

    public Integer getFoulsPersonal() {
        return foulsPersonal;
    }

    public Integer getTurnovers() {
        return turnovers;
    }

    public Integer getPlusMinusPoints() {
        return plusMinusPoints;
    }

    public String getComment() {
        return comment;
    }

    public String getStartingPosition() {
        return startingPosition;
    }
}
