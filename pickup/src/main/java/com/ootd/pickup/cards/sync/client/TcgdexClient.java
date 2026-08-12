package com.ootd.pickup.cards.sync.client;

import java.util.List;

public interface TcgdexClient {
  List<TcgdexSetSummary> findAllSets();

  TcgdexSetDetail getSet(String setId);

  TcgdexCardDetail getCard(String cardId);
}
