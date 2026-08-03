package com.ootd.pickup.bid.controller;

import com.ootd.pickup.bid.api.BidApi;
import com.ootd.pickup.bid.dto.request.GetAuctionBidsRequest;
import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.AuctionBidListItemResponse;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.bid.service.BidService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.OptionalMemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auctions/{auctionId}/bids")
@RequiredArgsConstructor
public class BidController implements BidApi {

  private final BidService bidService;

  @PostMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<PlaceBidResponse> placeBid(
      @PathVariable Long auctionId,
      @MemberId Long memberId,
      @Valid @RequestBody PlaceBidRequest placeBidRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(bidService.placeBid(auctionId, memberId, placeBidRequest));
  }

  @GetMapping
  @Override
  public ResponseEntity<CursorPageResponse<AuctionBidListItemResponse, String>> getAuctionBids(
      @PathVariable Long auctionId,
      @OptionalMemberId Long memberId,
      @Valid @ModelAttribute GetAuctionBidsRequest getAuctionBidsRequest) {
    return ResponseEntity.ok(bidService.getAuctionBids(auctionId, memberId, getAuctionBidsRequest));
  }
}
