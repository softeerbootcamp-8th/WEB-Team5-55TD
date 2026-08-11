package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
import com.ootd.pickup.auction.service.SellerStatsService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/me/stats")
@RequiredArgsConstructor
public class SellerStatsController {

  private final SellerStatsService sellerStatsService;

  @GetMapping
  @RequireAuthentication
  public ResponseEntity<SellerStatsResponse> getMySellerStats(@MemberId Long memberId) {
    return ResponseEntity.ok(sellerStatsService.getSellerStats(memberId));
  }
}
