package com.ootd.pickup.global.dto.response;

import java.util.List;

public record CursorPageResponse<T, C>(
    boolean hasNext,
    C cursor,
    int size,
    List<T> items
) {
    public static <T, C> CursorPageResponse<T, C> from(
        List<T> items,
        boolean hasNext,
        C cursor
    ) {
        return new CursorPageResponse<>(
            hasNext,
            hasNext ? cursor : null,
            items.size(),
            items
        );
    }
}
