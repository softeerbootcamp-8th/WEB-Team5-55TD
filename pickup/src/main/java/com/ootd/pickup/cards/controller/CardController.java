package com.ootd.pickup.cards.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ootd.pickup.cards.api.CardApi;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.service.CardService;

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

}
