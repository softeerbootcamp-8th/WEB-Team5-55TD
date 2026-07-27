package com.ootd.pickup.cards.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ootd.pickup.cards.domain.Card;

public interface CardJpaRepository extends JpaRepository<Card, Long> {
    Optional<Card> findCardByCardId(Long cardId);
}
