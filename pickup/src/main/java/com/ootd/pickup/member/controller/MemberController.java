package com.ootd.pickup.member.controller;

import com.ootd.pickup.member.api.MemberApi;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController implements MemberApi {

  private final MemberService memberService;

  @PostMapping
  @Override
  public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody MemberRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(memberService.createMember(request));
  }
}
