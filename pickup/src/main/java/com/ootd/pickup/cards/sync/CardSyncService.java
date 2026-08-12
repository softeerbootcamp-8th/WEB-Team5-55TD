package com.ootd.pickup.cards.sync;

import com.ootd.pickup.cards.domain.Card;
import com.ootd.pickup.cards.repository.CardJpaRepository;
import com.ootd.pickup.cards.sync.client.TcgdexCardDetail;
import com.ootd.pickup.cards.sync.client.TcgdexClient;
import com.ootd.pickup.cards.sync.client.TcgdexSetDetail;
import com.ootd.pickup.cards.sync.client.TcgdexSetSummary;
import com.ootd.pickup.cards.sync.domain.CardSetSync;
import com.ootd.pickup.cards.sync.domain.CardSetSyncStatus;
import com.ootd.pickup.cards.sync.repository.CardSetSyncRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardSyncService {

  private final TcgdexClient tcgdexClient;
  private final CardSetSyncRepository cardSetSyncRepository;
  private final CardJpaRepository cardJpaRepository;
  private final CardSyncProperties properties;

  public void synchronizeCards() {
    List<TcgdexSetSummary> sets = tcgdexClient.findAllSets();
    Map<String, CardSetSync> savedSets = new HashMap<>();
    cardSetSyncRepository.findAll().forEach(set -> savedSets.put(set.getTcgdexSetId(), set));

    sets.stream()
        .filter(this::isValid)
        .filter(summary -> isSyncTarget(summary, savedSets.get(summary.id())))
        .sorted(Comparator.comparingInt(summary -> priority(savedSets.get(summary.id()))))
        .limit(properties.maxSetsPerRun())
        .forEach(summary -> synchronizeSet(summary, savedSets.get(summary.id())));
  }

  private boolean isSyncTarget(TcgdexSetSummary summary, CardSetSync saved) {
    if (saved == null || saved.getStatus() != CardSetSyncStatus.COMPLETE) {
      return true;
    }
    if (!saved.getSetName().equals(summary.name())
        || saved.getExpectedCardCount() != summary.cardCount().total()) {
      return true;
    }
    return saved.getReleaseDate() != null
        && !saved.getReleaseDate().isBefore(LocalDate.now().minusDays(properties.recentDays()));
  }

  private int priority(CardSetSync saved) {
    if (saved != null && saved.getStatus() == CardSetSyncStatus.PARTIAL) {
      return 0;
    }
    return saved == null ? 1 : 2;
  }

  private void synchronizeSet(TcgdexSetSummary summary, CardSetSync saved) {
    CardSetSync sync =
        saved == null
            ? new CardSetSync(summary.id(), summary.name(), summary.cardCount().total())
            : saved;
    try {
      TcgdexSetDetail detail = Objects.requireNonNull(tcgdexClient.getSet(summary.id()));
      List<TcgdexSetDetail.CardBrief> cards = detail.cards() == null ? List.of() : detail.cards();
      int expectedCardCount =
          detail.cardCount() == null ? cards.size() : detail.cardCount().total();
      sync.start(detail.name(), expectedCardCount, detail.releaseDate());
      cardSetSyncRepository.save(sync);

      Map<String, Card> savedCards = new HashMap<>();
      cardJpaRepository
          .findAllByTcgdexSetId(detail.id())
          .forEach(card -> savedCards.put(card.getTcgdexId(), card));

      int failures = 0;
      for (TcgdexSetDetail.CardBrief brief : cards) {
        Card card = savedCards.get(brief.id());
        if (card != null && isUnchanged(card, brief, detail.name())) {
          continue;
        }
        try {
          upsertCard(detail, brief, card);
        } catch (RuntimeException exception) {
          failures++;
          log.warn(
              "카드 동기화 재시도 예정 - setId={}, cardId={}, reason={}",
              detail.id(),
              brief.id(),
              exception.getMessage());
        }
      }

      HashSet<String> sourceCardIds = new HashSet<>();
      cards.forEach(card -> sourceCardIds.add(card.id()));
      long synchronizedCount =
          cardJpaRepository.findAllByTcgdexSetId(detail.id()).stream()
              .map(Card::getTcgdexId)
              .filter(sourceCardIds::contains)
              .count();
      if (failures == 0 && synchronizedCount == expectedCardCount) {
        sync.complete(LocalDateTime.now());
        log.info("카드 세트 동기화 완료 - setId={}, cardCount={}", detail.id(), synchronizedCount);
      } else {
        sync.partial(
            LocalDateTime.now(),
            "expected="
                + expectedCardCount
                + ", actual="
                + synchronizedCount
                + ", failures="
                + failures);
        log.warn(
            "카드 세트 부분 동기화 - setId={}, expected={}, actual={}, failures={}",
            detail.id(),
            expectedCardCount,
            synchronizedCount,
            failures);
      }
      cardSetSyncRepository.save(sync);
    } catch (RuntimeException exception) {
      sync.partial(LocalDateTime.now(), exception.getMessage());
      cardSetSyncRepository.save(sync);
      log.error(
          "카드 세트 동기화 실패 - setId={}, reason={}", summary.id(), exception.getMessage(), exception);
    }
  }

  private void upsertCard(TcgdexSetDetail set, TcgdexSetDetail.CardBrief brief, Card savedCard) {
    TcgdexCardDetail detail = Objects.requireNonNull(tcgdexClient.getCard(brief.id()));
    String sourceImage = detail.image() == null ? brief.image() : detail.image();
    String imageUrl = imageUrl(sourceImage);
    if (savedCard == null) {
      cardJpaRepository.save(
          Card.fromTcgdex(
              detail.id(), set.id(), detail.name(), detail.localId(), set.name(), imageUrl));
      return;
    }
    savedCard.updateFromTcgdex(detail.name(), detail.localId(), set.name(), imageUrl);
    cardJpaRepository.save(savedCard);
  }

  private boolean isUnchanged(Card saved, TcgdexSetDetail.CardBrief brief, String setName) {
    return saved.getCardName().equals(brief.name())
        && saved.getCardNumber().equals(brief.localId())
        && saved.getSetName().equals(setName)
        && saved.getImageUrl().equals(imageUrl(brief.image()));
  }

  private String imageUrl(String image) {
    if (image == null || image.isBlank()) {
      return "";
    }
    return image.matches(".*\\.(png|jpg|jpeg|webp)$") ? image : image + "/high.webp";
  }

  private boolean isValid(TcgdexSetSummary summary) {
    return summary != null
        && summary.id() != null
        && summary.name() != null
        && summary.cardCount() != null;
  }
}
