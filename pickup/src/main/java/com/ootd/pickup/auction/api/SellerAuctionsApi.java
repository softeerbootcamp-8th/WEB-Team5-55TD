package com.ootd.pickup.auction.api;

import com.ootd.pickup.auction.dto.request.GetMyAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
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

@Tag(name = "SellerAuctions", description = "셀러 경매 API")
public interface SellerAuctionsApi {

  @Operation(
      summary = "종료되지 않은 경매 목록 조회",
      description =
          """
            로그인한 셀러가 등록한 경매 중 아직 종료되지 않은(SCHEDULED/ONGOING) 경매를
            커서 기반으로 조회합니다. status가 없으면 SCHEDULED/ONGOING을 모두 포함합니다.
            """,
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "경매 목록 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = CursorPageResponse.class),
                    examples =
                        @ExampleObject(
                            name = "경매 목록",
                            value =
                                """
                            {
                              "hasNext": false,
                              "cursor": null,
                              "size": 1,
                              "items": [
                                {
                                  "auctionId": 1,
                                  "consignmentId": 1,
                                  "card": {
                                    "cardId": 10,
                                    "cardName": "리자몽 1st Edition Holo",
                                    "setName": "Base Set",
                                    "cardNumber": "4/102",
                                    "language": "일본어",
                                    "rarity": "MINT",
                                    "imageUrl": "https://example.com/cards/10.png"
                                  },
                                  "grade": "PSA 10",
                                  "auctionStatus": "ONGOING",
                                  "startingPrice": 10000,
                                  "currentPrice": null,
                                  "startedAt": "2026-08-01T10:00:00",
                                  "endedAt": "2026-08-03T10:00:00",
                                  "remainingSeconds": 3600,
                                  "watchCount": 3,
                                  "watched": false,
                                  "thumbnailUrl": "https://example.com/thumb.png"
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
  ResponseEntity<CursorPageResponse<AuctionListItemResponse, String>> getMyAuctions(
      @Parameter(hidden = true) Long memberId, GetMyAuctionsRequest request);
}
