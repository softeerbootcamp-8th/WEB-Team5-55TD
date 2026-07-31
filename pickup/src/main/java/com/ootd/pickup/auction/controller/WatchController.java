package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.api.WatchApi;
import com.ootd.pickup.auction.dto.response.WatchResponse;
import com.ootd.pickup.auction.service.WatchService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auctions/{auctionId}/watch")
@RequiredArgsConstructor
public class WatchController implements WatchApi {

  private final WatchService watchService;

  @PostMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<WatchResponse> registerWatch(
      @MemberId Long memberId, @PathVariable Long auctionId) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(watchService.registerWatch(memberId, auctionId));
  }

  @DeleteMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<Void> deleteWatch(@MemberId Long memberId, @PathVariable Long auctionId) {
    watchService.deleteWatch(memberId, auctionId);
    return ResponseEntity.noContent().build();
  }
}
