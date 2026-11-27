package com.occupify.administration.controller;

import com.occupify.administration.config.OpenApiConfig;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
import com.occupify.administration.dto.base.SuccessResponse;
import com.occupify.administration.dto.request.AccountModerationRequest;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.security.AuthenticatedAdmin;
import com.occupify.administration.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin/users")
@Tag(
    name = "Users",
    description = "Endpoints for platform user governance, moderation, and auditing")
public class AdminUserController extends AbstractBaseController {

  private final AdminUserService adminUserService;

  public AdminUserController(AdminUserService adminUserService) {
    this.adminUserService = adminUserService;
  }

  @GetMapping
  @Operation(
      summary = "View and filter accounts",
      description =
          "Retrieves a paginated list of accounts with filtering by keyword, status, and role",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<PageResponse<AdminUserResponse>> getUsers(
      @ParameterObject AdminUserFilterRequest filter,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[GET /admin/users] Admin [{}] querying accounts with filter: {}",
        adminContext.adminId(),
        filter);
    PageResponse<AdminUserResponse> response = adminUserService.getUsers(filter, pageable);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "View profile detail",
      description =
          "Retrieves full user detail, moderation flags, and contract history for a specified user",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SingleResponse<AdminUserDetailResponse>> getUserDetail(
      @PathVariable("id") UUID id,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[GET /admin/users/{}] Admin [{}] fetching account detail", id, adminContext.adminId());
    AdminUserDetailResponse response = adminUserService.getUserDetail(id);
    return successSingle(response, "User details retrieved successfully");
  }

  @DeleteMapping("/{id}")
  @Operation(
      summary = "Soft delete user account",
      description =
          "Soft deletes a user account (sets status to DELETED, deletedAt, and optional statusReason), records an audit log, and dispatches status sync to identity and core services",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SuccessResponse> deleteUser(
      @PathVariable("id") UUID id,
      @Valid @RequestBody(required = false) AccountModerationRequest request,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    String reason = request != null ? request.reason() : null;
    log.info(
        "[DELETE /admin/users/{}] Admin [{}] soft-deleting account", id, adminContext.adminId());
    adminUserService.deleteUser(id, reason, adminContext);
    return success("User account deleted successfully");
  }

  @PatchMapping("/{id}/lock")
  @Operation(
      summary = "Lock user account",
      description =
          "Locks an active user account (sets status to BANNED and optional statusReason), revokes active sessions via identity service, and records an audit log",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SingleResponse<AdminUserResponse>> lockUser(
      @PathVariable("id") UUID id,
      @Valid @RequestBody(required = false) AccountModerationRequest request,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    String reason = request != null ? request.reason() : null;
    log.info("[PATCH /admin/users/{}/lock] Admin [{}] locking account", id, adminContext.adminId());
    AdminUserResponse response = adminUserService.lockUser(id, reason, adminContext);
    return successSingle(response, "User account locked successfully");
  }

  @PatchMapping("/{id}/unlock")
  @Operation(
      summary = "Unlock user account",
      description =
          "Unlocks a locked (BANNED) user account back to ACTIVE, clears statusReason, syncs status across services, and records an audit log",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SingleResponse<AdminUserResponse>> unlockUser(
      @PathVariable("id") UUID id,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[PATCH /admin/users/{}/unlock] Admin [{}] unlocking account", id, adminContext.adminId());
    AdminUserResponse response = adminUserService.unlockUser(id, adminContext);
    return successSingle(response, "User account unlocked successfully");
  }
}
