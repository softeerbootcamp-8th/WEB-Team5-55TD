package com.ootd.pickup.bid.api;

import com.ootd.pickup.bid.dto.request.PlaceBidRequest;
import com.ootd.pickup.bid.dto.response.PlaceBidResponse;
import com.ootd.pickup.global.config.SwaggerConfig;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Bid", description = "입찰 API")
public interface BidApi {

  @Operation(
      summary = "입찰",
      description =
          """
          진행 중인 경매에 입찰합니다.
          입찰가는 현재가보다 높고 현재가에서 최소 입찰 단위 이상 증가한 금액이어야 합니다.
          """,
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      requestBody =
          @RequestBody(
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = PlaceBidRequest.class),
                      examples =
                          @ExampleObject(
                              name = "입찰 요청",
                              value =
                                  """
                                  {
                                    "bidPrice": 10500
                                  }
                                  """))),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "입찰 성공",
            content =
                @Content(
                    schema = @Schema(implementation = PlaceBidResponse.class),
                    examples =
                        @ExampleObject(
                            name = "입찰 결과",
                            value =
                                """
                                {
                                  "bidId": 1,
                                  "auctionId": 1,
                                  "memberId": 2,
                                  "bidPrice": 10500,
                                  "bidStatus": "HIGHEST",
                                  "createdAt": "2026-07-30T12:00:00"
                                }
                                """))),
        @ApiResponse(
            responseCode = "400",
            description = "입찰가 누락 또는 양수가 아닌 입찰가",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증 필요",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "403",
            description = "판매자 본인의 경매에 입찰",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "경매를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description =
                "입찰 불가 (AUCTION_NOT_STARTED, AUCTION_ENDED, OUTBID_EXISTS, BELOW_MIN_INCREMENT)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<PlaceBidResponse> placeBid(
      Long auctionId, Long memberId, PlaceBidRequest placeBidRequest);
}
