package com.armandorodriguez.nba_premier_predictor.aws;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.aws")
public record AwsInfrastructureProperties(
        boolean enabled,
        String endpoint,
        String region,
        String accessKeyId,
        String secretAccessKey,
        String predictionReportsBucket,
        String asyncJobsQueueUrl) {

    String normalizedEndpoint() {
        return blankToNull(endpoint);
    }

    String normalizedRegion() {
        return defaultIfBlank(region, "us-east-1");
    }

    String normalizedAccessKeyId() {
        return defaultIfBlank(accessKeyId, "test");
    }

    String normalizedSecretAccessKey() {
        return defaultIfBlank(secretAccessKey, "test");
    }

    String normalizedPredictionReportsBucket() {
        return defaultIfBlank(predictionReportsBucket, "nba-premier-prediction-reports");
    }

    String normalizedAsyncJobsQueueUrl() {
        return defaultIfBlank(asyncJobsQueueUrl, "http://localhost:4566/000000000000/nba-premier-async-jobs");
    }

    private static String defaultIfBlank(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
