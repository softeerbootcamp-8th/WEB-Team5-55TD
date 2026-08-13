package com.ootd.pickup.auction.repository.auction;

import static com.ootd.pickup.global.exception.ExceptionCode.*;

import com.ootd.pickup.global.exception.PickUpException;
import java.util.Arrays;

/** 검색어(q)를 어떤 항목에 맞춰볼지 지정한다. */
public enum AuctionSearchField {
  /** 경매명 · 카드명 · 세트명 · 카드 언어 · 판매자 닉네임 중 하나라도 맞으면 된다. */
  ALL,
  AUCTION_TITLE,
  CARD_NAME,
  SELLER;

  public static AuctionSearchField from(String searchField) {
    if (searchField == null || searchField.isBlank()) {
      return ALL;
    }

    return Arrays.stream(values())
        .filter(value -> value.name().equalsIgnoreCase(searchField))
        .findFirst()
        .orElseThrow(() -> new PickUpException(INVALID_AUCTION_SEARCH_FIELD));
  }
}
