package com.ootd.pickup.images.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

  private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(10);

  @Bean(destroyMethod = "close")
  S3Client imageS3Client(ImageStorageProperties properties) {
    return S3Client.builder()
        .region(Region.of(properties.region()))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                .apiCallTimeout(API_CALL_TIMEOUT)
                .build())
        .build();
  }

  @Bean(destroyMethod = "close")
  S3Presigner imageS3Presigner(ImageStorageProperties properties) {
    return S3Presigner.builder().region(Region.of(properties.region())).build();
  }
}
