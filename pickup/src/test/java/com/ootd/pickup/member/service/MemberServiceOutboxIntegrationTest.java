package com.ootd.pickup.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.global.event.AggregateType;
import com.ootd.pickup.global.event.EventType;
import com.ootd.pickup.global.event.outbox.OutboxEventEntity;
import com.ootd.pickup.global.event.outbox.OutboxEventJpaRepository;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.event.MemberRegisteredMessageQueueEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberServiceOutboxIntegrationTest {

  @Autowired private MemberService memberService;

  @Autowired private OutboxEventJpaRepository outboxEventJpaRepository;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void 회원가입하면_포인트_계좌_생성용_이벤트가_Outbox에_적재된다() {
    // given
    String unique = "outbox-member-" + System.nanoTime();
    MemberRequest request = new MemberRequest(unique, unique + "-nickname", "password1234");

    // when
    MemberResponse response = memberService.createMember(request);

    // then
    OutboxEventEntity appended = singleAppendedRow(response.memberId());
    assertThat(appended.getEventType()).isEqualTo(EventType.MEMBER_REGISTERED);
    assertThat(appended.getAggregateType()).isEqualTo(AggregateType.MEMBER);
    assertThat(appended.isPublished()).isFalse();
    assertThat(appended.getId()).hasSize(36);

    MemberRegisteredMessageQueueEvent event =
        objectMapper.readValue(appended.getPayload(), MemberRegisteredMessageQueueEvent.class);
    assertThat(event.memberId()).isEqualTo(response.memberId());
    assertThat(appended.getId()).isEqualTo(event.eventId());
  }

  private OutboxEventEntity singleAppendedRow(Long memberId) {
    List<OutboxEventEntity> appended =
        outboxEventJpaRepository.findAll().stream()
            .filter(row -> row.getAggregateType() == AggregateType.MEMBER)
            .filter(row -> row.getAggregateId().equals(memberId))
            .toList();
    assertThat(appended).hasSize(1);
    return appended.getFirst();
  }
}
