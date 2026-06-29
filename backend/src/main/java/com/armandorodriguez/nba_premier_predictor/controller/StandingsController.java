package com.armandorodriguez.nba_premier_predictor.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.SeasonSimulationResponse;
import com.armandorodriguez.nba_premier_predictor.dto.StandingsProjectionResponse;
import com.armandorodriguez.nba_premier_predictor.service.StandingsProjectionService;

@Validated
@RestController
@RequestMapping("/api/standings")
public class StandingsController {

    private final StandingsProjectionService standingsProjectionService;

    public StandingsController(StandingsProjectionService standingsProjectionService) {
        this.standingsProjectionService = standingsProjectionService;
    }

    @GetMapping("/projections")
    StandingsProjectionResponse projections(@RequestParam(required = false) Integer season) {
        return standingsProjectionService.projections(season);
    }

    @GetMapping("/projections/{season}")
    StandingsProjectionResponse projectionsBySeason(@PathVariable Integer season) {
        return standingsProjectionService.projections(season);
    }

    @PostMapping("/simulate")
    SeasonSimulationResponse simulate(
            @RequestParam(required = false) Integer season,
            @RequestParam(required = false) Integer runs) {
        return standingsProjectionService.simulate(season, runs);
    }
}
