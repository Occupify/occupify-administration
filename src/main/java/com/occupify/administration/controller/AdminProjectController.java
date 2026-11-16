package com.occupify.administration.controller;

import com.occupify.administration.config.OpenApiConfig;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.security.AuthenticatedAdmin;
import com.occupify.administration.service.AdminProjectService;
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
@RequestMapping("/admin/projects")
@Tag(
    name = "Projects",
    description = "Endpoints for platform project moderation, dispute review, and auditing")
public class AdminProjectController extends AbstractBaseController {

  private final AdminProjectService adminProjectService;

  public AdminProjectController(AdminProjectService adminProjectService) {
    this.adminProjectService = adminProjectService;
  }

  @GetMapping
  @Operation(
      summary = "View and filter projects",
      description =
          "Retrieves a paginated list of projects with filtering by keyword, status, and employer",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<PageResponse<AdminProjectResponse>> getProjects(
      @ParameterObject AdminProjectFilterRequest filter,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[GET /admin/projects] Admin [{}] querying projects with filter: {}",
        adminContext.adminId(),
        filter);
    PageResponse<AdminProjectResponse> response = adminProjectService.getProjects(filter, pageable);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "View project detail",
      description =
          "Retrieves full project details including employer metadata and associated violation reports",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SingleResponse<AdminProjectDetailResponse>> getProjectDetail(
      @PathVariable("id") UUID id,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[GET /admin/projects/{}] Admin [{}] fetching project detail", id, adminContext.adminId());
    AdminProjectDetailResponse response = adminProjectService.getProjectDetail(id);
    return successSingle(response, "Project details retrieved successfully");
  }
}
