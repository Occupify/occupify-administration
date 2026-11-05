package com.occupify.administration.exception;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    String code();
    String message();
    HttpStatus httpStatus();
}

