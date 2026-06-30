package com.armandorodriguez.nba_premier_predictor.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Service;

@Service
class HttpContextRefreshSourceClient implements ContextRefreshSourceClient {

    private static final String TRUSTED_NEWS_PREFIX = "trusted-news:";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final TrustedNewsContextAdapter trustedNewsContextAdapter;

    HttpContextRefreshSourceClient(TrustedNewsContextAdapter trustedNewsContextAdapter) {
        this.trustedNewsContextAdapter = trustedNewsContextAdapter;
    }

    @Override
    public String fetch(String sourceUrl) {
        boolean trustedNews = sourceUrl.startsWith(TRUSTED_NEWS_PREFIX);
        String fetchUrl = trustedNews ? sourceUrl.substring(TRUSTED_NEWS_PREFIX.length()) : sourceUrl;
        HttpRequest request = HttpRequest.newBuilder(URI.create(fetchUrl))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Context source returned HTTP " + response.statusCode());
            }
            return trustedNews ? trustedNewsContextAdapter.toContextJson(fetchUrl, response.body()) : response.body();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read context source", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Context source request was interrupted", ex);
        }
    }
}
