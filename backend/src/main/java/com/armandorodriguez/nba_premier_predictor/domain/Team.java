package com.armandorodriguez.nba_premier_predictor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "teams")
public class Team {

    @Id
    @Column(name = "team_id")
    private Long id;

    private String city;
    private String name;

    @Column(name = "abbreviation")
    private String abbreviation;

    @Column(name = "season_founded")
    private Integer seasonFounded;

    @Column(name = "season_active_till")
    private Integer seasonActiveTill;

    private String league;

    protected Team() {
    }

    public Long getId() {
        return id;
    }

    public String getCity() {
        return city;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return String.join(" ", city == null ? "" : city, name == null ? "" : name).trim();
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public Integer getSeasonFounded() {
        return seasonFounded;
    }

    public Integer getSeasonActiveTill() {
        return seasonActiveTill;
    }

    public String getLeague() {
        return league;
    }
}
