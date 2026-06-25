package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.service.MlPredictionClient;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final MlPredictionClient mlPredictionClient;

    public ModelController(MlPredictionClient mlPredictionClient) {
        this.mlPredictionClient = mlPredictionClient;
    }

    @GetMapping("/metrics")
    @Cacheable(cacheNames = "modelMetrics", key = "'latest'")
    public Map<String, Object> metrics() {
        return mlPredictionClient.modelMetrics();
    }

    @GetMapping("/versions")
    @Cacheable(cacheNames = "modelVersions", key = "'latest'")
    public Map<String, Object> versions() {
        return mlPredictionClient.modelVersions();
    }
}
