package com.ootd.pickup.cards.api;

import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;

import com.ootd.pickup.cards.dto.request.SearchCardsRequest;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Card", description = "카드 API")
public interface CardApi {

    @Operation(
            summary = "카드 상세 조회",
            description = "카드 ID로 카드 상세 정보를 조회합니다.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "카드 상세 조회 성공",
                            content = @Content(schema = @Schema(implementation = GetCardDetailResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "카드를 찾을 수 없음",
                            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))
                    )
            }
    )
    ResponseEntity<GetCardDetailResponse> getCardDetail(
            @Parameter(description = "카드 ID", required = true) Long cardId
    );

    @Operation(
            summary = "카드 검색",
            description = """
                    카드명, 세트명, 언어 조건으로 카드를 검색합니다.
                    검색 조건은 모두 선택 사항이며, 카드 ID 내림차순으로 조회합니다.
                    다음 페이지 조회 시 이전 응답의 cursor를 전달합니다.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "카드 검색 성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = CursorPageResponse.class),
                                    examples = @ExampleObject(
                                            name = "카드 검색 결과",
                                            value = """
                                                    {
                                                      "hasNext": true,
                                                      "cursor": 9,
                                                      "size": 1,
                                                      "items": [
                                                        {
                                                          "cardId": 9,
                                                          "cardName": "리자몽 1st Edition Holo",
                                                          "setName": "Base Set",
                                                          "cardNumber": "4/102",
                                                          "language": "한국어",
                                                          "rarity": "MINT",
                                                          "imageUrl": "https://example.com/cards/9.png"
                                                        }
                                                      ]
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "잘못된 검색 조건",
                            content = @Content(schema = @Schema(implementation = ExceptionResponse.class))
                    )
            }
    )
    ResponseEntity<CursorPageResponse<SearchCardsResponse, Long>> searchCards(
            @ParameterObject SearchCardsRequest searchCardsRequest
    );
}
