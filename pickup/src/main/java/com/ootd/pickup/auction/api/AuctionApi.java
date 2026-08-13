package com.ootd.pickup.auction.api;

import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.request.SearchAuctionsRequest;
import com.ootd.pickup.auction.dto.response.AuctionDetailResponse;
import com.ootd.pickup.auction.dto.response.AuctionListItemResponse;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auction", description = "경매 API")
public interface AuctionApi {

  @Operation(
      summary = "경매 신청",
      description =
          """
            위탁상품에 대한 경매 개최를 신청합니다. 신청이 접수되면 경매 상태는
            SCHEDULED(예정)로 생성되고, 위탁상품 상태는 IN_AUCTION으로 전환됩니다.
            bidIncrement(입찰 단위)는 시작가의 5%로 시스템이 결정합니다.
            """,
      requestBody =
          @RequestBody(
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = CreateAuctionRequest.class),
                      examples =
                          @ExampleObject(
                              name = "경매 신청 요청",
                              value =
                                  """
                        {
                          "consignmentId": 100,
                          "startingPrice": 10000,
                          "reserve": 15000,
                          "scheduledStartAt": "2026-08-01T21:00:00"
                        }
                        """))),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "경매 신청 완료",
            content =
                @Content(
                    schema = @Schema(implementation = CreateAuctionResponse.class),
                    examples =
                        @ExampleObject(
                            name = "경매 신청 결과",
                            value =
                                """
                            {
                              "auctionId": 1,
                              "consignmentId": 100,
                              "auctionStatus": "SCHEDULED",
                              "startingPrice": 10000,
                              "bidIncrement": 500,
                              "startedAt": "2026-08-01T21:00:00",
                              "endedAt": "2026-08-08T21:00:00",
                              "winningBidId": null,
                              "winningPrice": null,
                              "createdAt": "2026-07-29T12:00:00"
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description =
                "요청 값 검증 실패 (필수 값 누락, 시작가/최소 낙찰가 오류, 과거 일정, 제목 100자/설명"
                    + " 1000자 초과 등)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "403",
            description = "본인 소유의 위탁상품이 아님",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "위탁상품을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "이미 경매 진행/예정 중이거나 신청 불가 상태",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CreateAuctionResponse> registerAuction(
      Long memberId, CreateAuctionRequest createAuctionRequest);

  @Operation(
      summary = "경매 목록 조회",
      description =
          """
            검색어(q), 경매 상태(status), 정렬(sort) 조건으로 경매 목록을 커서 기반으로 조회합니다.
            limit이 있으면 커서/hasNext 없이 상위 N개만 반환하는 홈 노출 전용 모드로 동작합니다.
            sellerId/cardId로 같은 판매자·같은 카드의 경매만 좁혀볼 수 있고,
            excludeAuctionId로 특정 경매(예: 현재 보고 있는 상세 화면의 경매)를 결과에서 제외할 수 있습니다.
            """,
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "경매 목록 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = CursorPageResponse.class),
                    examples =
                        @ExampleObject(
                            name = "경매 목록 조회 결과",
                            value =
                                """
                            {
                              "hasNext": true,
                              "cursor": "string",
                              "size": 20,
                              "items": [
                                {
                                  "auctionId": 1,
                                  "consignmentId": 100,
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
                                  "auctionStatus": "SCHEDULED",
                                  "startingPrice": 10000,
                                  "currentPrice": null,
                                  "startedAt": "2026-08-01T10:00:00",
                                  "endedAt": null,
                                  "remainingSeconds": null,
                                  "watchCount": 0,
                                  "watched": false,
                                  "thumbnailUrl": "https://example.com/consignments/100-front.png"
                                }
                              ]
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패 (잘못된 sort/status/cursor, size/limit 오류 등)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CursorPageResponse<AuctionListItemResponse, String>> searchAuctions(
      Long memberId, SearchAuctionsRequest searchAuctionsRequest);

  @Operation(
      summary = "대표 경매 조회",
      description = "홈 화면에 노출할 대표 경매 1건을 조회합니다. 진행 중(ONGOING)인 경매 중 관심 수가 가장 많은 경매를 반환합니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "대표 경매 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = AuctionListItemResponse.class),
                    examples =
                        @ExampleObject(
                            name = "대표 경매 조회 결과",
                            value =
                                """
                            {
                              "auctionId": 1,
                              "consignmentId": 100,
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
                              "auctionStatus": "ONGOING",
                              "startingPrice": 10000,
                              "currentPrice": 12000,
                              "startedAt": "2026-08-01T10:00:00",
                              "endedAt": "2026-08-01T12:00:00",
                              "remainingSeconds": 3600,
                              "watchCount": 42,
                              "watched": false,
                              "thumbnailUrl": "https://example.com/consignments/100-front.png"
                            }
                            """))),
        @ApiResponse(
            responseCode = "404",
            description = "대표로 보여줄 진행 중인 경매가 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<AuctionListItemResponse> getFeaturedAuction(
      @Parameter(hidden = true) Long memberId);

  @Operation(
      summary = "경매 상세 조회",
      description = "경매 ID로 경매 상세 정보(카드, 인증서, 이미지, 판매자, 입찰 관련 정보 등)를 조회합니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "경매 상세 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = AuctionDetailResponse.class),
                    examples =
                        @ExampleObject(
                            name = "경매 상세 조회 결과",
                            value =
                                """
                            {
                              "auctionId": 1,
                              "consignmentId": 100,
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
                              "auctionStatus": "SCHEDULED",
                              "startingPrice": 10000,
                              "currentPrice": null,
                              "startedAt": "2026-08-01T10:00:00",
                              "endedAt": null,
                              "remainingSeconds": null,
                              "watchCount": 0,
                              "watched": false,
                              "thumbnailUrl": "https://example.com/consignments/100-front.png",
                              "sellerId": 42,
                              "sellerNickname": "카드마스터샵",
                              "sellerProfileImageUrl": "https://example.com/members/42/profile.png",
                              "certificate": {
                                "certificateId": 1,
                                "serialNumber": "PSA-84213907",
                                "certificationBody": "PSA",
                                "grade": "10",
                                "inspectedAt": "2026-06-30"
                              },
                              "images": [
                                {
                                  "consignmentImageId": 1,
                                  "imageOrder": 0,
                                  "imageUrl": "https://example.com/consignments/100-front.png"
                                }
                              ],
                              "cardState": "HIGH",
                              "majorDefect": null,
                              "bidIncrement": 500,
                              "nextMinBid": 10000,
                              "recommendedBid": null
                            }
                            """))),
        @ApiResponse(
            responseCode = "404",
            description = "경매를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<AuctionDetailResponse> getAuctionDetail(
      Long memberId, @Parameter(description = "경매 ID", required = true) Long auctionId);
}
