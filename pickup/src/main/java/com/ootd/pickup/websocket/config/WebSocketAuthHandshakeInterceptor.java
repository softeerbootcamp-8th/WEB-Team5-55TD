package com.ootd.pickup.websocket.config;

import com.ootd.pickup.auth.token.AccessTokenVerifier;
import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 핸드셰이크(업그레이드 전 평범한 HTTP 요청)에서 REST와 같은 {@code access-token} 쿠키를 읽어 {@code memberId}를
 * 핸드셰이크 attribute로 남긴다. {@link MemberHandshakeHandler}가 이 값을 읽어 STOMP 세션의 {@link
 * java.security.Principal}을 만든다.
 *
 * <p>쿠키가 없거나 검증에 실패해도 핸드셰이크 자체를 막지 않는다 — 비로그인 사용자도 경매 토픽(브로드캐스트) 구독은 할 수 있어야 하고, 입찰 요청을 만들 수 없는
 * 사용자에게는 {@code /user/queue/bid-requests} 유니캐스트가 어차피 필요 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

  static final String MEMBER_ID_ATTRIBUTE = "memberId";

  private final AccessTokenVerifier accessTokenVerifier;

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    if (!(request instanceof ServletServerHttpRequest servletRequest)) {
      return true;
    }

    getAccessToken(servletRequest.getServletRequest())
        .ifPresent(
            token -> {
              try {
                Authentication authentication = accessTokenVerifier.verify(token);
                attributes.put(MEMBER_ID_ATTRIBUTE, authentication.memberId());
              } catch (RuntimeException exception) {
                log.info("WebSocket 핸드셰이크의 access-token 검증에 실패해 익명으로 연결합니다.");
              }
            });
    return true;
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  private Optional<String> getAccessToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    for (Cookie cookie : cookies) {
      if (AuthenticationAttributes.COOKIE_NAME.equals(cookie.getName())) {
        return Optional.of(cookie.getValue());
      }
    }
    return Optional.empty();
  }
}
