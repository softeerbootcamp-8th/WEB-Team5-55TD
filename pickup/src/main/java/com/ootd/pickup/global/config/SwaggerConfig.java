package com.ootd.pickup.global.config;

import com.ootd.pickup.global.auth.AuthenticationAttributes;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  public static final String ACCESS_TOKEN_SECURITY_SCHEME = "access-token-cookie";

  @Bean
  public OpenAPI openAPI() {
    return new OpenAPI()
        .info(new Info().title("PickUp API").version("v1").description("PickUp API 문서"))
        .servers(List.of(new Server().url("/")))
        .components(
            new Components()
                .addSecuritySchemes(
                    ACCESS_TOKEN_SECURITY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(AuthenticationAttributes.COOKIE_NAME)
                        .description("로그인 시 발급되는 access-token 쿠키")));
  }
}
