package com.ootd.pickup.consignments.api;

import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.GetConsignmentDetailResponse;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
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
                          "sellerMemberId": 1,
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
            responseCode = "404",
            description = "카드를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ExceptionResponse.class)))
      })
  ResponseEntity<RegisterConsignmentResponse> registerConsignment(
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
}
