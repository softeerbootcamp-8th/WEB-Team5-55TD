package com.ootd.pickup.member.controller;

import com.ootd.pickup.auction.dto.request.GetMyWatchesRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.bid.dto.request.GetMyBidsRequest;
import com.ootd.pickup.bid.dto.request.GetMyWinsRequest;
import com.ootd.pickup.bid.dto.response.MyBidListItemResponse;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.member.api.MemberApi;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import com.ootd.pickup.member.dto.UpdateMyProfileRequest;
import com.ootd.pickup.member.service.MemberService;
import com.ootd.pickup.member.service.ProfileApplicationService;
import com.ootd.pickup.point.dto.request.ChargePointRequest;
import com.ootd.pickup.point.dto.request.GetPointTransactionsRequest;
import com.ootd.pickup.point.dto.response.PointChargeResponse;
import com.ootd.pickup.point.dto.response.PointTransactionItemResponse;
import com.ootd.pickup.point.service.PointChargeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController implements MemberApi {

  private final MemberService memberService;
  private final ProfileApplicationService profileApplicationService;
  private final PointChargeService pointChargeService;

  @PostMapping
  @Override
  public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody MemberRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(request));
  }

  @GetMapping("/me")
  @Override
  @RequireAuthentication
  public ResponseEntity<MyProfileResponse> getMyProfile(@MemberId Long memberId) {
    return ResponseEntity.ok(memberService.getMyProfile(memberId));
  }

  @PatchMapping("/me")
  @Override
  @RequireAuthentication
  public ResponseEntity<MyProfileResponse> updateMyProfile(
      @MemberId Long memberId, @Valid @RequestBody UpdateMyProfileRequest request) {
    return ResponseEntity.ok(profileApplicationService.updateMyProfile(memberId, request));
  }

  @GetMapping("/me/points")
  @Override
  @RequireAuthentication
  public ResponseEntity<PointBalanceResponse> getMyPointBalance(@MemberId Long memberId) {
    return ResponseEntity.ok(memberService.getMyPointBalance(memberId));
  }

  @GetMapping("/me/point-transactions")
  @Override
  @RequireAuthentication
  public ResponseEntity<CursorPageResponse<PointTransactionItemResponse, String>>
      getMyPointTransactions(
          @MemberId Long memberId, @Valid @ModelAttribute GetPointTransactionsRequest request) {
    return ResponseEntity.ok(memberService.getMyPointTransactions(memberId, request));
  }

  @PostMapping("/me/point-charges")
  @Override
  @RequireAuthentication
  public ResponseEntity<PointChargeResponse> chargeMyPoint(
      @MemberId Long memberId, @Valid @RequestBody ChargePointRequest request) {
    try {
      PointChargeResponse response =
          pointChargeService.chargePoint(memberId, request.amount(), request.idempotencyKey());
      return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (DataIntegrityViolationException e) {
      // chargePoint()의 트랜잭션은 이미 롤백된 뒤 이 예외가 전파됐다. 완전히 새 트랜잭션으로
      // 이미 처리된 결과를 읽어 그대로 돌려준다 — 자세한 이유는 PointChargeService 참고.
      return ResponseEntity.ok(
          pointChargeService.getChargeResult(memberId, request.idempotencyKey()));
    }
  }

  @GetMapping("/me/bids")
  @Override
  @RequireAuthentication
  public ResponseEntity<CursorPageResponse<MyBidListItemResponse, String>> getMyBids(
      @MemberId Long memberId, @Valid @ModelAttribute GetMyBidsRequest getMyBidsRequest) {
    return ResponseEntity.ok(memberService.getMyBids(memberId, getMyBidsRequest));
  }

  @GetMapping("/me/wins")
  @Override
  @RequireAuthentication
  public ResponseEntity<CursorPageResponse<MyBidListItemResponse, String>> getMyWins(
      @MemberId Long memberId, @Valid @ModelAttribute GetMyWinsRequest getMyWinsRequest) {
    return ResponseEntity.ok(memberService.getMyWins(memberId, getMyWinsRequest));
  }

  @GetMapping("/me/watches")
  @Override
  @RequireAuthentication
  public ResponseEntity<CursorPageResponse<AuctionListItemResponse, String>> getMyWatches(
      @MemberId Long memberId, @Valid @ModelAttribute GetMyWatchesRequest getMyWatchesRequest) {
    return ResponseEntity.ok(memberService.getMyWatches(memberId, getMyWatchesRequest));
  }
}
