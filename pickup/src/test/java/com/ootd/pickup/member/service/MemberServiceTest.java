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
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.dto.request.GetMyWatchesRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.auth.service.AuthService;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.domain.BidStatus;
import com.ootd.pickup.bid.dto.request.GetMyBidsRequest;
import com.ootd.pickup.bid.dto.request.GetMyWinsRequest;
import com.ootd.pickup.bid.dto.response.MyBidListItemResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.bid.service.BidService;
import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.domain.Rarity;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.CertificationBody;
import com.ootd.pickup.consignments.domain.Consignment;
import com.ootd.pickup.consignments.domain.ConsignmentStatus;
import com.ootd.pickup.consignments.domain.Grade;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.consignments.service.ConsignmentService;
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
import com.ootd.pickup.member.dto.WithdrawMemberRequest;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointTransaction;
import com.ootd.pickup.point.dto.request.GetPointTransactionsRequest;
import com.ootd.pickup.point.dto.response.PointTransactionItemResponse;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointTransactionRepository;
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

  @Mock private PointTransactionRepository pointTransactionRepository;

  @Mock private ImageUrlResolver imageUrlResolver;

  @Mock private BidRepository bidRepository;

  @Mock private CertificateRepository certificateRepository;

  @Mock private WatchRepository watchRepository;

  @Mock private ConsignmentImageRepository consignmentImageRepository;

  @Mock private ConsignmentService consignmentService;

  @Mock private BidService bidService;

  @Mock private AuthService authService;

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
    assertThat(response.reservedPointBalance()).isZero();
    assertThat(response.availablePointBalance()).isZero();
  }

  @Test
  void 포인트정보가_없으면_404_예외를_던진다() {
    // given
    given(pointRepository.findByMemberId(1L)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> memberService.getMyPointBalance(1L))
        .isInstanceOf(PickUpException.class)
        .hasMessage("포인트 정보를 찾을 수 없습니다.");
  }

  @Test
  void 포인트_거래내역을_최신순_커서페이지로_조회한다() {
    // given
    Member member = Member.create("pickup-user", "password-hash", "픽업회원");
    ReflectionTestUtils.setField(member, "memberId", 1L);
    Auction auction = Auction.builder().build();
    ReflectionTestUtils.setField(auction, "auctionId", 1L);
    PointTransaction newest = PointTransaction.forAuctionPayout(member, 1_000L, 3_000L, auction);
    PointTransaction next = PointTransaction.forAuctionPayment(member, 500L, 2_000L, auction);
    ReflectionTestUtils.setField(newest, "pointTransactionId", 3L);
    ReflectionTestUtils.setField(next, "pointTransactionId", 2L);
    given(pointTransactionRepository.findAllByMemberId(1L, null, 2))
        .willReturn(List.of(newest, next));

    // when
    CursorPageResponse<PointTransactionItemResponse, String> response =
        memberService.getMyPointTransactions(1L, new GetPointTransactionsRequest(null, 1));

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo("3");
    assertThat(response.items())
        .singleElement()
        .extracting(PointTransactionItemResponse::amount)
        .isEqualTo(1_000L);
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
    // 내 입찰(100L) 대신 다른 회원의 입찰(999L)이 현재 최고가로 등록된 상태를 시뮬레이션한다.
    auction.updateWinningBid(999L, 12_000L);
    Bid lastBid = createBid(auction, member, 11_000L, BidStatus.OUTBID, 100L);

    given(bidRepository.findLastBidsByMemberId(1L, null, 21)).willReturn(List.of(lastBid));
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
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());

    // when
    CursorPageResponse<MyBidListItemResponse, String> response =
        memberService.getMyWins(1L, new GetMyWinsRequest(null, 1));

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo("101");
    assertThat(response.items()).extracting(MyBidListItemResponse::auctionId).containsExactly(10L);
  }

  @Test
  void 내_관심_목록을_조회하면_예정_경매_정보가_포함된_응답을_반환한다() {
    // given
    Member member = createMember(1L);
    Card card = createCard();
    Consignment consignment = createConsignment(2L, card);
    Auction auction = createAuction(10L, consignment, AuctionStatus.SCHEDULED, 10_000L);
    Watch watch = createWatch(500L, member, auction);
    Certificate certificate = createCertificate(consignment, Grade.MINT, CertificationBody.PSA);

    given(watchRepository.findAllActiveByMemberId(1L, null, 21)).willReturn(List.of(watch));
    given(watchRepository.countByAuctionIds(List.of(10L))).willReturn(Map.of(10L, 3L));
    given(certificateRepository.findAllByConsignmentIds(List.of(2L)))
        .willReturn(List.of(certificate));
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                List.of(2L)))
        .willReturn(List.of());

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        memberService.getMyWatches(1L, new GetMyWatchesRequest(null, 20));

    // then
    assertThat(response.hasNext()).isFalse();
    assertThat(response.cursor()).isNull();
    assertThat(response.items()).hasSize(1);

    AuctionListItemResponse item = response.items().get(0);
    assertThat(item.auctionId()).isEqualTo(10L);
    assertThat(item.auctionStatus()).isEqualTo(AuctionStatus.SCHEDULED);
    assertThat(item.grade()).isEqualTo("PSA 9");
    assertThat(item.currentPrice()).isNull();
    assertThat(item.watchCount()).isEqualTo(3L);
    assertThat(item.watched()).isTrue();
  }

  @Test
  void 진행중인_관심_경매는_현재_최고_입찰가를_currentPrice로_반환한다() {
    // given
    Member member = createMember(1L);
    Consignment consignment = createConsignment(2L, createCard());
    Auction auction = createAuction(10L, consignment, AuctionStatus.ONGOING, 10_000L);
    auction.updateWinningBid(200L, 12_000L);
    Watch watch = createWatch(500L, member, auction);

    given(watchRepository.findAllActiveByMemberId(1L, null, 21)).willReturn(List.of(watch));
    given(watchRepository.countByAuctionIds(List.of(10L))).willReturn(Map.of());
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                List.of(2L)))
        .willReturn(List.of());

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        memberService.getMyWatches(1L, new GetMyWatchesRequest(null, 20));

    // then
    AuctionListItemResponse item = response.items().get(0);
    assertThat(item.currentPrice()).isEqualTo(12_000L);
  }

  @Test
  void 입찰이_없는_진행중_관심_경매는_시작가를_currentPrice로_반환한다() {
    // given
    Member member = createMember(1L);
    Consignment consignment = createConsignment(2L, createCard());
    Auction auction = createAuction(10L, consignment, AuctionStatus.ONGOING, 10_000L);
    Watch watch = createWatch(500L, member, auction);

    given(watchRepository.findAllActiveByMemberId(1L, null, 21)).willReturn(List.of(watch));
    given(watchRepository.countByAuctionIds(List.of(10L))).willReturn(Map.of());
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                List.of(2L)))
        .willReturn(List.of());

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        memberService.getMyWatches(1L, new GetMyWatchesRequest(null, 20));

    // then
    AuctionListItemResponse item = response.items().get(0);
    assertThat(item.currentPrice()).isEqualTo(10_000L);
  }

  @Test
  void 관심목록_결과가_size보다_많으면_hasNext가_true이고_커서가_마지막_관심ID다() {
    // given
    Member member = createMember(1L);
    Consignment consignmentA = createConsignment(2L, createCard());
    Consignment consignmentB = createConsignment(3L, createCard());
    Auction auctionA = createAuction(10L, consignmentA, AuctionStatus.SCHEDULED, 10_000L);
    Auction auctionB = createAuction(11L, consignmentB, AuctionStatus.SCHEDULED, 20_000L);
    Watch watchA = createWatch(101L, member, auctionA);
    Watch watchB = createWatch(100L, member, auctionB);

    given(watchRepository.findAllActiveByMemberId(1L, null, 2)).willReturn(List.of(watchA, watchB));
    given(watchRepository.countByAuctionIds(List.of(10L))).willReturn(Map.of());
    given(certificateRepository.findAllByConsignmentIds(List.of(2L))).willReturn(List.of());
    given(
            consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
                List.of(2L)))
        .willReturn(List.of());

    // when
    CursorPageResponse<AuctionListItemResponse, String> response =
        memberService.getMyWatches(1L, new GetMyWatchesRequest(null, 1));

    // then
    assertThat(response.hasNext()).isTrue();
    assertThat(response.cursor()).isEqualTo("101");
    assertThat(response.items())
        .extracting(AuctionListItemResponse::auctionId)
        .containsExactly(10L);
  }

  @Test
  void 관심목록_커서값이_유효하지_않으면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(
            () -> memberService.getMyWatches(1L, new GetMyWatchesRequest("not-a-number", 20)))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(INVALID_CURSOR.getClientExceptionCode().name()));
    then(watchRepository).shouldHaveNoInteractions();
  }

  @Test
  void 관심목록_size가_1보다_작으면_예외가_발생한다() {
    // when & then
    assertThatThrownBy(() -> memberService.getMyWatches(1L, new GetMyWatchesRequest(null, 0)))
        .isInstanceOf(PickUpException.class)
        .satisfies(
            exception ->
                assertThat(((PickUpException) exception).getExceptionCodeName())
                    .isEqualTo(ILLEGAL_ARGUMENT.getClientExceptionCode().name()));
    then(watchRepository).shouldHaveNoInteractions();
  }

  @Test
  void 올바른_비밀번호로_탈퇴하면_회원_상태가_WITHDRAWN으로_전환된다() {
    // given
    String password = "password1234";
    Member member = Member.create("pickup-user", hashPassword(password), "픽업회원");
    writeMemberId(member, 1L);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(consignmentService.hasActiveConsignment(1L)).willReturn(false);
    given(bidService.hasActiveBid(1L)).willReturn(false);

    // when
    memberService.withdrawMember(1L, new WithdrawMemberRequest(password));

    // then
    assertThat(member.isWithdrawn()).isTrue();
    assertThat(member.getLoginId()).isNull();
    assertThat(readPasswordHash(member)).isNull();
    then(authService).should().revokeAllRefreshTokens(1L);
  }

  @Test
  void 비밀번호가_일치하지_않으면_탈퇴하지_않는다() {
    // given
    Member member = Member.create("pickup-user", hashPassword("password1234"), "픽업회원");
    writeMemberId(member, 1L);
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when & then
    assertThatThrownBy(
            () -> memberService.withdrawMember(1L, new WithdrawMemberRequest("wrong-password")))
        .isInstanceOf(PickUpException.class)
        .hasMessage("비밀번호가 일치하지 않습니다.");
    assertThat(member.isWithdrawn()).isFalse();
    then(consignmentService).shouldHaveNoInteractions();
    then(bidService).shouldHaveNoInteractions();
    then(authService).shouldHaveNoInteractions();
  }

  @Test
  void 이미_탈퇴한_회원이_다시_탈퇴하면_예외가_발생한다() {
    // given
    String password = "password1234";
    Member member = Member.create("pickup-user", hashPassword(password), "픽업회원");
    writeMemberId(member, 1L);
    member.withdraw();
    given(memberManageService.getMemberById(1L)).willReturn(member);

    // when & then
    assertThatThrownBy(() -> memberService.withdrawMember(1L, new WithdrawMemberRequest(password)))
        .isInstanceOf(PickUpException.class)
        .hasMessage("이미 탈퇴한 회원입니다.");
    then(authService).shouldHaveNoInteractions();
  }

  @Test
  void 경매_예정_또는_진행_중인_상품을_등록해_두면_탈퇴할_수_없다() {
    // given
    String password = "password1234";
    Member member = Member.create("pickup-user", hashPassword(password), "픽업회원");
    writeMemberId(member, 1L);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(consignmentService.hasActiveConsignment(1L)).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.withdrawMember(1L, new WithdrawMemberRequest(password)))
        .isInstanceOf(PickUpException.class)
        .hasMessage("진행 중인 경매 또는 입찰이 있어 탈퇴할 수 없습니다.");
    assertThat(member.isWithdrawn()).isFalse();
    then(bidService).shouldHaveNoInteractions();
    then(authService).shouldHaveNoInteractions();
  }

  @Test
  void 최고_입찰_중인_경매가_있으면_탈퇴할_수_없다() {
    // given
    String password = "password1234";
    Member member = Member.create("pickup-user", hashPassword(password), "픽업회원");
    writeMemberId(member, 1L);
    given(memberManageService.getMemberById(1L)).willReturn(member);
    given(consignmentService.hasActiveConsignment(1L)).willReturn(false);
    given(bidService.hasActiveBid(1L)).willReturn(true);

    // when & then
    assertThatThrownBy(() -> memberService.withdrawMember(1L, new WithdrawMemberRequest(password)))
        .isInstanceOf(PickUpException.class)
        .hasMessage("진행 중인 경매 또는 입찰이 있어 탈퇴할 수 없습니다.");
    assertThat(member.isWithdrawn()).isFalse();
    then(authService).shouldHaveNoInteractions();
  }

  private Watch createWatch(Long watchId, Member member, Auction auction) {
    Watch watch = Watch.builder().member(member).auction(auction).build();
    ReflectionTestUtils.setField(watch, "watchId", watchId);
    return watch;
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
        .rarity(Rarity.RARE_HOLO)
        .imageUrl("https://example.com/card.png")
        .build();
  }

  private Consignment createConsignment(Long consignmentId, Card card) {
    Consignment consignment =
        Consignment.builder()
            .card(card)
            .sellerMember(createMember(999L))
            .status(ConsignmentStatus.IN_AUCTION)
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

  /**
   * bidStatus는 저장된 값이 아니라 auction.winningBidId/auctionStatus로 계산된다({@link Bid#getBidStatus()}).
   * HIGHEST/WON을 시뮬레이션하려면 이 Bid를 Auction의 winningBid로 지정해야 하고, OUTBID는 그냥 지정하지 않으면(다른 누군가가
   * winningBid로 남아 있으면) 자연히 성립한다.
   */
  private Bid createBid(
      Auction auction, Member member, Long bidPrice, BidStatus bidStatus, Long bidId) {
    Bid bid = Bid.create(auction, member, bidPrice);
    ReflectionTestUtils.setField(bid, "bidId", bidId);
    if (bidStatus != BidStatus.OUTBID) {
      auction.updateWinningBid(bidId, bidPrice);
    }
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
