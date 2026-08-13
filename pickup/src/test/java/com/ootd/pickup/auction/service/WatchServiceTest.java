package com.ootd.pickup.auction.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.dto.response.WatchResponse;
import com.ootd.pickup.auction.repository.auction.AuctionRepository;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.global.exception.ExceptionCode;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.service.MemberManageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WatchServiceTest {

  @Mock private MemberManageService memberManageService;

  @Mock private AuctionRepository auctionRepository;

  @Mock private WatchRepository watchRepository;

  private WatchService watchService;

  @BeforeEach
  void setUp() {
    watchService = new WatchService(memberManageService, auctionRepository, watchRepository);
  }

  @Test
  void 존재하는_경매에_관심을_등록하면_관심정보를_반환한다() {
    // given
    Member member = createMember(1L);
    Auction auction = createAuction(100L);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(auctionRepository.findById(100L)).willReturn(Optional.of(auction));
    given(watchRepository.save(any(Watch.class)))
        .willAnswer(
            invocation -> {
              Watch watch = invocation.getArgument(0);
              ReflectionTestUtils.setField(watch, "watchId", 10L);
              return watch;
            });

    // when
    WatchResponse response = watchService.registerWatch(1L, 100L);

    // then
    assertThat(response.watchId()).isEqualTo(10L);
    assertThat(response.memberId()).isEqualTo(1L);
    assertThat(response.auctionId()).isEqualTo(100L);
    assertThat(response.createdAt()).isNotNull();
    then(watchRepository).should().flush();
    then(auctionRepository).should().incrementWatchCountById(100L);
  }

  @Test
  void 존재하지_않는_회원이_관심을_등록하면_관심을_저장하지_않는다() {
    // given
    given(memberManageService.getMemberById(999L))
        .willThrow(new PickUpException(ExceptionCode.MEMBER_NOT_FOUND));

    // when & then
    assertThatThrownBy(() -> watchService.registerWatch(999L, 100L))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.MEMBER_NOT_FOUND.getMessage());
    then(auctionRepository).shouldHaveNoInteractions();
    then(watchRepository).shouldHaveNoInteractions();
  }

  @Test
  void 존재하지_않는_경매에_관심을_등록하면_관심을_저장하지_않는다() {
    // given
    given(memberManageService.getMemberById(1L)).willReturn(createMember(1L));
    given(auctionRepository.findById(999L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> watchService.registerWatch(1L, 999L))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.AUCTION_NOT_FOUND.getMessage());
    then(watchRepository).shouldHaveNoInteractions();
  }

  @Test
  void 이미_관심등록한_경매를_다시_등록하면_중복예외가_발생한다() {
    // given
    given(memberManageService.getMemberById(1L)).willReturn(createMember(1L));
    given(auctionRepository.findById(100L)).willReturn(Optional.of(createAuction(100L)));
    given(watchRepository.save(any(Watch.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    willThrow(new DataIntegrityViolationException("duplicate watch"))
        .given(watchRepository)
        .flush();

    // when & then
    assertThatThrownBy(() -> watchService.registerWatch(1L, 100L))
        .isInstanceOf(PickUpException.class)
        .hasMessage(ExceptionCode.WATCH_ALREADY_EXISTS.getMessage());
  }

  @Test
  void 관심을_해제하면_회원과_경매가_일치하는_관심을_삭제한다() {
    // given
    given(watchRepository.deleteByMemberIdAndAuctionId(1L, 100L)).willReturn(1);

    // when
    watchService.deleteWatch(1L, 100L);

    // then
    then(watchRepository).should().deleteByMemberIdAndAuctionId(1L, 100L);
    then(auctionRepository).should().decrementWatchCountById(100L);
  }

  @Test
  void 등록된_관심이_없어도_관심해제는_정상완료된다() {
    // given
    given(watchRepository.deleteByMemberIdAndAuctionId(1L, 100L)).willReturn(0);

    // when
    watchService.deleteWatch(1L, 100L);

    // then
    then(watchRepository).should().deleteByMemberIdAndAuctionId(1L, 100L);
    then(auctionRepository).should(never()).decrementWatchCountById(anyLong());
  }

  @Test
  void 경매종료로_관심을_정리하면_해당_경매의_관심을_삭제한다() {
    // when
    watchService.deleteWatchesByAuctionId(100L);

    // then
    then(watchRepository).should().deleteByAuctionId(100L);
    then(auctionRepository).should().resetWatchCountById(100L);
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("loginId", "password", "닉네임");
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Auction createAuction(Long auctionId) {
    Auction auction = Auction.builder().title("테스트 제목").description("테스트 설명").build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }
}
