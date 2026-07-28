package com.ootd.pickup.cards.repository;

import java.util.List;
import java.util.Optional;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;

public interface CardRepository {
    Optional<Card> findCardById(Long id);

    List<Card> searchCards(
        String keyword,
        String setName,
        Language language,
        Long cursor,
        int size
    );
}
