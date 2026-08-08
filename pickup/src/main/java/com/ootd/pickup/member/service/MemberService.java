package com.ootd.pickup.member.service;

import static com.ootd.pickup.global.exception.ExceptionCode.ILLEGAL_ARGUMENT;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_CURSOR;
import static com.ootd.pickup.global.exception.ExceptionCode.INVALID_PASSWORD;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_LOGIN_ID_ALREADY_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.MEMBER_NICKNAME_ALREADY_EXISTS;
import static com.ootd.pickup.global.exception.ExceptionCode.POINT_NOT_FOUND;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.ootd.pickup.auction.domain.Auction;
import com.ootd.pickup.auction.domain.Watch;
import com.ootd.pickup.auction.dto.request.GetMyWatchesRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.repository.watch.WatchRepository;
import com.ootd.pickup.bid.domain.Bid;
import com.ootd.pickup.bid.dto.request.GetMyBidsRequest;
import com.ootd.pickup.bid.dto.request.GetMyWinsRequest;
import com.ootd.pickup.bid.dto.response.MyBidListItemResponse;
import com.ootd.pickup.bid.repository.BidRepository;
import com.ootd.pickup.consignments.domain.Certificate;
import com.ootd.pickup.consignments.domain.ConsignmentImage;
import com.ootd.pickup.consignments.repository.certificate.CertificateRepository;
import com.ootd.pickup.consignments.repository.consignmentImage.ConsignmentImageRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;
import com.ootd.pickup.images.service.ImageUrlResolver;
import com.ootd.pickup.member.domain.Member;
import com.ootd.pickup.member.dto.*;
import com.ootd.pickup.member.repository.MemberRepository;
import com.ootd.pickup.point.domain.Point;
import com.ootd.pickup.point.domain.PointTransaction;
import com.ootd.pickup.point.dto.request.GetPointTransactionsRequest;
import com.ootd.pickup.point.dto.response.PointTransactionItemResponse;
import com.ootd.pickup.point.repository.PointRepository;
import com.ootd.pickup.point.repository.PointTransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class MemberService {

  private static final int BCRYPT_COST_FACTOR = 12;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;
  private final MemberRepository memberRepository;
  private final MemberManageService memberManageService;
  private final PointRepository pointRepository;
  private final PointTransactionRepository pointTransactionRepository;
  private final ImageUrlResolver imageUrlResolver;
  private final BidRepository bidRepository;
  private final CertificateRepository certificateRepository;
  private final WatchRepository watchRepository;
  private final ConsignmentImageRepository consignmentImageRepository;

  public MemberResponse createMember(MemberRequest memberRequest) {
    if (memberRepository.existsByLoginId(memberRequest.loginId())) {
      throw new PickUpException(MEMBER_LOGIN_ID_ALREADY_EXISTS);
    }

    if (memberRepository.existsByNickname(memberRequest.nickname())) {
      throw new PickUpException(MEMBER_NICKNAME_ALREADY_EXISTS);
    }

    String passwordHash = hashPassword(memberRequest.password());
    Member member = Member.create(memberRequest.loginId(), passwordHash, memberRequest.nickname());

    Member savedMember;
    try {
      savedMember = memberRepository.save(member);
    } catch (DataIntegrityViolationException exception) {
      throw new PickUpException(MEMBER_LOGIN_ID_ALREADY_EXISTS);
    }

    pointRepository.save(Point.create(savedMember.getMemberId()));
    return new MemberResponse(
        savedMember.getMemberId(), savedMember.getLoginId(), savedMember.getNickname(), null);
  }

  @Transactional(readOnly = true)
  public MyProfileResponse getMyProfile(Long memberId) {
    Member member = memberManageService.getMemberById(memberId);
    return toMyProfileResponse(member);
  }

  public ProfileUpdateResult updateMyProfile(
      Long memberId,
      UpdateMyProfileRequest updateMyProfileRequest,
      String finalizedProfileObjectKey) {
    Member member = memberManageService.getMemberById(memberId);
    String nickname = updateMyProfileRequest.nickname();

    if (updateMyProfileRequest.password() != null
        && !member.isPasswordMatched(updateMyProfileRequest.currentPassword())) {
      throw new PickUpException(INVALID_PASSWORD);
    }

    if (nickname != null
        && !nickname.equals(member.getNickname())
        && memberRepository.existsByNickname(nickname)) {
      throw new PickUpException(MEMBER_NICKNAME_ALREADY_EXISTS);
    }

    String passwordHash =
        updateMyProfileRequest.password() == null
            ? null
            : hashPassword(updateMyProfileRequest.password());
    member.updateProfile(nickname, passwordHash);
    String previousObjectKey = member.getProfileImageObjectKey();
    updateProfileImage(member, updateMyProfileRequest, finalizedProfileObjectKey);
    return new ProfileUpdateResult(toMyProfileResponse(member), previousObjectKey);
  }

  @Transactional(readOnly = true)
  public PointBalanceResponse getMyPointBalance(Long memberId) {
    Point point =
        pointRepository
            .findByMemberId(memberId)
            .orElseThrow(() -> new PickUpException(POINT_NOT_FOUND));
    return new PointBalanceResponse(
        point.getBalance(), point.getReservedBalance(), point.getAvailableBalance());
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<PointTransactionItemResponse, String> getMyPointTransactions(
      Long memberId, GetPointTransactionsRequest request) {
    int size = resolveSize(request.size());
    Long cursorId = decodeCursor(request.cursor());
    List<PointTransaction> fetched =
        pointTransactionRepository.findAllByMemberId(memberId, cursorId, size + 1);
    boolean hasNext = fetched.size() > size;
    List<PointTransaction> page = hasNext ? fetched.subList(0, size) : fetched;
    List<PointTransactionItemResponse> items =
        page.stream().map(PointTransactionItemResponse::fromEntity).toList();
    String nextCursor = hasNext ? String.valueOf(page.getLast().getPointTransactionId()) : null;
    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<MyBidListItemResponse, String> getMyBids(
      Long memberId, GetMyBidsRequest request) {
    int size = resolveSize(request.size());
    Long cursorBidId = decodeCursor(request.cursor());

    List<Bid> fetched = bidRepository.findLastBidsByMemberId(memberId, cursorBidId, size + 1);
    boolean hasNext = fetched.size() > size;
    List<Bid> page = hasNext ? fetched.subList(0, size) : fetched;

    List<MyBidListItemResponse> items = assembleMyBids(page);

    String nextCursor = hasNext ? String.valueOf(page.getLast().getBidId()) : null;
    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<MyBidListItemResponse, String> getMyWins(
      Long memberId, GetMyWinsRequest request) {
    int size = resolveSize(request.size());
    Long cursorBidId = decodeCursor(request.cursor());

    List<Bid> fetched = bidRepository.findWonBidsByMemberId(memberId, cursorBidId, size + 1);
    boolean hasNext = fetched.size() > size;
    List<Bid> page = hasNext ? fetched.subList(0, size) : fetched;

    List<MyBidListItemResponse> items = assembleMyBids(page);

    String nextCursor = hasNext ? String.valueOf(page.getLast().getBidId()) : null;
    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  @Transactional(readOnly = true)
  public CursorPageResponse<AuctionListItemResponse, String> getMyWatches(
      Long memberId, GetMyWatchesRequest request) {
    int size = resolveSize(request.size());
    Long cursorWatchId = decodeCursor(request.cursor());

    List<Watch> fetched =
        watchRepository.findAllActiveByMemberId(memberId, cursorWatchId, size + 1);
    boolean hasNext = fetched.size() > size;
    List<Watch> page = hasNext ? fetched.subList(0, size) : fetched;

    List<AuctionListItemResponse> items = assembleMyWatches(page);

    String nextCursor = hasNext ? String.valueOf(page.getLast().getWatchId()) : null;
    return CursorPageResponse.from(items, hasNext, nextCursor);
  }

  private List<AuctionListItemResponse> assembleMyWatches(List<Watch> myWatches) {
    List<Auction> auctions = myWatches.stream().map(Watch::getAuction).toList();
    List<Long> auctionIds = auctions.stream().map(Auction::getAuctionId).toList();
    List<Long> consignmentIds =
        auctions.stream().map(a -> a.getConsignment().getConsignmentId()).toList();

    Map<Long, Long> watchCounts = watchRepository.countByAuctionIds(auctionIds);
    Map<Long, Certificate> certificatesByConsignmentId =
        certificateRepository.findAllByConsignmentIds(consignmentIds).stream()
            .collect(Collectors.toMap(c -> c.getConsignment().getConsignmentId(), c -> c));
    Map<Long, String> thumbnailsByConsignmentId = resolveThumbnails(consignmentIds);

    return auctions.stream()
        .map(
            auction -> {
              Long consignmentId = auction.getConsignment().getConsignmentId();
              return AuctionListItemResponse.of(
                  auction,
                  certificatesByConsignmentId.get(consignmentId),
                  thumbnailsByConsignmentId.get(consignmentId),
                  watchCounts.getOrDefault(auction.getAuctionId(), 0L),
                  true,
                  auction.getCurrentPrice());
            })
        .toList();
  }

  private Map<Long, String> resolveThumbnails(List<Long> consignmentIds) {
    if (consignmentIds.isEmpty()) {
      return Map.of();
    }

    List<ConsignmentImage> images =
        consignmentImageRepository.findAllByConsignmentIdsOrderByConsignmentIdAndImageOrder(
            consignmentIds);

    return images.stream()
        .collect(
            Collectors.toMap(
                image -> image.getConsignment().getConsignmentId(),
                image -> imageUrlResolver.resolve(image.getObjectKey()),
                (first, second) -> first));
  }

  private List<MyBidListItemResponse> assembleMyBids(List<Bid> myLastBids) {
    List<Long> auctionIds = myLastBids.stream().map(b -> b.getAuction().getAuctionId()).toList();
    List<Long> consignmentIds =
        myLastBids.stream().map(b -> b.getAuction().getConsignment().getConsignmentId()).toList();

    Map<Long, Long> currentPrices = bidRepository.findCurrentPricesByAuctionIds(auctionIds);
    Map<Long, Certificate> certificatesByConsignmentId =
        certificateRepository.findAllByConsignmentIds(consignmentIds).stream()
            .collect(Collectors.toMap(c -> c.getConsignment().getConsignmentId(), c -> c));

    return myLastBids.stream()
        .map(
            myLastBid -> {
              Long auctionId = myLastBid.getAuction().getAuctionId();
              Long consignmentId = myLastBid.getAuction().getConsignment().getConsignmentId();
              return MyBidListItemResponse.of(
                  myLastBid,
                  certificatesByConsignmentId.get(consignmentId),
                  currentPrices.getOrDefault(auctionId, myLastBid.getAuction().getStartingPrice()));
            })
        .toList();
  }

  private Long decodeCursor(String cursor) {
    if (!StringUtils.hasText(cursor)) {
      return null;
    }
    try {
      return Long.parseLong(cursor);
    } catch (NumberFormatException e) {
      throw new PickUpException(INVALID_CURSOR);
    }
  }

  private int resolveSize(Integer size) {
    if (size == null) {
      return DEFAULT_SIZE;
    }
    if (size < 1) {
      throw new PickUpException(ILLEGAL_ARGUMENT);
    }
    return Math.min(size, MAX_SIZE);
  }

  private String hashPassword(String rawPassword) {
    return BCrypt.withDefaults().hashToString(BCRYPT_COST_FACTOR, rawPassword.toCharArray());
  }

  private void updateProfileImage(
      Member member,
      UpdateMyProfileRequest updateMyProfileRequest,
      String finalizedProfileObjectKey) {
    ProfileImageUpdateRequest profileImageUpdate = updateMyProfileRequest.profileImageUpdate();
    if (profileImageUpdate == null) {
      return;
    }

    switch (profileImageUpdate.action()) {
      case SET -> member.updateProfileImage(finalizedProfileObjectKey);
      case REMOVE -> member.removeProfileImage();
    }
  }

  private MyProfileResponse toMyProfileResponse(Member member) {
    return MyProfileResponse.from(
        member, imageUrlResolver.resolve(member.getProfileImageObjectKey()));
  }

  public record ProfileUpdateResult(MyProfileResponse response, String previousObjectKey) {}
}
