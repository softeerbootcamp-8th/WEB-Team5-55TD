package com.ootd.pickup.cards.dto.request;

import com.ootd.pickup.global.dto.request.CursorPageRequest;
import jakarta.validation.constraints.NotNull;

public record SearchCardsRequest(
    String keyword, String setName, String language, Long cursor, @NotNull Integer size)
    implements CursorPageRequest {}
