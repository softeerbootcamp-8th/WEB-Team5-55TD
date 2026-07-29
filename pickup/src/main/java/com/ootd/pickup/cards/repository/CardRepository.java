package com.ootd.pickup.cards.repository;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import java.util.List;
import java.util.Optional;

public interface CardRepository {
  Optional<Card> findCardById(Long id);

  List<Card> searchCards(String keyword, String setName, Language language, Long cursor, int size);
}
