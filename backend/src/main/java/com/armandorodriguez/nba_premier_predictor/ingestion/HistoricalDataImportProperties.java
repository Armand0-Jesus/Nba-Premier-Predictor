package com.armandorodriguez.nba_premier_predictor.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.import")
public record HistoricalDataImportProperties(boolean enabled, String path) {
}
