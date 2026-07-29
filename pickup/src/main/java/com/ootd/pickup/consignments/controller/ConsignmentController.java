package com.ootd.pickup.consignments.controller;

import com.ootd.pickup.consignments.api.ConsignmentApi;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.service.ConsignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

  // TODO: 인증 구현 이후 sellerMemberId를 요청 바디가 아니라 인증 컨텍스트에서 추출하도록 변경
  @PostMapping
  @Override
  public ResponseEntity<RegisterConsignmentResponse> registerConsignment(
      @Valid @RequestBody RegisterConsignmentRequest registerConsignmentRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            consignmentService.registerConsignment(
                registerConsignmentRequest.sellerMemberId(), registerConsignmentRequest));
  }

  @GetMapping("/{consignmentId}")
  @Override
  public ResponseEntity<GetConsignmentDetailResponse> getConsignment(
      @PathVariable Long consignmentId) {
    return ResponseEntity.ok(consignmentService.getConsignment(consignmentId));
  }
}
