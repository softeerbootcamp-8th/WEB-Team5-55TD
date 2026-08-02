package com.ootd.pickup.images.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

  @Bean(destroyMethod = "close")
  S3Client imageS3Client(ImageStorageProperties properties) {
    return S3Client.builder()
        .region(Region.of(properties.region()))
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .build();
  }

  @Bean(destroyMethod = "close")
  S3Presigner imageS3Presigner(ImageStorageProperties properties) {
    return S3Presigner.builder().region(Region.of(properties.region())).build();
  }
}
