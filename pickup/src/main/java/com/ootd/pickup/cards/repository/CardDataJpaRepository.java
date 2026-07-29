package com.ootd.pickup.cards.repository;

import static com.ootd.pickup.cards.domain.QCard.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.domain.Language;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class CardDataJpaRepository implements CardRepository {
  private final CardJpaRepository cardJpaRepository;
  private final JPAQueryFactory queryFactory;

  @Override
  public Optional<Card> findCardById(Long cardId) {
    return cardJpaRepository.findById(cardId);
  }

  @Override
  public List<Card> searchCards(
      String keyword, String setName, Language language, Long cursor, int size) {
    return queryFactory
        .selectFrom(card)
        .where(
            cardNameContains(keyword), setNameEq(setName), languageEq(language), cardIdLt(cursor))
        .orderBy(card.cardId.desc())
        .limit(size)
        .fetch();
  }

  private BooleanExpression cardNameContains(String keyword) {
    if (!StringUtils.hasText(keyword)) {
      return null;
    }

    return card.cardName.contains(keyword);
  }

  private BooleanExpression setNameEq(String setName) {
    if (!StringUtils.hasText(setName)) {
      return null;
    }

    return card.setName.eq(setName);
  }

  private BooleanExpression languageEq(Language language) {
    if (language == null) {
      return null;
    }

    return card.language.eq(language);
  }

  private BooleanExpression cardIdLt(Long cursor) {
    if (cursor == null) {
      return null;
    }

    return card.cardId.lt(cursor);
  }
}
