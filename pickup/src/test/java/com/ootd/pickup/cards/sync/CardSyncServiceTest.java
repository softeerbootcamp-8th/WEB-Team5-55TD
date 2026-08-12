package com.ootd.pickup.cards.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardSyncServiceTest {

  private TcgdexClient tcgdexClient;
  private CardSetSyncRepository cardSetSyncRepository;
  private CardJpaRepository cardJpaRepository;
  private CardSyncService cardSyncService;

  @BeforeEach
  void setUp() {
    tcgdexClient = mock(TcgdexClient.class);
    cardSetSyncRepository = mock(CardSetSyncRepository.class);
    cardJpaRepository = mock(CardJpaRepository.class);
    cardSyncService =
        new CardSyncService(
            tcgdexClient, cardSetSyncRepository, cardJpaRepository, new CardSyncProperties(90, 10));
  }

  @Test
  void 신규_세트의_카드를_저장하고_COMPLETE로_기록한다() {
    TcgdexSetSummary summary = summary("base1", "Base Set", 1);
    TcgdexSetDetail.CardBrief brief = brief();
    when(tcgdexClient.findAllSets()).thenReturn(List.of(summary));
    when(cardSetSyncRepository.findAll()).thenReturn(List.of());
    when(tcgdexClient.getSet("base1")).thenReturn(detail(brief));
    Card synchronizedCard =
        Card.fromTcgdex("base1-1", "base1", "Alakazam", "1", "Base Set", "https://image/1");
    when(cardJpaRepository.findAllByTcgdexSetId("base1"))
        .thenReturn(List.of(), List.of(synchronizedCard));
    when(tcgdexClient.getCard("base1-1"))
        .thenReturn(
            new TcgdexCardDetail(
                "base1-1",
                "1",
                "Alakazam",
                "https://image/1",
                "Rare",
                new TcgdexCardDetail.SetBrief("base1", "Base Set")));

    cardSyncService.synchronizeCards();

    ArgumentCaptor<CardSetSync> syncCaptor = ArgumentCaptor.forClass(CardSetSync.class);
    verify(cardSetSyncRepository, times(2)).save(syncCaptor.capture());
    assertThat(syncCaptor.getValue().getStatus()).isEqualTo(CardSetSyncStatus.COMPLETE);
    verify(cardJpaRepository).save(any(Card.class));
  }

  @Test
  void 카드_상세_조회가_실패하면_PARTIAL로_기록해_다음_주기에_재시도한다() {
    TcgdexSetSummary summary = summary("base1", "Base Set", 1);
    when(tcgdexClient.findAllSets()).thenReturn(List.of(summary));
    when(cardSetSyncRepository.findAll()).thenReturn(List.of());
    when(tcgdexClient.getSet("base1")).thenReturn(detail(brief()));
    when(cardJpaRepository.findAllByTcgdexSetId("base1")).thenReturn(List.of());
    when(tcgdexClient.getCard("base1-1")).thenThrow(new IllegalStateException("timeout"));

    cardSyncService.synchronizeCards();

    ArgumentCaptor<CardSetSync> syncCaptor = ArgumentCaptor.forClass(CardSetSync.class);
    verify(cardSetSyncRepository, times(2)).save(syncCaptor.capture());
    assertThat(syncCaptor.getValue().getStatus()).isEqualTo(CardSetSyncStatus.PARTIAL);
    assertThat(syncCaptor.getValue().getRetryCount()).isEqualTo(1);
  }

  @Test
  void 변경되지_않은_과거_COMPLETE_세트는_건너뛴다() {
    TcgdexSetSummary summary = summary("base1", "Base Set", 1);
    CardSetSync saved = new CardSetSync("base1", "Base Set", 1);
    saved.start("Base Set", 1, LocalDate.of(1999, 1, 9));
    saved.complete(LocalDateTime.now());
    when(tcgdexClient.findAllSets()).thenReturn(List.of(summary));
    when(cardSetSyncRepository.findAll()).thenReturn(List.of(saved));

    cardSyncService.synchronizeCards();

    verify(tcgdexClient, never()).getSet("base1");
  }

  private TcgdexSetSummary summary(String id, String name, int total) {
    return new TcgdexSetSummary(id, name, new TcgdexSetSummary.CardCount(total, total));
  }

  private TcgdexSetDetail.CardBrief brief() {
    return new TcgdexSetDetail.CardBrief("base1-1", "1", "Alakazam", "https://image/1");
  }

  private TcgdexSetDetail detail(TcgdexSetDetail.CardBrief brief) {
    return new TcgdexSetDetail(
        "base1",
        "Base Set",
        new TcgdexSetSummary.CardCount(1, 1),
        LocalDate.of(1999, 1, 9),
        List.of(brief));
  }
}
