package com.ootd.pickup.cards.dto.response;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;

public record GetCardDetailResponse(
        Long cardId,
        String cardName,
        String setName,
        String cardNumber,
        Language language,
        String rarity,
        String imageUrl
) {
    public static GetCardDetailResponse from(Card card) {
        return new GetCardDetailResponse(
            card.getCardId(),
            card.getCardName(),
            card.getSetName(),
            card.getCardNumber(),
            card.getLanguage(),
            card.getRarity().name(),
            card.getImageUrl()
        );
    }
}
