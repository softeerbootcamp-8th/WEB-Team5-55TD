package com.ootd.pickup.cards.sync.client;

import java.time.LocalDate;
import java.util.List;

public record TcgdexSetDetail(
    String id,
    String name,
    TcgdexSetSummary.CardCount cardCount,
    LocalDate releaseDate,
    List<CardBrief> cards) {

  public record CardBrief(String id, String localId, String name, String image) {}
}
