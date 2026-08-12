package com.ootd.pickup.bid.api;

import com.ootd.pickup.bid.dto.request.CreateBidRequestRequest;
import com.ootd.pickup.bid.dto.response.CreateBidRequestResponse;
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
public interface BidRequestApi {

  @Operation(
      summary = "입찰 요청 생성",
      description =
          """
          입찰 요청을 접수합니다. 이 API는 가벼운 검증(경매 존재 여부, 입찰가 형식)만 하고 즉시 202를 반환합니다.
          실제 입찰 처리(현재가·최소 증가폭·판매자 본인 여부·포인트 한도 검증, 저장)는 비동기로 수행되며,
          같은 경매의 요청은 접수된 순서대로 처리됩니다.
          처리 결과는 REST 응답이 아니라 WebSocket으로 전달됩니다 —
          성공은 `/topic/auctions/{auctionId}` 브로드캐스트, 실패는 `/user/queue/bid-requests` 유니캐스트입니다.
          """,
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      requestBody =
          @RequestBody(
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = CreateBidRequestRequest.class),
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
            responseCode = "202",
            description = "입찰 요청 접수 성공",
            content =
                @Content(
                    schema = @Schema(implementation = CreateBidRequestResponse.class),
                    examples =
                        @ExampleObject(
                            name = "입찰 요청 접수 결과",
                            value =
                                """
                                {
                                  "bidRequestId": 1,
                                  "auctionId": 1,
                                  "memberId": 2,
                                  "bidPrice": 10500,
                                  "status": "PENDING",
                                  "createdAt": "2026-08-11T12:00:00"
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
            responseCode = "404",
            description = "경매를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<CreateBidRequestResponse> createBidRequest(
      Long auctionId, Long memberId, CreateBidRequestRequest createBidRequestRequest);
}
