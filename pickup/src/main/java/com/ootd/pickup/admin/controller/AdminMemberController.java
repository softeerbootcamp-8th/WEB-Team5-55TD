package com.ootd.pickup.admin.controller;

import com.ootd.pickup.admin.dto.response.AdminMemberDetailResponse;
import com.ootd.pickup.admin.dto.response.AdminMemberListItemResponse;
import com.ootd.pickup.global.auth.annotation.AdminId;
import com.ootd.pickup.global.auth.annotation.RequireAdminAuthentication;
import com.ootd.pickup.global.dto.response.PageResponse;
import com.ootd.pickup.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

  private final MemberService memberService;

  @GetMapping
  @RequireAdminAuthentication
  public ResponseEntity<PageResponse<AdminMemberListItemResponse>> searchMembers(
      @AdminId Long adminId, @RequestParam(required = false) String q, Pageable pageable) {
    return ResponseEntity.ok(memberService.searchMembersForAdmin(q, pageable));
  }

  @GetMapping("/{memberId}")
  @RequireAdminAuthentication
  public ResponseEntity<AdminMemberDetailResponse> getMemberDetail(
      @AdminId Long adminId, @PathVariable Long memberId) {
    return ResponseEntity.ok(memberService.getMemberDetailForAdmin(memberId));
  }
}
