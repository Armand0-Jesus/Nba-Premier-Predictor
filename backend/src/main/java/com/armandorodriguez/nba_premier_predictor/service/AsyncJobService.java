package com.armandorodriguez.nba_premier_predictor.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.armandorodriguez.nba_premier_predictor.aws.SqsJobPublisher;
import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;

@Service
public class AsyncJobService {

    private final SqsJobPublisher sqsJobPublisher;

    public AsyncJobService(SqsJobPublisher sqsJobPublisher) {
        this.sqsJobPublisher = sqsJobPublisher;
    }

    public boolean publishModelEvaluation() {
        return sqsJobPublisher.publish("model_evaluation", Map.of("source", "spring_api"));
    }

    public boolean publishModelRetraining(ModelRetrainRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "spring_api");
        payload.put("startSeason", request == null ? null : request.startSeason());
        payload.put("endSeason", request == null ? null : request.endSeason());
        payload.put("limit", request == null ? null : request.limit());
        payload.put("trainRatio", request == null ? null : request.trainRatio());
        payload.put("recencyHalflifeDays", request == null ? null : request.recencyHalflifeDays());
        payload.put("triggeredBy", request == null ? null : request.normalizedTriggeredBy());
        return sqsJobPublisher.publish("model_retraining", payload);
    }

    public boolean publishPredictionErrorRefresh() {
        return sqsJobPublisher.publish("prediction_error_refresh", Map.of("source", "spring_api"));
    }

    public boolean publishContextRefresh(String triggeredBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "spring_api");
        payload.put("triggeredBy", triggeredBy);
        return sqsJobPublisher.publish("context_refresh", payload);
    }
}
