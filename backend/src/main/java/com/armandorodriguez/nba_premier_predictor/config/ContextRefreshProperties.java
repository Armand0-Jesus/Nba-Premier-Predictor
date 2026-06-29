package com.armandorodriguez.nba_premier_predictor.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.context-refresh")
public class ContextRefreshProperties {

    private boolean enabled;
    private String cron = "0 30 * * * *";
    private List<String> sourceUrls = new ArrayList<>();
    private Integer projectionSeason;
    private boolean refreshProjections = true;
    private boolean retrainAfterIngestion;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        if (cron != null && !cron.isBlank()) {
            this.cron = cron;
        }
    }

    public List<String> getSourceUrls() {
        return sourceUrls;
    }

    public void setSourceUrls(List<String> sourceUrls) {
        this.sourceUrls = sourceUrls == null ? new ArrayList<>() : new ArrayList<>(sourceUrls);
    }

    public Integer getProjectionSeason() {
        return projectionSeason;
    }

    public void setProjectionSeason(Integer projectionSeason) {
        this.projectionSeason = projectionSeason;
    }

    public boolean isRefreshProjections() {
        return refreshProjections;
    }

    public void setRefreshProjections(boolean refreshProjections) {
        this.refreshProjections = refreshProjections;
    }

    public boolean isRetrainAfterIngestion() {
        return retrainAfterIngestion;
    }

    public void setRetrainAfterIngestion(boolean retrainAfterIngestion) {
        this.retrainAfterIngestion = retrainAfterIngestion;
    }

    public List<String> normalizedSourceUrls() {
        return sourceUrls.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
