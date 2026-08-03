package com.ootd.pickup.auction.controller;

import com.ootd.pickup.auction.api.SalesApi;
import com.ootd.pickup.auction.dto.request.GetSalesHistoryRequest;
import com.ootd.pickup.auction.dto.response.SaleHistoryItemResponse;
import com.ootd.pickup.auction.service.SalesService;
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
@RequestMapping("/sellers/me/sales")
@RequiredArgsConstructor
public class SalesController implements SalesApi {

  private final SalesService salesService;

  @GetMapping
  @RequireAuthentication
  @Override
  public ResponseEntity<CursorPageResponse<SaleHistoryItemResponse, String>> getMySalesHistory(
      @MemberId Long memberId, @Valid @ModelAttribute GetSalesHistoryRequest request) {
    return ResponseEntity.ok(salesService.getSalesHistory(memberId, request));
  }
}
