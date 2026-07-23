package com.ootd.pickup.cards.repository;

import java.util.Optional;

import com.ootd.pickup.cards.domain.Card;

public interface CardRepository {
    public Optional<Card> findCardById(Long Id);
}
