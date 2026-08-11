package com.ootd.pickup.member.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberRegisteredMessageQueueEventTest {

  @Test
  void 회원으로부터_생성하면_회원_id가_옮겨진다() {
    // given
    Member member = createMember(1L);

    // when
    MemberRegisteredMessageQueueEvent event = MemberRegisteredMessageQueueEvent.fromEntity(member);

    // then
    assertThat(event.memberId()).isEqualTo(1L);
    assertThat(event.eventId()).isNotBlank();
    assertThat(event.occurredAt()).isNotNull();
  }

  @Test
  void 회원_애그리거트와_이벤트타입이_고정값으로_반환된다() {
    // given
    Member member = createMember(1L);

    // when
    MemberRegisteredMessageQueueEvent event = MemberRegisteredMessageQueueEvent.fromEntity(member);

    // then
    assertThat(event.aggregateType()).isEqualTo(AggregateType.MEMBER);
    assertThat(event.aggregateId()).isEqualTo(member.getMemberId());
    assertThat(event.eventType()).isEqualTo(EventType.MEMBER_REGISTERED);
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("loginId" + memberId, "password", "닉네임" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }
}
