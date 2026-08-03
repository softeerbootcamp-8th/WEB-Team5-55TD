package com.ootd.pickup.member.service;

import static com.ootd.pickup.global.exception.ExceptionCode.ILLEGAL_ARGUMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CURSOR;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NOT_FOUND;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.AuctionStatus;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.dto.request.GetMyBidsRequest;
import com.ootd.pickup.bid.dto.request.GetMyWinsRequest;
import com.ootd.pickup.bid.dto.response.MyBidListItemResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.dto.ProfileImageAction;
import com.ootd.pickup.member.dto.ProfileImageUpdateRequest;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.repository.PointRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @Mock private MemberManageService memberManageService;

  @Mock private PointRepository pointRepository;

  @Mock private ImageUrlResolver imageUrlResolver;

  @Mock private BidRepository bidRepository;

  @Mock private CertificateRepository certificateRepository;

  @InjectMocks private MemberService memberService;

  @Test
  void 중복되지_않은_회원정보로_회원을_생성한다() {
    // given
    MemberRequest request = new MemberRequest("pickup-user", "픽업회원", "password1234");
    given(memberRepository.existsByLoginId(request.loginId())).willReturn(false);
    given(memberRepository.existsByNickname(request.nickname())).willReturn(false);
    given(memberRepository.save(any(Member.class)))
        .willAnswer(
            invocation -> {
              Member member = invocation.getArgument(0);
              writeMemberId(member, 1L);
              return member;
            });

    // when
    MemberResponse response = memberService.createMember(request);

    // then
    assertThat(response.loginId()).isEqualTo(request.loginId());
    assertThat(response.nickname()).isEqualTo(request.nickname());

    ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
    then(memberRepository).should().save(memberCaptor.capture());
    assertThat(memberCaptor.getValue()).isNotNull();

    assertThat(readPasswordHash(memberCaptor.getValue())).isNotEqualTo(request.password());
    assertThat(
            BCrypt.verifyer()
                .verify(request.password().toCharArray(), readPasswordHash(memberCaptor.getValue()))
                .verified)
        .isTrue();

    ArgumentCaptor<Point> pointCaptor = ArgumentCaptor.forClass(Point.class);
    then(pointRepository).should().save(pointCaptor.capture());
    assertThat(pointCaptor.getValue().getMemberId()).isEqualTo(1L);
    assertThat(pointCaptor.getValue().getBalance()).isZero();
  }

  @Test
  void 아이디가_중복되면_회원을_생성하지_않는다() {
    // given
    MemberRequest request = new MemberRequest("pickup-user", "픽업회원", "password1234");
    given(memberRepository.existsByLoginId(request.loginId())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.createMember(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 사용 중인 아이디입니다.");

    verify(memberRepository, never()).save(any(Member.class));
  }

  @Test
  void 닉네임이_중복되면_회원을_생성하지_않는다() {
    // given
    MemberRequest request = new MemberRequest("pickup-user", "픽업회원", "password1234");
    given(memberRepository.existsByLoginId(request.loginId())).willReturn(false);
    given(memberRepository.existsByNickname(request.nickname())).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.createMember(request))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 사용 중인 닉네임입니다.");

    verify(memberRepository, never()).save(any(Member.class));
  }

  @Test
  void 존재하는_회원정보를_조회하면_내_정보를_반환한다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when
    MyProfileResponse response = memberService.getMyProfile(1L);

    // then
    assertThat(response.loginId()).isEqualTo("pickup-user");
    assertThat(response.nickname()).isEqualTo("픽업회원");
    assertThat(response.profileImageUrl()).isNull();
  }

  @Test
  void 닉네임만_수정하면_다른_회원정보는_유지된다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    UpdateMyProfileRequest request = new UpdateMyProfileRequest("라이츄회원", null, null, null);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(memberRepository.existsByNickname("라이츄회원")).willReturn(false);

    // when
    MyProfileResponse response = memberService.updateMyProfile(1L, request, null).response();

    // then
    assertThat(response.nickname()).isEqualTo("라이츄회원");
    assertThat(response.loginId()).isEqualTo("pickup-user");
    assertThat(readPasswordHash(member)).isEqualTo("password-hash");
  }

  @Test
  void 비밀번호를_수정하면_BCrypt_해시로_저장된다() {
    // given
    String currentPassword = "old-password";
    Member member = Member.create("pickup-user", hashPassword(currentPassword), "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(null, currentPassword, "new-password", null);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when
    memberService.updateMyProfile(1L, request, null);

    // then
    assertThat(readPasswordHash(member)).isNotEqualTo(request.password());
    assertThat(
            BCrypt.verifyer()
                .verify(request.password().toCharArray(), readPasswordHash(member))
                .verified)
        .isTrue();
  }

  @Test
  void 현재비밀번호가_일치하지_않으면_비밀번호를_변경하지_않는다() {
    // given
    String passwordHash = hashPassword("old-password");
    Member member = Member.create("pickup-user", passwordHash, "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(null, "wrong-password", "new-password", null);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when & then
    assertThatThrownBy(() -> memberService.updateMyProfile(1L, request, null))
        .isInstanceOf(PickUpException.class)
        .hasMessage("비밀번호가 일치하지 않습니다.");
    assertThat(readPasswordHash(member)).isEqualTo(passwordHash);
  }

  @Test
  void 현재비밀번호가_일치하지_않으면_닉네임과_비밀번호를_모두_변경하지_않는다() {
    // given
    String passwordHash = hashPassword("old-password");
    Member member = Member.create("pickup-user", passwordHash, "픽업회원");
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest("라이츄회원", "wrong-password", "new-password", null);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when & then
    assertThatThrownBy(() -> memberService.updateMyProfile(1L, request, null))
        .isInstanceOf(PickUpException.class)
        .hasMessage("비밀번호가 일치하지 않습니다.");
    assertThat(member.getNickname()).isEqualTo("픽업회원");
    assertThat(readPasswordHash(member)).isEqualTo(passwordHash);
    then(memberRepository).should(never()).existsByNickname(anyString());
  }

  @Test
  void 프로필이미지를_수정하면_최종_객체의_URL을_반환한다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    String temporaryObjectKey = "uploads/1/profiles/00000000-0000-0000-0000-000000000001.jpg";
    String objectKey = "media/profiles/1/00000000-0000-0000-0000-000000000001.jpg";
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(
            null,
            null,
            null,
            new ProfileImageUpdateRequest(ProfileImageAction.SET, temporaryObjectKey));
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(imageUrlResolver.resolve(objectKey)).willReturn("https://images.test/" + objectKey);

    // when
    MyProfileResponse response = memberService.updateMyProfile(1L, request, objectKey).response();

    // then
    assertThat(response.profileImageUrl()).isEqualTo("https://images.test/" + objectKey);
    assertThat(member.getProfileImageObjectKey()).isEqualTo(objectKey);
  }

  @Test
  void 프로필이미지를_삭제하면_DB_연결을_지우고_커밋후_객체를_삭제한다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    String previousObjectKey = "media/profiles/1/00000000-0000-0000-0000-000000000001.jpg";
    member.updateProfileImage(previousObjectKey);
    UpdateMyProfileRequest request =
        new UpdateMyProfileRequest(
            null, null, null, new ProfileImageUpdateRequest(ProfileImageAction.REMOVE, null));
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when
    MemberService.ProfileUpdateResult result = memberService.updateMyProfile(1L, request, null);
    MyProfileResponse response = result.response();

    // then
    assertThat(response.profileImageUrl()).isNull();
    assertThat(member.getProfileImageObjectKey()).isNull();
    assertThat(result.previousObjectKey()).isEqualTo(previousObjectKey);
  }

  @Test
  void 이미_사용중인_닉네임으로_수정하면_409_예외를_던진다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    UpdateMyProfileRequest request = new UpdateMyProfileRequest("라이츄회원", null, null, null);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(memberRepository.existsByNickname("라이츄회원")).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.updateMyProfile(1L, request, null))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 사용 중인 닉네임입니다.");
  }

  @Test
  void 존재하는_회원의_포인트를_조회하면_잔액을_반환한다() {
    // given
    Point point = Point.create(1L);
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.of(point));

    // when
    PointBalanceResponse response = memberService.getMyPointBalance(1L);

    // then
    assertThat(response.pointBalance()).isZero();
  }

  @Test
  void 포인트정보가_없으면_404_예외를_던진다() {
    // given
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> memberService.getMyPointBalance(1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage("회원을 찾을 수 없습니다.");
  }

  @Test
  void 존재하지_않는_회원정보를_조회하면_404_예외를_던진다() {
    // given
    given(memberManageService.getMemberById(1L)).willThrow(new PickUpException(MEMBER_NOT_FOUND));

    // when & then
    assertThatThrownBy(() -> memberService.getMyProfile(1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage("회원을 찾을 수 없습니다.");
  }

  @Test
  void 내_입찰_내역을_조회하면_경매_정보와_현재가가_포함된_응답을_반환한다() {
    // given
    Member member = createMember(1L);
    Card card = createCard();
    Consignment consignment = createConsignment(2L, card);
    Auction auction = createAuction(10L, consignment, AuctionStatus.ONGOING, 10_000L);
    Bid lastBid = createBid(auction, member, 11_000L, BidStatus.HIGHEST, 100L);
    Certificate certificate = createCertificate(consignment, Grade.MINT, CertificationBody.PSA);

    given(bidRepository.findLastBidsByMemberId(1L, null, 21)).willReturn(List.of(lastBid));
    given(bidRepository.findCurrentPricesByAuctionIds(List.of(10L)))
        .willReturn(Map.of(10L, 11_000L));
    given(certificateRepository.findAllByConsignmentIds(List.of(2L)))
        .willReturn(List.of(certificate));

    // when
    CursorPageResponse<MyBidListItemResponse, String> response =
        memberService.getMyBids(1L, new GetMyBidsRequest(null, 20));

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
    assertThat(response.items()).hasSize(1);

    MyBidListItemResponse item = response.items().get(0);
    assertThat(item.auctionId()).isEqualTo(10L);
    assertThat(item.card().cardId()).isEqualTo(card.getCardId());
    assertThat(item.grade()).isEqualTo("PSA 9");
    assertThat(item.myBidPrice()).isEqualTo(11_000L);
    assertThat(item.currentPrice()).isEqualTo(11_000L);
    assertThat(item.status()).isEqualTo(BidStatus.HIGHEST);
    assertThat(item.auctionStatus()).isEqualTo(AuctionStatus.ONGOING);
  }

  @Test
  void 다른_입찰에_추월당하면_현재가가_내_입찰가보다_높게_반환된다() {
    // given
    Member member = createMember(1L);
    Card card = createCard();
    Consignment consignment = createConsignment(2L, card);
    Auction auction = createAuction(10L, consignment, AuctionStatus.ONGOING, 10_000L);
    Bid lastBid = createBid(auction, member, 11_000L, BidStatus.OUTBID, 100L);

    given(bidRepository.findLastBidsByMemberId(1L, null, 21)).willReturn(List.of(lastBid));
    given(bidRepository.findCurrentPricesByAuctionIds(List.of(10L)))
        .willReturn(Map.of(10L, 12_000L));
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());

    // when
    CursorPageResponse<MyBidListItemResponse, String> response =
        memberService.getMyBids(1L, new GetMyBidsRequest(null, 20));

    // then
    MyBidListItemResponse item = response.items().get(0);
    assertThat(item.myBidPrice()).isEqualTo(11_000L);
    assertThat(item.currentPrice()).isEqualTo(12_000L);
    assertThat(item.status()).isEqualTo(BidStatus.OUTBID);
    assertThat(item.grade()).isNull();
  }

  @Test
  void 결과가_size보다_많으면_hasNext가_true이고_커서가_마지막_입찰ID다() {
    // given
    Member member = createMember(1L);
    Consignment consignmentA = createConsignment(2L, createCard());
    Consignment consignmentB = createConsignment(3L, createCard());
    Auction auctionA = createAuction(10L, consignmentA, AuctionStatus.ONGOING, 10_000L);
    Auction auctionB = createAuction(11L, consignmentB, AuctionStatus.ONGOING, 20_000L);
    Bid bidA = createBid(auctionA, member, 11_000L, BidStatus.HIGHEST, 101L);
    Bid bidB = createBid(auctionB, member, 21_000L, BidStatus.HIGHEST, 100L);

    given(bidRepository.findLastBidsByMemberId(1L, null, 2)).willReturn(List.of(bidA, bidB));
    given(bidRepository.findCurrentPricesByAuctionIds(List.of(10L)))
        .willReturn(Map.of(10L, 11_000L));
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());

    // when
    CursorPageResponse<MyBidListItemResponse, String> response =
        memberService.getMyBids(1L, new GetMyBidsRequest(null, 1));

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo("101");
    assertThat(response.items()).extracting(MyBidListItemResponse::auctionId).containsExactly(10L);
  }

  @Test
  void 유효하지_않은_커서값이면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> memberService.getMyBids(1L, new GetMyBidsRequest("not-a-number", 20)))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(INVALID_CURSOR.getClientExceptionCode().name()));
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void size가_1보다_작으면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> memberService.getMyBids(1L, new GetMyBidsRequest(null, 0)))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(ILLEGAL_ARGUMENT.getClientExceptionCode().name()));
    then(bidRepository).shouldHaveNoInteractions();
  }

  @Test
  void 내_낙찰_내역을_조회하면_낙찰된_항목만_반환한다() {
    // given
    Member member = createMember(1L);
    Card card = createCard();
    Consignment consignment = createConsignment(2L, card);
    Auction auction = createAuction(11L, consignment, AuctionStatus.WON, 300_000L);
    Bid wonBid = createBid(auction, member, 330_000L, BidStatus.WON, 200L);
    Certificate certificate = createCertificate(consignment, Grade.NM, CertificationBody.CGC);

    given(bidRepository.findWonBidsByMemberId(1L, null, 21)).willReturn(List.of(wonBid));
    given(bidRepository.findCurrentPricesByAuctionIds(List.of(11L)))
        .willReturn(Map.of(11L, 330_000L));
    given(certificateRepository.findAllByConsignmentIds(List.of(2L)))
        .willReturn(List.of(certificate));

    // when
    CursorPageResponse<MyBidListItemResponse, String> response =
        memberService.getMyWins(1L, new GetMyWinsRequest(null, 20));

    // then
    assertThat(response.items()).hasSize(1);
    MyBidListItemResponse item = response.items().get(0);
    assertThat(item.auctionId()).isEqualTo(11L);
    assertThat(item.status()).isEqualTo(BidStatus.WON);
    assertThat(item.auctionStatus()).isEqualTo(AuctionStatus.WON);
    assertThat(item.myBidPrice()).isEqualTo(330_000L);
    assertThat(item.currentPrice()).isEqualTo(330_000L);
    then(bidRepository).should(never()).findLastBidsByMemberId(any(), any(), anyInt());
  }

  @Test
  void 낙찰_내역도_결과가_size보다_많으면_hasNext가_true이고_커서가_마지막_입찰ID다() {
    // given
    Member member = createMember(1L);
    Consignment consignmentA = createConsignment(2L, createCard());
    Consignment consignmentB = createConsignment(3L, createCard());
    Auction auctionA = createAuction(10L, consignmentA, AuctionStatus.WON, 10_000L);
    Auction auctionB = createAuction(11L, consignmentB, AuctionStatus.WON, 20_000L);
    Bid bidA = createBid(auctionA, member, 11_000L, BidStatus.WON, 101L);
    Bid bidB = createBid(auctionB, member, 21_000L, BidStatus.WON, 100L);

    given(bidRepository.findWonBidsByMemberId(1L, null, 2)).willReturn(List.of(bidA, bidB));
    given(bidRepository.findCurrentPricesByAuctionIds(List.of(10L)))
        .willReturn(Map.of(10L, 11_000L));
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());

    // when
    CursorPageResponse<MyBidListItemResponse, String> response =
        memberService.getMyWins(1L, new GetMyWinsRequest(null, 1));

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo("101");
    assertThat(response.items()).extracting(MyBidListItemResponse::auctionId).containsExactly(10L);
  }

  private Member createMember(Long memberId) {
    Member member = Member.create("loginId" + memberId, "password-hash", "닉네임" + memberId);
    ReflectionTestUtils.setField(member, "memberId", memberId);
    return member;
  }

  private Card createCard() {
    return Card.builder()
        .cardName("리자몽 1st Edition Holo")
        .cardNumber("4/102")
        .setName("Base Set")
        .language(Language.JAPANESE)
        .rarity(Rarity.MINT)
        .imageUrl("https://example.com/card.png")
        .build();
  }

  private Consignment createConsignment(Long consignmentId, Card card) {
    Consignment consignment =
        Consignment.builder()
            .card(card)
            .sellerMember(createMember(999L))
            .status(ConsignmentStatus.AUCTION_ONGOING)
            .build();
    ReflectionTestUtils.setField(consignment, "consignmentId", consignmentId);
    return consignment;
  }

  private Auction createAuction(
      Long auctionId, Consignment consignment, AuctionStatus status, Long startingPrice) {
    Auction auction =
        Auction.builder()
            .consignment(consignment)
            .startedAt(LocalDateTime.now().minusHours(1))
            .endedAt(LocalDateTime.now().plusHours(1))
            .auctionStatus(status)
            .startingPrice(startingPrice)
            .reservePrice(startingPrice + 5_000L)
            .bidIncrement(500L)
            .build();
    ReflectionTestUtils.setField(auction, "auctionId", auctionId);
    return auction;
  }

  private Bid createBid(
      Auction auction, Member member, Long bidPrice, BidStatus bidStatus, Long bidId) {
    Bid bid = Bid.create(auction, member, bidPrice);
    if (bidStatus == BidStatus.OUTBID) {
      bid.outbid();
    } else if (bidStatus == BidStatus.WON) {
      // Bid 도메인에 WON 전환 뮤테이터가 아직 없어(경매 종료 배치 미구현) 테스트에서 직접 필드를 설정한다.
      ReflectionTestUtils.setField(bid, "bidStatus", BidStatus.WON);
    }
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    return bid;
  }

  private Certificate createCertificate(
      Consignment consignment, Grade grade, CertificationBody certificationBody) {
    return Certificate.builder()
        .consignment(consignment)
        .grade(grade)
        .certificationBody(certificationBody)
        .serialNumber("SN-" + consignment.getConsignmentId())
        .inspectedAt(LocalDate.now())
        .build();
  }

  private String readPasswordHash(Member member) {
    try {
      Field passwordField = Member.class.getDeclaredField("password");
      passwordField.setAccessible(true);
      return (String) passwordField.get(member);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }

  private String hashPassword(String rawPassword) {
    return BCrypt.withDefaults().hashToString(4, rawPassword.toCharArray());
  }

  private void writeMemberId(Member member, Long memberId) {
    try {
      Field memberIdField = Member.class.getDeclaredField("memberId");
      memberIdField.setAccessible(true);
      memberIdField.set(member, memberId);
    } catch (ReflectiveOperationException exception) {
      throw new AssertionError(exception);
    }
  }
}
