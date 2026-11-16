package com.occupify.administration.exception;

import org.springframework.http.HttpStatus;

public enum AdminErrorCode implements BaseErrorCode {
  FORBIDDEN("SYS_007", "Access denied: ADMIN role required", HttpStatus.FORBIDDEN),
  UNAUTHORIZED("SYS_008", "Authentication context required", HttpStatus.UNAUTHORIZED),
  INVALID_PARAMETER("SYS_001", "Invalid request parameters", HttpStatus.BAD_REQUEST),
  INTERNAL_SERVER_ERROR(
      "SYS_999", "An unexpected system error occurred", HttpStatus.INTERNAL_SERVER_ERROR),

  USER_NOT_FOUND("ADM_001", "User not found with specified identifier", HttpStatus.NOT_FOUND),
  PROJECT_NOT_FOUND("ADM_002", "Project not found with specified identifier", HttpStatus.NOT_FOUND),
  INVALID_USER_STATUS(
      "ADM_003", "Invalid user status transition requested", HttpStatus.BAD_REQUEST),
  REPORT_NOT_FOUND("ADM_004", "Report not found with specified identifier", HttpStatus.NOT_FOUND);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;

  AdminErrorCode(String code, String message, HttpStatus httpStatus) {
    this.code = code;
    this.message = message;
    this.httpStatus = httpStatus;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String message() {
    return message;
  }

  @Override
  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
