package com.ootd.pickup.consignments.controller;

import com.ootd.pickup.consignments.api.ConsignmentApi;
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.service.ConsignmentService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consignments")
@RequiredArgsConstructor
public class ConsignmentController implements ConsignmentApi {

  private final ConsignmentService consignmentService;

  @PostMapping
  @Override
  @RequireAuthentication
  public ResponseEntity<RegisterConsignmentResponse> registerConsignment(
      @MemberId Long sellerMemberId,
      @Valid @RequestBody RegisterConsignmentRequest registerConsignmentRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(consignmentService.registerConsignment(sellerMemberId, registerConsignmentRequest));
  }

  @GetMapping("/{consignmentId}")
  @Override
  public ResponseEntity<GetConsignmentDetailResponse> getConsignment(
      @PathVariable Long consignmentId) {
    return ResponseEntity.ok(consignmentService.getConsignment(consignmentId));
  }

  @PatchMapping("/{consignmentId}")
  @Override
  @RequireAuthentication
  public ResponseEntity<GetConsignmentDetailResponse> modifyConsignment(
      @PathVariable Long consignmentId,
      @MemberId Long sellerMemberId,
      @Valid @RequestBody ModifyConsignmentRequest modifyConsignmentRequest) {
    return ResponseEntity.ok(
        consignmentService.modifyConsignment(
            consignmentId, sellerMemberId, modifyConsignmentRequest));
  }
}
