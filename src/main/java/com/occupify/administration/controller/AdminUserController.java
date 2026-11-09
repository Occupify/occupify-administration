package com.occupify.administration.controller;

import com.occupify.administration.config.OpenApiConfig;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
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
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
