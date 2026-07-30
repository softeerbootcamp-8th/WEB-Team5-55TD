package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.api.AuctionApi;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.service.AuctionService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auctions")
@RequiredArgsConstructor
public class AuctionController implements AuctionApi {

  private final AuctionService auctionService;

  @PostMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<CreateAuctionResponse> registerAuction(
      @MemberId Long memberId, @Valid @RequestBody CreateAuctionRequest createAuctionRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(auctionService.registerAuction(memberId, createAuctionRequest));
  }
}
