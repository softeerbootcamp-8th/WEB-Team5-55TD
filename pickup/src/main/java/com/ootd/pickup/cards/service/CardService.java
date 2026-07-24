package com.ootd.pickup.cards.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.dto.response.GetCardDetailResponse;
import com.ootd.pickup.cards.dto.response.SearchCardsResponse;
import com.ootd.pickup.cards.repository.CardRepository;
import com.ootd.pickup.global.dto.response.CursorPageResponse;
import com.ootd.pickup.global.exception.PickUpException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

    private final CardRepository cardRepository;

    public GetCardDetailResponse getCardDetail(Long cardId) {
        Card card = cardRepository.findCardById(cardId)
            .orElseThrow(() -> new PickUpException(CARD_NOT_FOUND));
        return GetCardDetailResponse.from(card);
    }

    public CursorPageResponse<SearchCardsResponse> searchCards(
            String keyword,
            String setName,
            String language,
            String cursor,
            Integer size
    ) {
        return CursorPageResponse.from(List.of(), false, null);
    }
}
