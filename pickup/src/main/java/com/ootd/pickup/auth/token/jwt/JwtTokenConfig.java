package com.ootd.pickup.auth.token.jwt;

import com.ootd.pickup.auth.token.AccessTokenGenerator;
import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.auth.token.RefreshTokenGenerator;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
@EnableConfigurationProperties(JwtTokenProperties.class)
public class JwtTokenConfig {

    @Bean
    SecretKey jwtSigningKey(JwtTokenProperties properties) {
        byte[] secretBytes = Decoders.BASE64.decode(properties.secret());
        return Keys.hmacShaKeyFor(secretBytes);
    }

    @Bean
    AccessTokenGenerator accessTokenGenerator(JwtTokenProperties properties, SecretKey jwtSigningKey) {
        return new JwtAccessTokenGenerator(properties.issuer(), jwtSigningKey, properties.accessTokenTtl());
    }

    @Bean
    AccessTokenVerifier accessTokenVerifier(JwtTokenProperties properties, SecretKey jwtSigningKey) {
        return new JwtAccessTokenVerifier(properties.issuer(), jwtSigningKey);
    }

    @Bean
    RefreshTokenGenerator refreshTokenGenerator() {
        return new RefreshTokenGenerator();
    }
}
