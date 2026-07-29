package com.ootd.pickup.member.api;

import com.ootd.pickup.global.config.SwaggerConfig;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import com.ootd.pickup.member.dto.MemberRequest;
import com.ootd.pickup.member.dto.MemberResponse;
import com.ootd.pickup.member.dto.MyProfileResponse;
import com.ootd.pickup.member.dto.PointBalanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

  @Operation(
      summary = "내 정보 조회",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "내 정보 조회 성공",
            content = @Content(schema = @Schema(implementation = MyProfileResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<MyProfileResponse> getMyProfile(@Parameter(hidden = true) Long memberId);

  @Operation(
      summary = "내 포인트 잔액 조회",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "포인트 잔액 조회 성공",
            content = @Content(schema = @Schema(implementation = PointBalanceResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<PointBalanceResponse> getMyPointBalance(@Parameter(hidden = true) Long memberId);
}
