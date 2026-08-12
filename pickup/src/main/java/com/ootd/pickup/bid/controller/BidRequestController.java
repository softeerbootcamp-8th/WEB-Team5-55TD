package com.ootd.pickup.bid.controller;

import com.ootd.pickup.bid.api.BidRequestApi;
import com.ootd.pickup.bid.dto.request.CreateBidRequestRequest;
import com.ootd.pickup.bid.dto.response.BidRequestResultResponse;
import com.ootd.pickup.bid.dto.response.CreateBidRequestResponse;
import com.ootd.pickup.bid.service.BidRequestService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auctions/{auctionId}/bid-requests")
@RequiredArgsConstructor
public class BidRequestController implements BidRequestApi {

  private final BidRequestService bidRequestService;

  @PostMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<CreateBidRequestResponse> createBidRequest(
      @PathVariable Long auctionId,
      @MemberId Long memberId,
      @Valid @RequestBody CreateBidRequestRequest createBidRequestRequest) {
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(
            bidRequestService.createBidRequest(
                auctionId, memberId, createBidRequestRequest.bidPrice()));
  }

  @GetMapping("/{bidRequestId}")
  @RequireAuthentication
  @Override
  public ResponseEntity<BidRequestResultResponse> getBidRequestResult(
      @PathVariable Long auctionId, @PathVariable Long bidRequestId, @MemberId Long memberId) {
    return ResponseEntity.ok(
        bidRequestService.getBidRequestResult(auctionId, bidRequestId, memberId));
  }
}
