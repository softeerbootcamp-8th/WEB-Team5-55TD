package com.ootd.pickup.point.handler;

import com.ootd.pickup.global.event.EventHandler;
import com.ootd.pickup.member.event.MemberRegisteredMessageQueueEvent;
import com.ootd.pickup.point.service.PointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link MemberRegisteredMessageQueueEvent} 수신 어댑터.
 *
 * <p>인스턴스가 여러 대면 같은 회원의 가입 이벤트가 서로 다른 인스턴스에서 동시에 처리될 수 있다. 이때 뒤늦은 쪽은 {@code member_point.member_id}
 * 유니크 제약에 걸려 {@link DataIntegrityViolationException}과 함께 트랜잭션이 롤백된다. 이는 실패가 아니라 다른 인스턴스가 이미 같은 계좌를
 * 만들었다는 신호이므로, 트랜잭션이 이미 안전하게 롤백된 이 시점(트랜잭션 경계 밖)에서 잡아 정상 소비로 처리한다. 여기서 잡지 않으면 {@code
 * SQSEventConsumer}가 이를 알 수 없는 실패로 보고 error 로그(Slack 알림)를 남기고 메시지 그룹을 막아, 자기 치유되는 경합인데도 소음과 지연을
 * 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointInitializationEventHandler
    implements EventHandler<MemberRegisteredMessageQueueEvent> {

  private final PointService pointService;

  @Override
  public Class<MemberRegisteredMessageQueueEvent> eventClass() {
    return MemberRegisteredMessageQueueEvent.class;
  }

  @Override
  public void handle(MemberRegisteredMessageQueueEvent event) {
    try {
      pointService.initializePoint(event.memberId());
    } catch (DataIntegrityViolationException exception) {
      log.info(
          "다른 인스턴스가 동시에 생성한 포인트 계좌라 건너뜀 - memberId={}, eventId={}",
          event.memberId(),
          event.eventId());
    }
  }
}
