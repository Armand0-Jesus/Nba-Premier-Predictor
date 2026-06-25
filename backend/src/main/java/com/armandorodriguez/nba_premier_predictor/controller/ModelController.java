package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.service.ModelMetadataService;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final ModelMetadataService modelMetadataService;

    public ModelController(ModelMetadataService modelMetadataService) {
        this.modelMetadataService = modelMetadataService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return modelMetadataService.metrics();
    }

    @GetMapping("/versions")
    public Map<String, Object> versions() {
        return modelMetadataService.versions();
    }
}
