package com.ootd.pickup.member.api;

import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Member", description = "회원 API")
public interface MemberApi {

  @Operation(
      summary = "회원가입",
      description = "로그인 ID, 닉네임, 비밀번호로 회원을 생성합니다.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "회원가입 성공",
            content = @Content(schema = @Schema(implementation = MemberResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "로그인 ID 또는 닉네임 중복",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<MemberResponse> createMember(MemberRequest request);
}
