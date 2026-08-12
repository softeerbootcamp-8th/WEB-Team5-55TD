package com.ootd.pickup.auction.api;

import com.ootd.pickup.auction.dto.request.GetSalesHistoryRequest;
import com.ootd.pickup.auction.dto.response.SaleHistoryItemResponse;
import com.ootd.pickup.global.config.SwaggerConfig;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
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

@Tag(name = "Sales", description = "판매 내역 API")
public interface SalesApi {

  @Operation(
      summary = "판매 내역 조회",
      description =
          """
            로그인한 판매자가 등록한 경매 중 종료(낙찰 WON/유찰 PASSED)된 판매 내역을
            종료 시각 최신순으로 커서 기반 조회합니다. status가 없으면 WON/PASSED를 모두 포함합니다.
            """,
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "판매 내역 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = CursorPageResponse.class),
                    examples =
                        @ExampleObject(
                            name = "판매 내역 조회 결과",
                            value =
                                """
                            {
                              "hasNext": true,
                              "cursor": "string",
                              "size": 20,
                              "items": [
                                {
                                  "auctionId": 1,
                                  "card": {
                                    "cardId": 10,
                                    "cardName": "리자몽 1st Edition Holo",
                                    "setName": "Base Set",
                                    "cardNumber": "4/102",
                                    "language": "일본어",
                                    "rarity": "레어 홀로",
                                    "imageUrl": "https://example.com/cards/10.png"
                                  },
                                  "grade": "PSA 10",
                                  "winningPrice": 12000,
                                  "resultType": "WON"
                                }
                              ]
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패 (잘못된 status/cursor/size)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CursorPageResponse<SaleHistoryItemResponse, String>> getMySalesHistory(
      @Parameter(hidden = true) Long memberId, GetSalesHistoryRequest request);
}
