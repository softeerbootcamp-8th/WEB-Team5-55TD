package com.ootd.pickup.global.exception.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.ZonedDateTime;

public record ExceptionResponse(
    int status,
    String error,
    String message,
    String path,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        ZonedDateTime timestamp) {}
