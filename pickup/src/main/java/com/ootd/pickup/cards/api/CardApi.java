package com.ootd.pickup.cards.api;

import org.springframework.http.ResponseEntity;

import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.global.exception.dto.response.ExceptionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
}
