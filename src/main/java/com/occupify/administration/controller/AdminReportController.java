package com.occupify.administration.controller;

import com.occupify.administration.config.OpenApiConfig;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
import com.occupify.administration.dto.request.UpdateReportStatusRequest;
import com.occupify.administration.dto.response.AdminReportDetailResponse;
import com.occupify.administration.dto.response.AdminReportResponse;
import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.security.AuthenticatedAdmin;
import com.occupify.administration.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin/reports")
@Tag(
    name = "Reports",
    description = "Endpoints for platform report review, moderation, and dispute resolution")
public class AdminReportController extends AbstractBaseController {

  private final AdminReportService adminReportService;

  public AdminReportController(AdminReportService adminReportService) {
    this.adminReportService = adminReportService;
  }

  @GetMapping
  @Operation(
      summary = "List and filter reports",
      description =
          "Retrieves a paginated list of reports with optional filtering by status, targetType, and reasonCategory",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<PageResponse<AdminReportResponse>> listReports(
      @RequestParam(name = "status", required = false) ReportStatus status,
      @RequestParam(name = "targetType", required = false) ReportTargetType targetType,
      @RequestParam(name = "reasonCategory", required = false) ReportReasonCategory reasonCategory,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[GET /admin/reports] Admin [{}] querying reports with status={}, targetType={}, reasonCategory={}",
        adminContext.adminId(),
        status,
        targetType,
        reasonCategory);
    PageResponse<AdminReportResponse> response =
        adminReportService.listReports(pageable, status, targetType, reasonCategory);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "View report detail",
      description = "Retrieves full report details including attached evidence URLs",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SingleResponse<AdminReportDetailResponse>> getReportById(
      @PathVariable("id") UUID id,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[GET /admin/reports/{}] Admin [{}] fetching report detail", id, adminContext.adminId());
    AdminReportDetailResponse response = adminReportService.getReportById(id);
    return successSingle(response, "Report details retrieved successfully");
  }

  @PutMapping("/{id}/status")
  @Operation(
      summary = "Update report status",
      description =
          "Updates the status of a violation report and broadcasts an update event to the message bus",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  public ResponseEntity<SingleResponse<AdminReportResponse>> updateReportStatus(
      @PathVariable("id") UUID id,
      @Valid @RequestBody UpdateReportStatusRequest request,
      @Parameter(hidden = true) @AuthenticatedAdmin AdminContext adminContext) {
    log.info(
        "[PUT /admin/reports/{}/status] Admin [{}] updating status to {}",
        id,
        adminContext.adminId(),
        request.status());
    AdminReportResponse response = adminReportService.updateReportStatus(id, request, adminContext);
    return successSingle(response, "Report status updated successfully");
  }
}
