package com.ootd.pickup.point.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.ootd.pickup.member.event.MemberRegisteredMessageQueueEvent;
import com.ootd.pickup.point.service.PointService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PointInitializationEventHandlerTest {

  @Mock private PointService pointService;

  private PointInitializationEventHandler pointInitializationEventHandler;

  @BeforeEach
  void setUp() {
    pointInitializationEventHandler = new PointInitializationEventHandler(pointService);
  }

  @Test
  void eventClass를_호출하면_MemberRegisteredMessageQueueEvent를_반환한다() {
    // when & then
    assertThat(pointInitializationEventHandler.eventClass())
        .isEqualTo(MemberRegisteredMessageQueueEvent.class);
  }

  @Test
  void 이벤트를_받으면_회원_id로_포인트_계좌_생성을_위임한다() {
    // given
    MemberRegisteredMessageQueueEvent event = createEvent(1L);

    // when
    pointInitializationEventHandler.handle(event);

    // then
    then(pointService).should().initializePoint(1L);
  }

  @Test
  void 다른_인스턴스가_동시에_생성해_유니크_제약에_막혀도_예외를_다시_던지지_않는다() {
    // given: 다른 인스턴스가 먼저 커밋해 이 인스턴스의 생성 트랜잭션은 유니크 제약에 막혀 롤백된 상황을 흉내낸다
    MemberRegisteredMessageQueueEvent event = createEvent(1L);
    willThrow(new DataIntegrityViolationException("uk_member_point_member_id"))
        .given(pointService)
        .initializePoint(1L);

    // when & then: 메시지를 정상 소비 처리할 수 있도록 예외가 밖으로 새어 나가지 않아야 한다
    assertThatCode(() -> pointInitializationEventHandler.handle(event)).doesNotThrowAnyException();
  }

  private MemberRegisteredMessageQueueEvent createEvent(Long memberId) {
    return new MemberRegisteredMessageQueueEvent("event-id", memberId, LocalDateTime.now());
  }
}
