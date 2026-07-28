package com.ootd.pickup.cards.dto.request;

import jakarta.validation.constraints.NotNull;

public record SearchCardsRequest(
    String keyword,
    String setName,
    String language,
    Long cursor,
    @NotNull Integer size
) {
}
