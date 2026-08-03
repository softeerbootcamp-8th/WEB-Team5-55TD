package com.ootd.pickup.consignments.controller;

import com.ootd.pickup.consignments.api.ConsignmentApi;
import com.ootd.pickup.consignments.dto.request.GetMyConsignmentsRequest;
import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.GetMyConsignmentsResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.service.ConsignmentService;
import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

  @GetMapping
  @Override
  @RequireAuthentication
  public ResponseEntity<CursorPageResponse<GetMyConsignmentsResponse, Long>> getMyConsignments(
      @MemberId Long sellerMemberId,
      @Valid @ModelAttribute GetMyConsignmentsRequest getMyConsignmentsRequest) {
    return ResponseEntity.ok(
        consignmentService.getMyConsignments(sellerMemberId, getMyConsignmentsRequest));
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

  @DeleteMapping("/{consignmentId}")
  @Override
  @RequireAuthentication
  public ResponseEntity<Void> deleteConsignment(
      @PathVariable Long consignmentId, @MemberId Long sellerMemberId) {
    consignmentService.deleteConsignment(consignmentId, sellerMemberId);
    return ResponseEntity.noContent().build();
  }
}
