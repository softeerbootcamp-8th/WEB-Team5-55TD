package com.ootd.pickup.cards.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ootd.pickup.cards.domain.Card;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CardDataJpaRepository implements CardRepository {
    private final CardJpaRepository cardJpaRepository;

    @Override
    public Optional<Card> findCardById(Long cardId) {
        return cardJpaRepository.findCardByCardId(cardId);
    }

}
