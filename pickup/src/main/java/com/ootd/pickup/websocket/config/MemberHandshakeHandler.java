package com.ootd.pickup.websocket.config;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * {@link WebSocketAuthHandshakeInterceptor}가 남긴 {@code memberId} attribute를 STOMP 세션의 {@link
 * Principal}로 승격한다. 로그인하지 않은 연결은 {@code null}을 반환한다 — Spring은 이 경우 해당 세션에 대한 사용자 목적지({@code
 * /user/**}) 라우팅을 지원하지 않을 뿐, 연결 자체나 공개 토픽 구독은 그대로 동작한다.
 */
@Component
public class MemberHandshakeHandler extends DefaultHandshakeHandler {

  @Override
  protected Principal determineUser(
      ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
    Object memberId = attributes.get(WebSocketAuthHandshakeInterceptor.MEMBER_ID_ATTRIBUTE);
    return memberId == null ? null : new MemberPrincipal(memberId.toString());
  }
}
