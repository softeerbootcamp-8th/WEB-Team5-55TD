package com.ootd.pickup.auth.token.jwt;

import com.ootd.pickup.auth.token.AdminAccessTokenGenerator;
import com.ootd.pickup.auth.token.AdminAccessTokenVerifier;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminJwtTokenProperties.class)
public class AdminJwtTokenConfig {

  @Bean
  AdminAccessTokenGenerator adminAccessTokenGenerator(
      JwtTokenProperties properties,
      AdminJwtTokenProperties adminProperties,
      SecretKey jwtSigningKey) {
    return new JwtAdminAccessTokenGenerator(
        properties.issuer(), jwtSigningKey, adminProperties.accessTokenTtl());
  }

  @Bean
  AdminAccessTokenVerifier adminAccessTokenVerifier(
      JwtTokenProperties properties, SecretKey jwtSigningKey) {
    return new JwtAdminAccessTokenVerifier(properties.issuer(), jwtSigningKey);
  }
}
