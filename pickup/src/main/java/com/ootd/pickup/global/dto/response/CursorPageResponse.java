package com.ootd.pickup.global.dto.response;

import java.util.List;

public record CursorPageResponse<T>(
        boolean hasNext,
        String cursor,
        int size,
        List<T> items
) {
    public static <T> CursorPageResponse<T> from(
            List<T> items,
            boolean hasNext,
            String cursor
    ) {
        return new CursorPageResponse<>(
                hasNext,
                hasNext ? cursor : null,
                items.size(),
                items
        );
    }
}
