package com.ootd.pickup.admin.controller;

import com.ootd.pickup.admin.dto.request.AdminBlockConsignmentRequest;
import com.ootd.pickup.admin.dto.request.AdminSearchConsignmentsRequest;
import com.ootd.pickup.admin.dto.response.AdminConsignmentDetailResponse;
import com.ootd.pickup.admin.dto.response.AdminConsignmentListItemResponse;
import com.ootd.pickup.consignments.service.ConsignmentService;
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
@RequestMapping("/admin/consignments")
@RequiredArgsConstructor
public class AdminConsignmentController {

  private final ConsignmentService consignmentService;

  @GetMapping
  @RequireAdminAuthentication
  public ResponseEntity<PageResponse<AdminConsignmentListItemResponse>> searchConsignments(
      @AdminId Long adminId,
      @ModelAttribute AdminSearchConsignmentsRequest request,
      Pageable pageable) {
    return ResponseEntity.ok(consignmentService.searchConsignmentsForAdmin(request, pageable));
  }

  @GetMapping("/{consignmentId}")
  @RequireAdminAuthentication
  public ResponseEntity<AdminConsignmentDetailResponse> getConsignmentDetail(
      @AdminId Long adminId, @PathVariable Long consignmentId) {
    return ResponseEntity.ok(consignmentService.getConsignmentDetailForAdmin(consignmentId));
  }

  @PostMapping("/{consignmentId}/block")
  @RequireAdminAuthentication
  public ResponseEntity<AdminConsignmentDetailResponse> blockConsignment(
      @AdminId Long adminId,
      @PathVariable Long consignmentId,
      @Valid @RequestBody AdminBlockConsignmentRequest request) {
    return ResponseEntity.ok(consignmentService.blockConsignment(adminId, consignmentId, request));
  }

  @PostMapping("/{consignmentId}/unblock")
  @RequireAdminAuthentication
  public ResponseEntity<AdminConsignmentDetailResponse> unblockConsignment(
      @AdminId Long adminId, @PathVariable Long consignmentId) {
    return ResponseEntity.ok(consignmentService.unblockConsignment(adminId, consignmentId));
  }
}
