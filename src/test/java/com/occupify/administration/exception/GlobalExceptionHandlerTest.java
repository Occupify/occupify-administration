package com.occupify.administration.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.occupify.administration.dto.base.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler exceptionHandler;

  @Mock private HttpServletRequest request;

  @BeforeEach
  void setUp() {
    exceptionHandler = new GlobalExceptionHandler();
  }

  @Test
  @DisplayName("Should format ErrorResponse correctly for AdminException")
  void shouldFormatErrorResponse_forAdminException() {
    when(request.getRequestURI()).thenReturn("/admin/users");
    AdminException ex = new AdminException(AdminErrorCode.FORBIDDEN);

    ResponseEntity<ErrorResponse> response = exceptionHandler.handleBaseException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().statusCode()).isEqualTo(403);
    assertThat(response.getBody().errorCode()).isEqualTo("SYS_007");
    assertThat(response.getBody().message()).contains("Access denied");
  }

  @Test
  @DisplayName("Should format ErrorResponse correctly for generic Exception")
  void shouldFormatErrorResponse_forGenericException() {
    when(request.getRequestURI()).thenReturn("/admin/users");
    RuntimeException ex = new RuntimeException("Unexpected database failure");

    ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().statusCode()).isEqualTo(500);
    assertThat(response.getBody().errorCode()).isEqualTo("SYS_999");
  }
}
