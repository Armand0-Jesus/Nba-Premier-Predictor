package com.armandorodriguez.nba_premier_predictor.dto;

public record FeatureGenerationResponse(
        String featureType,
        Integer seasonStartYear,
        int snapshotsGenerated) {
}
