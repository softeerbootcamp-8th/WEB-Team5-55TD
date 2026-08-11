package com.ootd.pickup.websocket.config;

import java.security.Principal;

/**
 * WebSocket STOMP 세션에 붙는 인증 주체. {@link #getName()}이 {@code memberId} 문자열이라 {@code
 * SimpMessagingTemplate.convertAndSendToUser(memberId, ...)}가 이 세션을 찾아낼 수 있다.
 */
public record MemberPrincipal(String name) implements Principal {

  @Override
  public String getName() {
    return name;
  }
}
