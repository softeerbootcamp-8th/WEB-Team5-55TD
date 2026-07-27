package com.ootd.pickup.cards.dto.response;

import com.ootd.pickup.cards.domain.Card;

public record SearchCardsResponse(
        Long cardId,
        String cardName,
        String setName,
        String cardNumber,
        String language,
        String rarity,
        String imageUrl
) {
    public static SearchCardsResponse from(Card card) {
        return new SearchCardsResponse(
                card.getCardId(),
                card.getCardName(),
                card.getSetName(),
                card.getCardNumber(),
                card.getLanguage().getDisplayName(),
                card.getRarity().name(),
                card.getImageUrl()
        );
    }
}
