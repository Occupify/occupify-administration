package com.occupify.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AccountModerationRequest;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.AdminUserService;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

  @Mock private AdminUserService adminUserService;

  @Spy private ResponseFactory responseFactory = new ResponseFactory();

  @InjectMocks private AdminUserController controller;

  private AdminContext adminContext;

  @BeforeEach
  void setUp() {
    adminContext = new AdminContext(UUID.randomUUID(), "admin@occupify.app", UserRole.ADMIN);
  }

  @Test
  @DisplayName("GET /admin/users - Should return 200 OK with paginated users")
  void getUsers_shouldReturnPaginatedUsers() {
    AdminUserFilterRequest filter =
        new AdminUserFilterRequest("test", UserStatus.ACTIVE, UserRole.USER);
    Pageable pageable = PageRequest.of(0, 20);

    AdminUserResponse userResponse =
        new AdminUserResponse(
            UUID.randomUUID(),
            "test@example.com",
            "Test User",
            null,
            UserRole.USER,
            UserStatus.ACTIVE,
            null,
            false,
            3,
            0,
            Instant.now());

    PageResponse<AdminUserResponse> pageResponse =
        new PageResponse<>(
            200,
            "Users retrieved successfully",
            List.of(userResponse),
            new PageResponse.PagingInfo(0, 20, 1, 1));

    when(adminUserService.getUsers(any(), any())).thenReturn(pageResponse);

    ResponseEntity<PageResponse<AdminUserResponse>> response =
        controller.getUsers(filter, pageable, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().statusCode()).isEqualTo(200);
    assertThat(response.getBody().data()).hasSize(1);
    assertThat(response.getBody().data().get(0).email()).isEqualTo("test@example.com");
  }

  @Test
  @DisplayName("GET /admin/users/{id} - Should return user detail when user exists")
  void getUserDetail_shouldReturnUserDetail() {
    UUID userId = UUID.randomUUID();
    AdminUserDetailResponse detail =
        new AdminUserDetailResponse(
            userId,
            "detail@example.com",
            "Detail User",
            null,
            UserRole.USER,
            UserStatus.ACTIVE,
            null,
            Instant.now(),
            null,
            false,
            null,
            5,
            10,
            0,
            Collections.emptyList());

    when(adminUserService.getUserDetail(eq(userId))).thenReturn(detail);

    var response = controller.getUserDetail(userId, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data()).isNotNull();
    assertThat(response.getBody().data().id()).isEqualTo(userId);
    assertThat(response.getBody().data().email()).isEqualTo("detail@example.com");
  }

  @Test
  @DisplayName("GET /admin/users/{id} - Should throw AdminException when user not found")
  void getUserDetail_shouldThrow_whenUserNotFound() {
    UUID userId = UUID.randomUUID();
    when(adminUserService.getUserDetail(eq(userId)))
        .thenThrow(new AdminException(AdminErrorCode.USER_NOT_FOUND));

    assertThatThrownBy(() -> controller.getUserDetail(userId, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.USER_NOT_FOUND.code());
  }

  @Test
  @DisplayName("DELETE /admin/users/{id} - Should soft delete account with nullable reason")
  void deleteUser_shouldReturnSuccess() {
    UUID userId = UUID.randomUUID();
    var response =
        controller.deleteUser(userId, new AccountModerationRequest("Spam account"), adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().statusCode()).isEqualTo(200);
    verify(adminUserService).deleteUser(userId, "Spam account", adminContext);
  }

  @Test
  @DisplayName("DELETE /admin/users/{id} - Should allow null request body")
  void deleteUser_shouldAllowNullBody() {
    UUID userId = UUID.randomUUID();
    var response = controller.deleteUser(userId, null, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(adminUserService).deleteUser(userId, null, adminContext);
  }

  @Test
  @DisplayName("PATCH /admin/users/{id}/lock - Should lock account and return updated response")
  void lockUser_shouldReturnLockedUser() {
    UUID userId = UUID.randomUUID();
    AdminUserResponse locked =
        new AdminUserResponse(
            userId,
            "user@example.com",
            "Target User",
            null,
            UserRole.USER,
            UserStatus.BANNED,
            "Violation of terms",
            false,
            0,
            1,
            Instant.now());

    when(adminUserService.lockUser(userId, "Violation of terms", adminContext)).thenReturn(locked);

    var response =
        controller.lockUser(
            userId, new AccountModerationRequest("Violation of terms"), adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().status()).isEqualTo(UserStatus.BANNED);
    assertThat(response.getBody().data().statusReason()).isEqualTo("Violation of terms");
  }

  @Test
  @DisplayName("PATCH /admin/users/{id}/unlock - Should unlock account and return updated response")
  void unlockUser_shouldReturnActiveUser() {
    UUID userId = UUID.randomUUID();
    AdminUserResponse unlocked =
        new AdminUserResponse(
            userId,
            "user@example.com",
            "Target User",
            null,
            UserRole.USER,
            UserStatus.ACTIVE,
            null,
            false,
            0,
            1,
            Instant.now());

    when(adminUserService.unlockUser(userId, adminContext)).thenReturn(unlocked);

    var response = controller.unlockUser(userId, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(response.getBody().data().statusReason()).isNull();
  }
}
