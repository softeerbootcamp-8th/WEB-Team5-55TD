package com.ootd.pickup.cards.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.ootd.pickup.cards.dto.request.SearchCardsRequest;
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

    public CursorPageResponse<SearchCardsResponse, Long> searchCards(SearchCardsRequest searchCardsRequest) {
        validateSize(searchCardsRequest.size());

        List<Card> searchedCards = cardRepository.searchCards(
            searchCardsRequest.keyword(),
            searchCardsRequest.setName(),
            Language.from(searchCardsRequest.language()),
            searchCardsRequest.cursor(),
            searchCardsRequest.size() + 1
        );

        boolean hasNext = searchedCards.size() > searchCardsRequest.size();
        List<Card> cards = hasNext ? searchedCards.subList(0, searchCardsRequest.size()) : searchedCards;
        Long nextCursor = hasNext ? cards.getLast().getCardId() : null;

        List<SearchCardsResponse> items = cards.stream()
            .map(SearchCardsResponse::from)
            .toList();

        return CursorPageResponse.from(items, hasNext, nextCursor);
    }

    private void validateSize(Integer size) {
        if (size == null || size < 1) {
            throw new PickUpException(ILLEGAL_ARGUMENT);
        }
    }
}
