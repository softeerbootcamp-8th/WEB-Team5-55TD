package com.ootd.pickup.auction.api;

import com.ootd.pickup.auction.dto.request.CreateAuctionRequest;
import com.ootd.pickup.auction.dto.response.CreateAuctionResponse;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
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
            SCHEDULED(예정)로 생성되고, 위탁상품 상태는 AUCTION_SCHEDULED로 전환됩니다.
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
                          "scheduledStartAt": "2026-08-01T10:00:00"
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
                              "startedAt": "2026-08-01T10:00:00",
                              "endedAt": null,
                              "winningBidId": null,
                              "winningPrice": null,
                              "createdAt": "2026-07-29T12:00:00"
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패 (필수 값 누락, 시작가/최소 낙찰가 오류, 과거 일정 등)",
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
}
