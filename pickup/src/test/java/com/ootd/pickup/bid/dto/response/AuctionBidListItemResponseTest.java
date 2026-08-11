package com.ootd.pickup.bid.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionBidListItemResponseTest {

  @Test
  void 입찰자_닉네임을_마스킹해서_담는다() {
    // given
    Bid bid = createBid("김민주임다");

    // when
    AuctionBidListItemResponse response = AuctionBidListItemResponse.of(bid, null);

    // then
    assertThat(response.nicknameMasked()).isEqualTo("김***다");
  }

  @Test
  void 조회한_회원과_입찰자가_같으면_isMine이_true다() {
    // given
    Bid bid = createBid("피카");

    // when
    AuctionBidListItemResponse response = AuctionBidListItemResponse.of(bid, 1L);

    // then
    assertThat(response.isMine()).isTrue();
  }

  private Bid createBid(String nickname) {
    Member member = Member.create("loginId", "password", nickname);
    ReflectionTestUtils.setField(member, "memberId", 1L);
    Bid bid = Bid.create(null, member, 10_000L);
    ReflectionTestUtils.setField(bid, "bidId", 1L);
    return bid;
  }
}
