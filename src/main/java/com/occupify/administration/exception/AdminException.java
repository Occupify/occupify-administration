package com.occupify.administration.exception;

public class AdminException extends BaseException {
    public AdminException(AdminErrorCode errorCode) {
        super(errorCode);
    }

    public AdminException(AdminErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}

