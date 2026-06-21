package com.armandorodriguez.nba_premier_predictor.ingestion;

import java.nio.file.Path;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class HistoricalDataImportRunner implements ApplicationRunner {

    private final HistoricalDataImportProperties properties;
    private final HistoricalDataImportService importService;

    HistoricalDataImportRunner(HistoricalDataImportProperties properties, HistoricalDataImportService importService) {
        this.properties = properties;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!properties.enabled()) {
            return;
        }
        if (properties.path() == null || properties.path().isBlank()) {
            throw new IllegalArgumentException("NBA_IMPORT_PATH must point to archive.zip or an extracted data directory");
        }
        importService.importFrom(Path.of(properties.path()));
    }
}
