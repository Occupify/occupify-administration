package com.occupify.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.base.SingleResponse;
import com.occupify.administration.dto.request.UpdateReportStatusRequest;
import com.occupify.administration.dto.response.AdminReportDetailResponse;
import com.occupify.administration.dto.response.AdminReportResponse;
import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.AdminReportService;
import java.time.Instant;
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
class AdminReportControllerTest {

  @Mock private AdminReportService adminReportService;

  @Spy private ResponseFactory responseFactory = new ResponseFactory();

  @InjectMocks private AdminReportController controller;

  private AdminContext adminContext;

  @BeforeEach
  void setUp() {
    adminContext = new AdminContext(UUID.randomUUID(), "admin@occupify.app", UserRole.ADMIN);
  }

  @Test
  @DisplayName("GET /admin/reports - Should return 200 OK with paginated reports")
  void getReports_shouldReturnPaginatedReports() {
    Pageable pageable = PageRequest.of(0, 20);
    AdminReportResponse reportResponse =
        new AdminReportResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "reporter@test.com",
            ReportTargetType.USER,
            UUID.randomUUID(),
            null,
            ReportReasonCategory.FRAUD,
            "Fraudulent behavior",
            ReportStatus.PENDING,
            Instant.now(),
            Instant.now());

    PageResponse<AdminReportResponse> mockPage =
        new PageResponse<>(
            200, "OK", List.of(reportResponse), new PageResponse.PagingInfo(0, 20, 1L, 1));

    when(adminReportService.listReports(eq(pageable), eq(ReportStatus.PENDING), any(), any()))
        .thenReturn(mockPage);

    ResponseEntity<PageResponse<AdminReportResponse>> response =
        controller.listReports(ReportStatus.PENDING, null, null, pageable, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data()).hasSize(1);
    assertThat(response.getBody().data().get(0).reasonCategory())
        .isEqualTo(ReportReasonCategory.FRAUD);
  }

  @Test
  @DisplayName(
      "GET /admin/reports/{id} - Should return 200 OK with report details and evidence URLs")
  void getReportById_shouldReturnReportDetail() {
    UUID reportId = UUID.randomUUID();
    AdminReportDetailResponse detailResponse =
        new AdminReportDetailResponse(
            reportId,
            UUID.randomUUID(),
            "reporter@test.com",
            ReportTargetType.PROJECT,
            null,
            UUID.randomUUID(),
            ReportReasonCategory.SPAM_HARASSMENT,
            "Spam project posting",
            ReportStatus.PENDING,
            Instant.now(),
            Instant.now(),
            List.of("https://storage.occupify.app/evidence1.png"));

    when(adminReportService.getReportById(reportId)).thenReturn(detailResponse);

    ResponseEntity<SingleResponse<AdminReportDetailResponse>> response =
        controller.getReportById(reportId, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().id()).isEqualTo(reportId);
    assertThat(response.getBody().data().evidenceUrls()).hasSize(1);
  }

  @Test
  @DisplayName(
      "PUT /admin/reports/{id}/status - Should update status to RESOLVED and return 200 OK")
  void updateReportStatus_shouldReturnUpdatedReport() {
    UUID reportId = UUID.randomUUID();
    UpdateReportStatusRequest request =
        new UpdateReportStatusRequest(ReportStatus.RESOLVED, "Violation confirmed by admin review");

    AdminReportResponse updatedResponse =
        new AdminReportResponse(
            reportId,
            UUID.randomUUID(),
            "reporter@test.com",
            ReportTargetType.USER,
            UUID.randomUUID(),
            null,
            ReportReasonCategory.FRAUD,
            "Fraudulent behavior",
            ReportStatus.RESOLVED,
            Instant.now(),
            Instant.now());

    when(adminReportService.updateReportStatus(eq(reportId), any(), any()))
        .thenReturn(updatedResponse);

    ResponseEntity<SingleResponse<AdminReportResponse>> response =
        controller.updateReportStatus(reportId, request, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data().status()).isEqualTo(ReportStatus.RESOLVED);
  }
}
