package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.api.SellerAuctionsApi;
import com.ootd.pickup.auction.dto.request.GetMyAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.service.AuctionService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/me/auctions")
@RequiredArgsConstructor
public class SellerAuctionsController implements SellerAuctionsApi {

  private final AuctionService auctionService;

  @GetMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<CursorPageResponse<AuctionListItemResponse, String>> getMyAuctions(
      @MemberId Long memberId, @Valid @ModelAttribute GetMyAuctionsRequest request) {
    return ResponseEntity.ok(auctionService.getMyAuctions(memberId, request));
  }
}
