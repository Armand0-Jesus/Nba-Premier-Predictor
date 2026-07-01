package com.armandorodriguez.nba_premier_predictor.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class AwsInfrastructureTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void awsClientsAreCreatedOnlyWhenEnabled() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withBean(AwsInfrastructureProperties.class, AwsInfrastructureTests::enabledProperties)
                .withUserConfiguration(AwsClientConfig.class);

        contextRunner
                .withPropertyValues("app.aws.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(SqsClient.class);
                });

        contextRunner
                .withPropertyValues("app.aws.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(S3Client.class);
                    assertThat(context).doesNotHaveBean(SqsClient.class);
                });
    }

    @Test
    void s3StorageWritesPredictionReportJson() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        S3StorageService storageService = new S3StorageService(enabledProperties(), provider(S3Client.class, s3Client), objectMapper);

        boolean exported = storageService.putJson("predictions/player_stat/1.json", java.util.Map.of("predictionId", 1));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(exported).isTrue();
        assertThat(request.getValue().bucket()).isEqualTo("nba-premier-prediction-reports");
        assertThat(request.getValue().key()).isEqualTo("predictions/player_stat/1.json");
        assertThat(request.getValue().contentType()).isEqualTo("application/json");
    }

    @Test
    void s3StorageIsNoopWhenAwsIsDisabled() {
        S3Client s3Client = mock(S3Client.class);
        S3StorageService storageService = new S3StorageService(disabledProperties(), provider(S3Client.class, s3Client), objectMapper);

        boolean exported = storageService.putJson("predictions/player_stat/1.json", java.util.Map.of("predictionId", 1));

        assertThat(exported).isFalse();
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void s3StorageFailureDoesNotThrow() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new IllegalStateException("localstack unavailable"));
        S3StorageService storageService = new S3StorageService(enabledProperties(), provider(S3Client.class, s3Client), objectMapper);

        assertThat(storageService.putJson("predictions/player_stat/1.json", java.util.Map.of("predictionId", 1))).isFalse();
    }

    @Test
    void sqsPublisherSendsJobMessage() {
        SqsClient sqsClient = mock(SqsClient.class);
        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("job-1").build());
        SqsJobPublisher publisher = new SqsJobPublisher(enabledProperties(), provider(SqsClient.class, sqsClient), objectMapper);

        boolean published = publisher.publish("model_retraining", java.util.Map.of("source", "test"));

        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(request.capture());
        assertThat(published).isTrue();
        assertThat(request.getValue().queueUrl()).isEqualTo("http://localhost:4566/000000000000/nba-premier-async-jobs");
        assertThat(request.getValue().messageBody()).contains("\"jobType\":\"model_retraining\"");
    }

    @Test
    void sqsPublisherIsNoopWhenAwsIsDisabled() {
        SqsClient sqsClient = mock(SqsClient.class);
        SqsJobPublisher publisher = new SqsJobPublisher(disabledProperties(), provider(SqsClient.class, sqsClient), objectMapper);

        boolean published = publisher.publish("model_retraining", java.util.Map.of());

        assertThat(published).isFalse();
        verify(sqsClient, never()).sendMessage(any(SendMessageRequest.class));
    }

    private static AwsInfrastructureProperties enabledProperties() {
        return new AwsInfrastructureProperties(
                true,
                "http://localhost:4566",
                "us-east-1",
                "test",
                "test",
                "nba-premier-prediction-reports",
                "http://localhost:4566/000000000000/nba-premier-async-jobs");
    }

    private static AwsInfrastructureProperties disabledProperties() {
        return new AwsInfrastructureProperties(
                false,
                "http://localhost:4566",
                "us-east-1",
                "test",
                "test",
                "nba-premier-prediction-reports",
                "http://localhost:4566/000000000000/nba-premier-async-jobs");
    }

    private static <T> org.springframework.beans.factory.ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean("bean", bean);
        return factory.getBeanProvider(type);
    }
}
