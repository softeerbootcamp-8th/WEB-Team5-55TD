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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
