package com.ootd.pickup.cards.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ootd.pickup.cards.api.CardApi;
import com.ootd.pickup.cards.dto.request.SearchCardsRequest;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.cards.service.CardService;
import com.ootd.pickup.global.dto.response.CursorPageResponse;

import jakarta.validation.Valid;
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
            @Valid @ModelAttribute SearchCardsRequest searchCardsRequest
    ) {
        return ResponseEntity.ok(cardService.searchCards(searchCardsRequest));
    }
}
