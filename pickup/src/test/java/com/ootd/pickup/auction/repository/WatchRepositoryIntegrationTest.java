package com.ootd.pickup.auction.repository;

import static org.assertj.core.api.Assertions.*;

import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.repository.auction.AuctionJpaRepository;
import com.ootd.pickup.auction.repository.watch.WatchJpaRepository;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.auction.repository.watch.WatchSummary;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.repository.consignment.ConsignmentJpaRepository;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.repository.MemberJpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WatchRepositoryIntegrationTest {

  @Autowired private MemberJpaRepository memberJpaRepository;

  @Autowired private CardJpaRepository cardJpaRepository;

  @Autowired private ConsignmentJpaRepository consignmentJpaRepository;

  @Autowired private AuctionJpaRepository auctionJpaRepository;

  @Autowired private WatchJpaRepository watchJpaRepository;

  @Autowired private WatchRepository watchRepository;

  @Test
  void 같은_회원과_경매의_관심은_중복저장할_수_없다() {
    // given
    Member member = createMember("watch-duplicate");
    Auction auction = createAuction(member, "중복 관심 카드");
    watchJpaRepository.saveAndFlush(Watch.builder().member(member).auction(auction).build());

    // when & then
    assertThatThrownBy(
            () ->
                watchJpaRepository.saveAndFlush(
                    Watch.builder().member(member).auction(auction).build()))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void 관심해제는_회원과_경매가_모두_일치하는_관심만_삭제한다() {
    // given
    Member firstMember = createMember("watch-owner");
    Member secondMember = createMember("watch-other");
    Auction firstAuction = createAuction(firstMember, "첫 번째 관심 카드");
    Auction secondAuction = createAuction(firstMember, "두 번째 관심 카드");
    watchJpaRepository.save(Watch.builder().member(firstMember).auction(firstAuction).build());
    watchJpaRepository.save(Watch.builder().member(firstMember).auction(secondAuction).build());
    watchJpaRepository.save(Watch.builder().member(secondMember).auction(firstAuction).build());
    watchJpaRepository.flush();

    // when
    int deletedCount =
        watchRepository.deleteByMemberIdAndAuctionId(
            firstMember.getMemberId(), firstAuction.getAuctionId());

    // then
    assertThat(deletedCount).isEqualTo(1);
    assertThat(watchJpaRepository.findAll())
        .extracting(
            watch -> watch.getMember().getMemberId(), watch -> watch.getAuction().getAuctionId())
        .containsExactlyInAnyOrder(
            tuple(firstMember.getMemberId(), secondAuction.getAuctionId()),
            tuple(secondMember.getMemberId(), firstAuction.getAuctionId()));
  }

  @Test
  void 관심요약조회는_경매별_전체수와_조회자_본인의_관심여부를_한번에_반환한다() {
    // given
    Member viewer = createMember("watch-summary-viewer");
    Member other = createMember("watch-summary-other");
    Auction watchedByViewer = createAuction(viewer, "조회자가 관심등록한 카드");
    Auction watchedByOther = createAuction(viewer, "다른 회원만 관심등록한 카드");
    Auction notWatched = createAuction(viewer, "관심없는 카드");
    watchJpaRepository.save(Watch.builder().member(viewer).auction(watchedByViewer).build());
    watchJpaRepository.save(Watch.builder().member(other).auction(watchedByViewer).build());
    watchJpaRepository.save(Watch.builder().member(other).auction(watchedByOther).build());
    watchJpaRepository.flush();

    // when
    Map<Long, WatchSummary> summaries =
        watchRepository.findWatchSummariesByAuctionIds(
            viewer.getMemberId(),
            List.of(
                watchedByViewer.getAuctionId(),
                watchedByOther.getAuctionId(),
                notWatched.getAuctionId()));

    // then
    assertThat(summaries.get(watchedByViewer.getAuctionId())).isEqualTo(new WatchSummary(2L, true));
    assertThat(summaries.get(watchedByOther.getAuctionId())).isEqualTo(new WatchSummary(1L, false));
    assertThat(summaries).doesNotContainKey(notWatched.getAuctionId());
  }

  @Test
  void 관심요약조회는_비로그인_조회자면_본인관심여부가_항상_false다() {
    // given
    Member other = createMember("watch-summary-anonymous-other");
    Auction auction = createAuction(other, "비로그인 관심요약 카드");
    watchJpaRepository.save(Watch.builder().member(other).auction(auction).build());
    watchJpaRepository.flush();

    // when
    Map<Long, WatchSummary> summaries =
        watchRepository.findWatchSummariesByAuctionIds(null, List.of(auction.getAuctionId()));

    // then
    assertThat(summaries.get(auction.getAuctionId())).isEqualTo(new WatchSummary(1L, false));
  }

  @Test
  void 경매기준_관심삭제는_해당_경매의_관심만_모든_회원것을_삭제한다() {
    // given
    Member firstMember = createMember("watch-cleanup-first");
    Member secondMember = createMember("watch-cleanup-second");
    Auction targetAuction = createAuction(firstMember, "삭제 대상 카드");
    Auction otherAuction = createAuction(firstMember, "다른 카드");
    watchJpaRepository.save(Watch.builder().member(firstMember).auction(targetAuction).build());
    watchJpaRepository.save(Watch.builder().member(secondMember).auction(targetAuction).build());
    watchJpaRepository.save(Watch.builder().member(firstMember).auction(otherAuction).build());
    watchJpaRepository.flush();

    // when
    int deletedCount = watchRepository.deleteByAuctionId(targetAuction.getAuctionId());

    // then
    assertThat(deletedCount).isEqualTo(2);
    assertThat(watchJpaRepository.findAll())
        .extracting(watch -> watch.getAuction().getAuctionId())
        .containsExactly(otherAuction.getAuctionId());
  }

  @Test
  void 관심이_없는_경매를_기준으로_삭제해도_예외없이_0건_처리된다() {
    // given
    Member member = createMember("watch-cleanup-empty");
    Auction auction = createAuction(member, "관심없는 카드");

    // when
    int deletedCount = watchRepository.deleteByAuctionId(auction.getAuctionId());

    // then
    assertThat(deletedCount).isZero();
  }

  @Test
  void 회원의_관심목록조회는_예정_또는_진행중_상태_경매만_최신순으로_반환한다() {
    // given
    Member member = createMember("watch-list-owner");
    Auction scheduledAuction = createAuction(member, "예정 카드", AuctionStatus.SCHEDULED);
    Auction ongoingAuction = createAuction(member, "진행중 카드", AuctionStatus.ONGOING);
    Auction wonAuction = createAuction(member, "낙찰 카드", AuctionStatus.WON);
    Watch firstWatch =
        watchJpaRepository.save(Watch.builder().member(member).auction(scheduledAuction).build());
    Watch secondWatch =
        watchJpaRepository.save(Watch.builder().member(member).auction(ongoingAuction).build());
    watchJpaRepository.save(Watch.builder().member(member).auction(wonAuction).build());
    watchJpaRepository.flush();

    // when
    List<Watch> result = watchRepository.findAllActiveByMemberId(member.getMemberId(), null, 10);

    // then
    assertThat(result)
        .extracting(Watch::getWatchId)
        .containsExactly(secondWatch.getWatchId(), firstWatch.getWatchId());
  }

  @Test
  void 관심목록조회는_커서_이후의_항목만_watchId_역순으로_반환한다() {
    // given
    Member member = createMember("watch-list-cursor");
    Auction auctionA = createAuction(member, "커서 카드1", AuctionStatus.SCHEDULED);
    Auction auctionB = createAuction(member, "커서 카드2", AuctionStatus.SCHEDULED);
    Auction auctionC = createAuction(member, "커서 카드3", AuctionStatus.SCHEDULED);
    Watch watchA =
        watchJpaRepository.save(Watch.builder().member(member).auction(auctionA).build());
    Watch watchB =
        watchJpaRepository.save(Watch.builder().member(member).auction(auctionB).build());
    Watch watchC =
        watchJpaRepository.save(Watch.builder().member(member).auction(auctionC).build());
    watchJpaRepository.flush();

    // when
    List<Watch> result =
        watchRepository.findAllActiveByMemberId(member.getMemberId(), watchC.getWatchId(), 10);

    // then
    assertThat(result)
        .extracting(Watch::getWatchId)
        .containsExactly(watchB.getWatchId(), watchA.getWatchId());
  }

  private Member createMember(String loginId) {
    return memberJpaRepository.save(Member.create(loginId, "password", loginId + "-nickname"));
  }

  private Auction createAuction(Member sellerMember, String cardName) {
    return createAuction(sellerMember, cardName, AuctionStatus.SCHEDULED);
  }

  private Auction createAuction(Member sellerMember, String cardName, AuctionStatus auctionStatus) {
    Card card =
        cardJpaRepository.save(
            Card.builder()
                .cardName(cardName)
                .cardNumber(cardName)
                .setName("관심 테스트 세트")
                .language(Language.KOREAN)
                .rarity(Rarity.MINT)
                .imageUrl("https://example.com/watch.png")
                .build());
    Consignment consignment =
        consignmentJpaRepository.save(
            Consignment.builder()
                .card(card)
                .sellerMember(sellerMember)
                .status(ConsignmentStatus.IN_AUCTION)
                .build());
    return auctionJpaRepository.save(
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().plusDays(1))
            .auctionStatus(auctionStatus)
            .startingPrice(10000L)
            .reservePrice(15000L)
            .bidIncrement(500L)
            .build());
  }
}
