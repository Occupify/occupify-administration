package com.occupify.administration.service;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.UpdateReportStatusRequest;
import com.occupify.administration.dto.response.AdminReportDetailResponse;
import com.occupify.administration.dto.response.AdminReportResponse;
import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import com.occupify.administration.security.AdminContext;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface AdminReportService {
  PageResponse<AdminReportResponse> listReports(
      Pageable pageable,
      ReportStatus status,
      ReportTargetType targetType,
      ReportReasonCategory reasonCategory);

  AdminReportDetailResponse getReportById(UUID id);

  AdminReportResponse updateReportStatus(
      UUID id, UpdateReportStatusRequest request, AdminContext adminContext);
}
