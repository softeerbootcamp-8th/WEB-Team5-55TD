package com.ootd.pickup.admin.controller;

import com.ootd.pickup.admin.dto.request.AdminCancelAuctionRequest;
import com.ootd.pickup.admin.dto.request.AdminSearchAuctionsRequest;
import com.ootd.pickup.admin.dto.response.AdminAuctionDetailResponse;
import com.ootd.pickup.admin.dto.response.AdminAuctionListItemResponse;
import com.ootd.pickup.auction.service.AuctionService;
import com.ootd.pickup.global.auth.annotation.AdminId;
import com.ootd.pickup.global.auth.annotation.RequireAdminAuthentication;
import com.ootd.pickup.global.dto.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {

  private final AuctionService auctionService;

  @GetMapping
  @RequireAdminAuthentication
  public ResponseEntity<PageResponse<AdminAuctionListItemResponse>> searchAuctions(
      @AdminId Long adminId,
      @ModelAttribute AdminSearchAuctionsRequest request,
      Pageable pageable) {
    return ResponseEntity.ok(auctionService.searchAuctionsForAdmin(request, pageable));
  }

  @GetMapping("/{auctionId}")
  @RequireAdminAuthentication
  public ResponseEntity<AdminAuctionDetailResponse> getAuctionDetail(
      @AdminId Long adminId, @PathVariable Long auctionId) {
    return ResponseEntity.ok(auctionService.getAuctionDetailForAdmin(auctionId));
  }

  @PostMapping("/{auctionId}/cancel")
  @RequireAdminAuthentication
  public ResponseEntity<AdminAuctionDetailResponse> cancelAuction(
      @AdminId Long adminId,
      @PathVariable Long auctionId,
      @Valid @RequestBody AdminCancelAuctionRequest request) {
    return ResponseEntity.ok(auctionService.cancelAuction(adminId, auctionId, request));
  }
}
