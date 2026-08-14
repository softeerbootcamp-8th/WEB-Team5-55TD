package com.ootd.pickup.auth.api;

import com.ootd.pickup.auth.dto.KakaoLoginRequest;
import com.ootd.pickup.auth.dto.LoginRequest;
import com.ootd.pickup.auth.dto.LoginResponseBody;
import com.ootd.pickup.auth.dto.RefreshResponseBody;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Authentication", description = "로그인 및 토큰 관리 API")
public interface AuthApi {

  @Operation(
      summary = "로그인",
      description = "아이디와 비밀번호를 검증하고 Access Token과 Refresh Token을 HttpOnly 쿠키로 발급합니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            headers =
                @Header(
                    name = "Set-Cookie",
                    description = "access-token과 refresh-token 쿠키를 발급합니다.",
                    schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = LoginResponseBody.class))),
        @ApiResponse(
            responseCode = "400",
            description = "아이디 또는 비밀번호 형식 오류",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "아이디 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<LoginResponseBody> login(LoginRequest loginRequest);

  @Operation(
      summary = "카카오 로그인",
      description =
          "카카오 인가 코드를 검증하고 최초 로그인 시 랜덤 닉네임으로 자동 가입한 뒤 서비스 토큰 쿠키를 발급합니다. "
              + "최초 가입 회원은 응답의 needsNickname 이 true 입니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "카카오 로그인 성공",
            headers =
                @Header(
                    name = "Set-Cookie",
                    description = "access-token과 refresh-token 쿠키를 발급합니다.",
                    schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = LoginResponseBody.class))),
        @ApiResponse(
            responseCode = "401",
            description = "카카오 인가 코드 검증 실패",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<LoginResponseBody> kakaoLogin(KakaoLoginRequest request);

  @Operation(
      summary = "토큰 갱신",
      description = "refresh-token 쿠키를 검증하고 기존 토큰을 폐기한 뒤 새 Access Token과 Refresh Token을 발급합니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "토큰 갱신 성공",
            headers =
                @Header(
                    name = "Set-Cookie",
                    description = "새 access-token과 refresh-token 쿠키를 발급합니다.",
                    schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = RefreshResponseBody.class))),
        @ApiResponse(
            responseCode = "401",
            description = "Refresh Token이 없거나 유효하지 않음, 또는 토큰 저장소 장애",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<RefreshResponseBody> refresh(String refreshToken);

  @Operation(
      summary = "로그아웃",
      description = "refresh-token 쿠키를 폐기하고 Access Token과 Refresh Token 쿠키를 만료합니다.",
      responses =
          @ApiResponse(
              responseCode = "204",
              description = "로그아웃 성공",
              headers =
                  @Header(
                      name = "Set-Cookie",
                      description = "access-token과 refresh-token 쿠키를 만료합니다.",
                      schema = @Schema(type = "string"))))
  ResponseEntity<Void> logout(String refreshToken);
}
