package com.armandorodriguez.nba_premier_predictor.dto;

public record ModelRetrainRequest(
        Integer startSeason,
        Integer endSeason,
        Integer limit,
        Double trainRatio,
        Double recencyHalflifeDays,
        String triggeredBy) {

    public int normalizedLimit() {
        return limit == null ? 100000 : limit;
    }

    public double normalizedTrainRatio() {
        return trainRatio == null ? 0.8 : trainRatio;
    }

    public String normalizedTriggeredBy() {
        return triggeredBy == null || triggeredBy.isBlank() ? "manual" : triggeredBy;
    }
}
