package com.ootd.pickup.auction.api;

import com.ootd.pickup.auction.dto.response.SellerStatsResponse;
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

@Tag(name = "SellerStats", description = "셀러 대시보드 통계 API")
public interface SellerStatsApi {

  @Operation(
      summary = "셀러 대시보드 통계 조회",
      description = "로그인한 셀러의 등록 상품·경매 예정·진행 중·판매 완료 건수를 조회합니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "통계 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = SellerStatsResponse.class),
                    examples =
                        @ExampleObject(
                            name = "셀러 대시보드 통계",
                            value =
                                """
                            {
                              "registeredConsignments": 12,
                              "scheduledAuctions": 5,
                              "ongoingAuctions": 2,
                              "wonConsignments": 38
                            }
                            """))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<SellerStatsResponse> getMyStats(@Parameter(hidden = true) Long memberId);
}
