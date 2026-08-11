package com.ootd.pickup.auth.kakao;

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

  @SuppressWarnings("unchecked")
  public KakaoUser authenticate(KakaoLoginRequest request) {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", properties.clientId());
    form.add("client_secret", properties.clientSecret());
    form.add("redirect_uri", request.redirectUri());
    form.add("code", request.code());
    Map<String, Object> token =
        restClient
            .post()
            .uri("https://kauth.kakao.com/oauth/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    String accessToken = (String) token.get("access_token");
    Map<String, Object> user =
        restClient
            .get()
            .uri("https://kapi.kakao.com/v2/user/me")
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);
    String subject = String.valueOf(user.get("id"));
    Map<String, Object> propertiesMap =
        (Map<String, Object>) user.getOrDefault("properties", Map.of());
    return new KakaoUser(subject, (String) propertiesMap.get("profile_image"));
  }

  public record KakaoUser(String subject, String profileImageUrl) {}
}
