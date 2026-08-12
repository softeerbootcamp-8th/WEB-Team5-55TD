package com.ootd.pickup.cards.repository;

import com.ootd.pickup.cards.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardJpaRepository extends JpaRepository<Card, Long> {
  java.util.Optional<Card> findByTcgdexId(String tcgdexId);

  java.util.List<Card> findAllByTcgdexSetId(String tcgdexSetId);

  long countByTcgdexSetId(String tcgdexSetId);
}
