package com.armandorodriguez.nba_premier_predictor.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.armandorodriguez.nba_premier_predictor.dto.ModelRetrainRequest;
import com.armandorodriguez.nba_premier_predictor.service.AsyncJobService;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final AsyncJobService asyncJobService;

    public JobController(AsyncJobService asyncJobService) {
        this.asyncJobService = asyncJobService;
    }

    @PostMapping("/model-evaluation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> publishModelEvaluation() {
        return response("model_evaluation", asyncJobService.publishModelEvaluation());
    }

    @PostMapping("/model-retraining")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> publishModelRetraining(@RequestBody(required = false) ModelRetrainRequest request) {
        return response("model_retraining", asyncJobService.publishModelRetraining(request));
    }

    @PostMapping("/prediction-error-refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> publishPredictionErrorRefresh() {
        return response("prediction_error_refresh", asyncJobService.publishPredictionErrorRefresh());
    }

    @PostMapping("/context-refresh")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> publishContextRefresh(
            @RequestParam(defaultValue = "manual_context_refresh") String triggeredBy) {
        return response("context_refresh", asyncJobService.publishContextRefresh(triggeredBy));
    }

    private static Map<String, Object> response(String jobType, boolean published) {
        return Map.of(
                "jobType", jobType,
                "published", published);
    }
}
