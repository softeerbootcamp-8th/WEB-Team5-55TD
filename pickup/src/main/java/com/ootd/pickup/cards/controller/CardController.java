package com.ootd.pickup.cards.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ootd.pickup.cards.api.CardApi;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.cards.service.CardService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cards")
@RequiredArgsConstructor
public class CardController implements CardApi {

    private final CardService cardService;

    @GetMapping("/{cardId}")
    @Override
    public ResponseEntity<GetCardDetailResponse> getCardDetail(@PathVariable Long cardId) {
        return ResponseEntity.ok(cardService.getCardDetail(cardId));
    }

    @GetMapping
    public ResponseEntity<CursorPageResponse<SearchCardsResponse>> searchCards(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String setName,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String cursor,
            @RequestParam Integer size
    ) {
        return ResponseEntity.ok(cardService.searchCards(keyword, setName, language, cursor, size));
    }
}
