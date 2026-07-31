package com.ootd.pickup.auction.api;

import com.ootd.pickup.auction.dto.response.WatchResponse;
import com.ootd.pickup.global.config.SwaggerConfig;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Watch", description = "관심 API")
public interface WatchApi {

  @Operation(
      summary = "관심 등록",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "관심 등록 완료",
            content =
                @Content(
                    schema = @Schema(implementation = WatchResponse.class),
                    examples =
                        @ExampleObject(
                            value =
                                """
                            {
                              "watchId": 1,
                              "memberId": 1,
                              "auctionId": 100,
                              "createdAt": "2026-07-31T12:00:00"
                            }
                            """))),
        @ApiResponse(
            responseCode = "401",
            description = "인증이 필요함",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "회원 또는 경매를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "이미 관심 등록한 경매",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<WatchResponse> registerWatch(
      @Parameter(hidden = true) Long memberId,
      @Parameter(description = "경매 ID", required = true) Long auctionId);

  @Operation(
      summary = "관심 해제",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(responseCode = "204", description = "관심 해제 완료"),
        @ApiResponse(
            responseCode = "401",
            description = "인증이 필요함",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<Void> deleteWatch(
      @Parameter(hidden = true) Long memberId,
      @Parameter(description = "경매 ID", required = true) Long auctionId);
}
