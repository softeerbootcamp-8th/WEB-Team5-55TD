package com.ootd.pickup.cards.sync.client;

public record TcgdexCardDetail(
    String id, String localId, String name, String image, String rarity, SetBrief set) {

  public record SetBrief(String id, String name) {}
}
