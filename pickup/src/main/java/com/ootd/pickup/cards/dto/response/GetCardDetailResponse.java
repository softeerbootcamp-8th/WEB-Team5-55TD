package com.ootd.pickup.cards.dto.response;

import com.ootd.pickup.cards.domain.Language;

public record GetCardDetailResponse(
        int cardId,
        String cardName,
        String setName,
        String cardNumber,
        Language language,
        String rarity,
        String imageUrl
) {
}
