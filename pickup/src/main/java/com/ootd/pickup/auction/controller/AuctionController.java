package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.api.AuctionApi;
import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.request.SearchAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionDetailResponse;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.auction.service.AuctionService;
import com.ootd.pickup.auction.service.FeaturedAuctionFacade;
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
@RequestMapping("/auctions")
@RequiredArgsConstructor
public class AuctionController implements AuctionApi {

  private final AuctionService auctionService;
  private final FeaturedAuctionFacade featuredAuctionFacade;

  @PostMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<CreateAuctionResponse> registerAuction(
      @MemberId Long memberId, @Valid @RequestBody CreateAuctionRequest createAuctionRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(auctionService.registerAuction(memberId, createAuctionRequest));
  }

  @GetMapping
  @Override
  public ResponseEntity<CursorPageResponse<AuctionListItemResponse, String>> searchAuctions(
      @OptionalMemberId Long memberId,
      @Valid @ModelAttribute SearchAuctionsRequest searchAuctionsRequest) {
    return ResponseEntity.ok(auctionService.searchAuctions(memberId, searchAuctionsRequest));
  }

  @GetMapping("/featured")
  @Override
  public ResponseEntity<AuctionListItemResponse> getFeaturedAuction(
      @OptionalMemberId Long memberId) {
    return ResponseEntity.ok(featuredAuctionFacade.getFeaturedAuction(memberId));
  }

  @GetMapping("/{auctionId}")
  @Override
  public ResponseEntity<AuctionDetailResponse> getAuctionDetail(
      @OptionalMemberId Long memberId, @PathVariable Long auctionId) {
    return ResponseEntity.ok(auctionService.getAuctionDetail(memberId, auctionId));
  }
}
