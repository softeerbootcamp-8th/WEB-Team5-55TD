package com.ootd.pickup.cards.repository;

import com.ootd.pickup.cards.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardJpaRepository extends JpaRepository<Card, Long> {}
