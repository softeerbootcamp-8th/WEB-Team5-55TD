package com.ootd.pickup.auction.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 경매 목록 검색 조건.
 *
 * <p>{@code status}는 여러 개를 지정할 수 있고 서로 OR로 합쳐진다. 화면의 상태 탭이 "단일 선택"인 것은 UI 개념이고, 종료 탭 하나가 {@code
 * WON}·{@code PASSED} 두 상태를 함께 보내는 것처럼 API는 처음부터 다중 상태 필터다. 상태 종류가 넷뿐이라 그보다 많이 보내는 요청은 의미가 없어 개수만
 * 막는다.
 */
public record SearchAuctionsRequest(
    String q,
    @Size(max = 4, message = "상태 필터는 최대 4개까지 지정할 수 있습니다.") List<String> status,
    String sort,
    Integer limit,
    String cursor,
    Integer size,
    Long sellerId,
    Long cardId,
    Long excludeAuctionId) {}
