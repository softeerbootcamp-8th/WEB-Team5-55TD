package com.ootd.pickup.global.exception.dto.response;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ExceptionResponse(int status,
                                String error,
                                String message,
                                String path,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX") ZonedDateTime timestamp) {
}
