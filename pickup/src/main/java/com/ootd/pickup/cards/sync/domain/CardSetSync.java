package com.ootd.pickup.cards.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "card_set_sync")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardSetSync {

  @Id
  @Column(name = "tcgdex_set_id", nullable = false, length = 100)
  private String tcgdexSetId;

  @Column(name = "set_name", nullable = false)
  private String setName;

  @Column(name = "expected_card_count", nullable = false)
  private int expectedCardCount;

  @Column(name = "release_date")
  private LocalDate releaseDate;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private CardSetSyncStatus status;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_synced_at")
  private LocalDateTime lastSyncedAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  public CardSetSync(String tcgdexSetId, String setName, int expectedCardCount) {
    this.tcgdexSetId = tcgdexSetId;
    this.setName = setName;
    this.expectedCardCount = expectedCardCount;
    this.status = CardSetSyncStatus.IN_PROGRESS;
  }

  public void start(String setName, int expectedCardCount, LocalDate releaseDate) {
    this.setName = setName;
    this.expectedCardCount = expectedCardCount;
    this.releaseDate = releaseDate;
    this.status = CardSetSyncStatus.IN_PROGRESS;
    this.lastError = null;
  }

  public void complete(LocalDateTime syncedAt) {
    this.status = CardSetSyncStatus.COMPLETE;
    this.lastSyncedAt = syncedAt;
    this.retryCount = 0;
    this.lastError = null;
  }

  public void partial(LocalDateTime syncedAt, String error) {
    this.status = CardSetSyncStatus.PARTIAL;
    this.lastSyncedAt = syncedAt;
    this.retryCount++;
    this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
  }
}
