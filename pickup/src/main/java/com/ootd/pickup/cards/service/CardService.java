package com.ootd.pickup.cards.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import org.springframework.stereotype.Service;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.repository.CardRepository;
import com.ootd.pickup.global.exception.PickUpException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;

    public GetCardDetailResponse getCardDetail(Long cardId) {
        Card card = cardRepository.findCardById(cardId)
            .orElseThrow(() -> new PickUpException(CARD_NOT_FOUND));
        return GetCardDetailResponse.from(card);
    }

}
