package com.armandorodriguez.nba_premier_predictor.aws;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")
class AwsClientConfig {

    @Bean
    S3Client s3Client(AwsInfrastructureProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.normalizedRegion()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());
        String endpoint = properties.normalizedEndpoint();
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    SqsClient sqsClient(AwsInfrastructureProperties properties) {
        var builder = SqsClient.builder()
                .region(Region.of(properties.normalizedRegion()))
                .credentialsProvider(credentialsProvider(properties));
        String endpoint = properties.normalizedEndpoint();
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private static StaticCredentialsProvider credentialsProvider(AwsInfrastructureProperties properties) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.normalizedAccessKeyId(),
                properties.normalizedSecretAccessKey()));
    }
}
