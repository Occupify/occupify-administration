package com.occupify.administration.controller;

import com.occupify.administration.dto.base.CreatedResponse;
import com.occupify.administration.dto.base.ErrorResponse;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
import com.occupify.administration.dto.base.SuccessResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;

public abstract class AbstractBaseController {

  @Autowired protected ResponseFactory responseFactory = new ResponseFactory();

  protected <T> ResponseEntity<SingleResponse<T>> successSingle(@Nullable T data, String message) {
    return responseFactory.successSingle(data, message);
  }

  protected <T> ResponseEntity<CreatedResponse<T>> created(@Nullable T data, String message) {
    return responseFactory.created(data, message);
  }

  protected ResponseEntity<SuccessResponse> success(String message) {
    return responseFactory.success(message);
  }

  protected <T> ResponseEntity<PageResponse<T>> paging(Page<T> page, String message) {
    PageResponse<T> response =
        responseFactory.createPageResponse(
            message,
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages());
    return ResponseEntity.ok(response);
  }

  protected ResponseEntity<ErrorResponse> error(
      HttpStatus status, String message, @Nullable String errorCode) {
    return responseFactory.error(status, message, errorCode);
  }
}
