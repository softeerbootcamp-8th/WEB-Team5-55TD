package com.ootd.pickup.consignments.api;

import com.ootd.pickup.consignments.dto.request.ModifyConsignmentRequest;
import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.global.config.SwaggerConfig;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Consignment", description = "상품 등록 API")
public interface ConsignmentApi {

  @Operation(
      summary = "상품 등록",
      description =
          """
            카드 검색으로 선택한 카드와 감정서, 이미지(앞/뒤 최소 2장) 정보를 받아
            상품을 등록합니다. 이미지 순서는 요청 배열에 담긴 순서를 그대로 사용합니다.
            등록 직후 상태는 REGISTERABLE(등록 가능)로 설정됩니다.
            """,
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      requestBody =
          @RequestBody(
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = RegisterConsignmentRequest.class),
                      examples =
                          @ExampleObject(
                              name = "상품 등록 요청",
                              value =
                                  """
                        {
                          "cardId": 10,
                          "majorDefect": "모서리에 약간의 마모",
                          "certificate": {
                            "serialNumber": "PSA-84213907",
                            "certificationBody": "PSA",
                            "grade": "10",
                            "inspectedAt": "2026-06-30"
                          },
                          "images": [
                            { "imageUrl": "https://example.com/cards/10-front.png" },
                            { "imageUrl": "https://example.com/cards/10-back.png" }
                          ]
                        }
                        """))),
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "상품 등록 성공",
            content =
                @Content(
                    schema = @Schema(implementation = RegisterConsignmentResponse.class),
                    examples =
                        @ExampleObject(
                            name = "상품 등록 결과",
                            value =
                                """
                            {
                              "consignmentId": 100,
                              "card": {
                                "cardId": 10,
                                "cardName": "리자몽 1st Edition Holo",
                                "setName": "Base Set",
                                "cardNumber": "4/102",
                                "language": "일본어",
                                "rarity": "MINT",
                                "imageUrl": "https://example.com/cards/10.png"
                              },
                              "sellerMemberId": 1,
                              "majorDefect": "모서리에 약간의 마모",
                              "status": "REGISTERABLE",
                              "certificate": {
                                "certificateId": 200,
                                "serialNumber": "PSA-84213907",
                                "certificationBody": "PSA",
                                "grade": "10",
                                "gradeCode": "GEM_MINT",
                                "inspectedAt": "2026-06-30"
                              }
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패 (필수 값 누락, 이미지 2장 미만, 유효하지 않은 등급/감정기관 등)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증이 필요함 (access-token 쿠키 없음/만료)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "카드 또는 회원을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "이미 등록된 인증서 일련번호",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<RegisterConsignmentResponse> registerConsignment(
      @Parameter(hidden = true) Long sellerMemberId,
      RegisterConsignmentRequest registerConsignmentRequest);

  @Operation(
      summary = "상품 상세 조회",
      description = "상품 ID로 상품 상세 정보를 조회합니다.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "상품 상세 조회 성공",
            content =
                @Content(
                    schema = @Schema(implementation = GetConsignmentDetailResponse.class),
                    examples =
                        @ExampleObject(
                            name = "상품 상세 조회 결과",
                            value =
                                """
                            {
                              "consignmentId": 100,
                              "card": {
                                "cardId": 10,
                                "cardName": "리자몽 1st Edition Holo",
                                "setName": "Base Set",
                                "cardNumber": "4/102",
                                "language": "일본어",
                                "rarity": "MINT",
                                "imageUrl": "https://example.com/cards/10.png"
                              },
                              "sellerMemberNickname": "피카츄",
                              "majorDefect": "모서리에 약간의 마모",
                              "status": "REGISTERABLE",
                              "certificate": {
                                "certificateId": 200,
                                "serialNumber": "PSA-84213907",
                                "certificationBody": "PSA",
                                "grade": "10",
                                "gradeCode": "GEM_MINT",
                                "inspectedAt": "2026-06-30"
                              },
                              "images": [
                                { "productImageId": 1, "imageOrder": 1, "imageUrl": "https://example.com/cards/10-front.png" },
                                { "productImageId": 2, "imageOrder": 2, "imageUrl": "https://example.com/cards/10-back.png" }
                              ],
                              "auctionRegistered": false
                            }
                            """))),
        @ApiResponse(
            responseCode = "404",
            description = "상품을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<GetConsignmentDetailResponse> getConsignment(
      @Parameter(description = "상품 ID", required = true) Long consignmentId);

  @Operation(
      summary = "상품 정보 수정",
      description =
          """
            상품의 major defect, 감정서, 이미지 목록(전체 교체)을 수정합니다.
            경매 신청 이후(AUCTION_SCHEDULED)/진행 중(AUCTION_ONGOING)/낙찰 완료(WON) 상태에서는 수정할 수 없습니다.
            """,
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      requestBody =
          @RequestBody(
              required = true,
              content =
                  @Content(
                      schema = @Schema(implementation = ModifyConsignmentRequest.class),
                      examples =
                          @ExampleObject(
                              name = "상품 정보 수정 요청",
                              value =
                                  """
                        {
                          "majorDefect": "모서리에 약간의 마모",
                          "certificate": {
                            "serialNumber": "PSA-84213907",
                            "certificationBody": "PSA",
                            "grade": "10",
                            "inspectedAt": "2026-06-30"
                          },
                          "images": [
                            { "imageUrl": "https://example.com/cards/10-front.png" },
                            { "imageUrl": "https://example.com/cards/10-back.png" }
                          ]
                        }
                        """))),
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "상품 정보 수정 성공",
            content =
                @Content(
                    schema = @Schema(implementation = GetConsignmentDetailResponse.class),
                    examples =
                        @ExampleObject(
                            name = "상품 정보 수정 결과",
                            value =
                                """
                            {
                              "consignmentId": 100,
                              "card": {
                                "cardId": 10,
                                "cardName": "리자몽 1st Edition Holo",
                                "setName": "Base Set",
                                "cardNumber": "4/102",
                                "language": "일본어",
                                "rarity": "MINT",
                                "imageUrl": "https://example.com/cards/10.png"
                              },
                              "sellerMemberNickname": "피카츄",
                              "majorDefect": "모서리에 약간의 마모",
                              "status": "REGISTERABLE",
                              "certificate": {
                                "certificateId": 201,
                                "serialNumber": "PSA-84213907",
                                "certificationBody": "PSA",
                                "grade": "10",
                                "gradeCode": "GEM_MINT",
                                "inspectedAt": "2026-06-30"
                              },
                              "images": [
                                { "productImageId": 3, "imageOrder": 1, "imageUrl": "https://example.com/cards/10-front.png" },
                                { "productImageId": 4, "imageOrder": 2, "imageUrl": "https://example.com/cards/10-back.png" }
                              ],
                              "auctionRegistered": false
                            }
                            """))),
        @ApiResponse(
            responseCode = "400",
            description = "요청 값 검증 실패 (이미지 2장 미만, 유효하지 않은 등급/감정기관 등)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "401",
            description = "인증이 필요함 (access-token 쿠키 없음/만료)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "403",
            description = "본인이 등록한 상품이 아님",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "상품을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "경매 신청 이후/진행 중인 상품이라 수정할 수 없거나, 다른 상품이 이미 사용 중인 인증서 일련번호",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<GetConsignmentDetailResponse> modifyConsignment(
      @Parameter(description = "상품 ID", required = true) Long consignmentId,
      @Parameter(hidden = true) Long sellerMemberId,
      ModifyConsignmentRequest modifyConsignmentRequest);

  @Operation(
      summary = "상품 삭제",
      description =
          "상품을 삭제합니다. 경매가 시작된 이후(AUCTION_SCHEDULED/AUCTION_ONGOING/WON) 상태의 상품은 삭제할 수 없습니다.",
      security = @SecurityRequirement(name = SwaggerConfig.ACCESS_TOKEN_SECURITY_SCHEME),
      responses = {
        @ApiResponse(responseCode = "204", description = "상품 삭제 성공"),
        @ApiResponse(
            responseCode = "401",
            description = "인증이 필요함 (access-token 쿠키 없음/만료)",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "403",
            description = "본인이 등록한 상품이 아님",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "404",
            description = "상품을 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))),
        @ApiResponse(
            responseCode = "409",
            description = "경매가 시작된 이후 상태라 삭제할 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<Void> deleteConsignment(
      @Parameter(description = "상품 ID", required = true) Long consignmentId,
      @Parameter(hidden = true) Long sellerMemberId);
}
