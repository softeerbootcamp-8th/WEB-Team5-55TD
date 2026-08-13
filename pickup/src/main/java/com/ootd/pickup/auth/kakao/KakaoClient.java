package com.ootd.pickup.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

public class KakaoClient {
  private final RestClient restClient;
  private final KakaoProperties properties;

  KakaoClient(RestClient restClient, KakaoProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public KakaoUser authenticate(KakaoLoginRequest request) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", properties.clientId());
    form.add("client_secret", properties.clientSecret());
    form.add("redirect_uri", request.redirectUri());
    form.add("code", request.code());
    KakaoTokenResponse token =
        restClient
            .post()
            .uri("https://kauth.kakao.com/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(KakaoTokenResponse.class);
    if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
      throw new KakaoAuthenticationException("Kakao access token is missing");
    }
    Map<String, Object> user =
        restClient
            .get()
            .uri("https://kapi.kakao.com/v2/user/me")
            .header("Authorization", "Bearer " + token.accessToken())
            .retrieve()
            .body(Map.class);
    if (user == null || user.get("id") == null) {
      throw new KakaoAuthenticationException("Kakao user identity is missing");
    }
    String subject = String.valueOf(user.get("id"));
    Object properties = user.get("properties");
    String profileImageUrl = null;
    if (properties instanceof Map<?, ?> propertiesMap) {
      Object profileImage = propertiesMap.get("profile_image");
      if (profileImage instanceof String value) {
        profileImageUrl = value;
      }
    }
    return new KakaoUser(subject, profileImageUrl);
  }

  public record KakaoTokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType) {}

  public static class KakaoAuthenticationException extends RuntimeException {
    public KakaoAuthenticationException(String message) {
      super(message);
    }
  }

  public record KakaoUser(String subject, String profileImageUrl) {}
}
