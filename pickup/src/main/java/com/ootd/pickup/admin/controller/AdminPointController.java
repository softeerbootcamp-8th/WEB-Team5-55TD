package com.ootd.pickup.admin.controller;

import com.ootd.pickup.admin.dto.request.AdminGrantPointRequest;
import com.ootd.pickup.admin.dto.response.AdminPointGrantResponse;
import com.ootd.pickup.admin.dto.response.AdminPointGrantResultResponse;
import com.ootd.pickup.global.auth.annotation.AdminId;
import com.ootd.pickup.global.auth.annotation.RequireAdminAuthentication;
import com.ootd.pickup.global.dto.response.PageResponse;
import com.ootd.pickup.point.service.PointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/members/{memberId}/points")
@RequiredArgsConstructor
public class AdminPointController {

  private final PointService pointService;

  @PostMapping("/grants")
  @RequireAdminAuthentication
  public ResponseEntity<AdminPointGrantResultResponse> grantPoint(
      @AdminId Long adminId,
      @PathVariable Long memberId,
      @Valid @RequestBody AdminGrantPointRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(pointService.grantPoint(adminId, memberId, request));
  }

  @GetMapping("/grants")
  @RequireAdminAuthentication
  public ResponseEntity<PageResponse<AdminPointGrantResponse>> getGrantHistory(
      @AdminId Long adminId, @PathVariable Long memberId, Pageable pageable) {
    return ResponseEntity.ok(pointService.getGrantHistory(memberId, pageable));
  }
}
