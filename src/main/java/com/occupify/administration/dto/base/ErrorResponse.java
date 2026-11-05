package com.occupify.administration.dto.base;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import org.springframework.lang.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"statusCode", "errorCode", "message", "timestamp"})
public record ErrorResponse(
    Integer statusCode,
    String message,
    @Nullable String errorCode,
    Instant timestamp
) {
    public ErrorResponse(Integer statusCode, String message, @Nullable String errorCode) {
        this(statusCode, message, errorCode, Instant.now());
    }
}

