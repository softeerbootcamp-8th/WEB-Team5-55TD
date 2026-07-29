package com.ootd.pickup.cards.service;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.repository.CardRepository;
import com.ootd.pickup.global.exception.PickUpException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardManageService {

  private final CardRepository cardRepository;

  public Card getCardByCardId(Long cardId) {
    return cardRepository
        .findCardById(cardId)
        .orElseThrow(() -> new PickUpException(CARD_NOT_FOUND));
  }
}
