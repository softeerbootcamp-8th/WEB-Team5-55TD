package com.ootd.pickup.cards.sync.client;

public record TcgdexSetSummary(String id, String name, CardCount cardCount) {
  public record CardCount(int total, int official) {}
}
