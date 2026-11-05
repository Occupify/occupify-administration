package com.occupify.administration.controller;

import com.occupify.administration.dto.base.CreatedResponse;
import com.occupify.administration.dto.base.ErrorResponse;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
import com.occupify.administration.dto.base.SuccessResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ResponseFactory {

    public <T> SingleResponse<T> createSingleResponse(HttpStatus status, String message, @Nullable T data) {
        return new SingleResponse<>(status.value(), message, data);
    }

    public <T> PageResponse<T> createPageResponse(
            String message, List<T> data, int page, int limit, long total, int totalPages) {
        return new PageResponse<>(
                HttpStatus.OK.value(),
                message,
                data,
                new PageResponse.PagingInfo(page, limit, total, totalPages));
    }

    public SuccessResponse createSuccessResponse(HttpStatus status, String message) {
        return new SuccessResponse(status.value(), message);
    }

    public <T> ResponseEntity<SingleResponse<T>> successSingle(@Nullable T data, String message) {
        return ResponseEntity.ok(new SingleResponse<>(HttpStatus.OK.value(), message, data));
    }

    public <T> ResponseEntity<CreatedResponse<T>> created(@Nullable T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreatedResponse<>(HttpStatus.CREATED.value(), message, data));
    }

    public ResponseEntity<SuccessResponse> success(String message) {
        return ResponseEntity.ok(new SuccessResponse(HttpStatus.OK.value(), message));
    }

    public ResponseEntity<ErrorResponse> error(HttpStatus status, String message, @Nullable String errorCode) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), message, errorCode));
    }
}

