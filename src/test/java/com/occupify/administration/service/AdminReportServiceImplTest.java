package com.occupify.administration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.UpdateReportStatusRequest;
import com.occupify.administration.dto.response.AdminReportDetailResponse;
import com.occupify.administration.dto.response.AdminReportResponse;
import com.occupify.administration.entity.Project;
import com.occupify.administration.entity.Report;
import com.occupify.administration.entity.ReportEvidence;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ReportUpdatedEvent;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.repository.ReportEvidenceRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.impl.AdminReportServiceImpl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceImplTest {

  @Mock private ReportRepository reportRepository;
  @Mock private ReportEvidenceRepository reportEvidenceRepository;
  @Mock private RabbitTemplate rabbitTemplate;
  @Spy private ResponseFactory responseFactory = new ResponseFactory();

  @InjectMocks private AdminReportServiceImpl adminReportService;

  private UUID reportId;
  private User reporter;
  private User targetUser;
  private Project targetProject;
  private Report sampleReport;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        adminReportService, "reportExchangeName", "occupify.report.exchange");
    ReflectionTestUtils.setField(adminReportService, "reportUpdatedRoutingKey", "report.updated");

    reportId = UUID.randomUUID();
    reporter =
        User.builder()
            .id(UUID.randomUUID())
            .email("reporter@test.com")
            .fullName("Reporter User")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    targetUser =
        User.builder()
            .id(UUID.randomUUID())
            .email("target@test.com")
            .fullName("Target User")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    targetProject = Project.builder().id(UUID.randomUUID()).title("Test Project").build();

    sampleReport =
        Report.builder()
            .id(reportId)
            .reporterUser(reporter)
            .targetType(ReportTargetType.USER)
            .targetUser(targetUser)
            .targetProject(targetProject)
            .reasonCategory(ReportReasonCategory.FRAUD)
            .description("Fraud report")
            .status(ReportStatus.PENDING)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("Should list reports with filters")
  void listReports_returnsPageResponse() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Report> page = new PageImpl<>(List.of(sampleReport), pageable, 1);

    when(reportRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

    PageResponse<AdminReportResponse> response =
        adminReportService.listReports(
            pageable, ReportStatus.PENDING, ReportTargetType.USER, ReportReasonCategory.FRAUD);

    assertThat(response).isNotNull();
    assertThat(response.data()).hasSize(1);
    AdminReportResponse item = response.data().get(0);
    assertThat(item.id()).isEqualTo(reportId);
    assertThat(item.reporterId()).isEqualTo(reporter.getId());
    assertThat(item.reporterEmail()).isEqualTo("reporter@test.com");
    assertThat(item.targetType()).isEqualTo(ReportTargetType.USER);
    assertThat(item.status()).isEqualTo(ReportStatus.PENDING);
  }

  @Test
  @DisplayName("Should return report details including evidences")
  void getReportById_whenFound_returnsDetailResponse() {
    when(reportRepository.findById(reportId)).thenReturn(Optional.of(sampleReport));

    ReportEvidence evidence1 =
        ReportEvidence.builder()
            .id(UUID.randomUUID())
            .report(sampleReport)
            .fileUrl("https://storage.occupify.com/evidence1.png")
            .build();
    ReportEvidence evidence2 =
        ReportEvidence.builder()
            .id(UUID.randomUUID())
            .report(sampleReport)
            .fileUrl("https://storage.occupify.com/evidence2.pdf")
            .build();

    when(reportEvidenceRepository.findByReportId(reportId))
        .thenReturn(List.of(evidence1, evidence2));

    AdminReportDetailResponse result = adminReportService.getReportById(reportId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(reportId);
    assertThat(result.evidenceUrls())
        .containsExactly(
            "https://storage.occupify.com/evidence1.png",
            "https://storage.occupify.com/evidence2.pdf");
  }

  @Test
  @DisplayName("Should throw exception when report not found by id")
  void getReportById_whenNotFound_throwsAdminException() {
    when(reportRepository.findById(reportId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> adminReportService.getReportById(reportId))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.REPORT_NOT_FOUND.code());
  }

  @Test
  @DisplayName("Should update report status and publish ReportUpdatedEvent")
  void updateReportStatus_updatesStatusAndPublishesEvent() {
    when(reportRepository.findById(reportId)).thenReturn(Optional.of(sampleReport));
    when(reportRepository.save(any(Report.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    UUID adminId = UUID.randomUUID();
    AdminContext adminContext = new AdminContext(adminId, "admin@occupify.app", UserRole.ADMIN);
    UpdateReportStatusRequest request =
        new UpdateReportStatusRequest(ReportStatus.RESOLVED, "Violation confirmed, warning issued");

    AdminReportResponse response =
        adminReportService.updateReportStatus(reportId, request, adminContext);

    assertThat(response.status()).isEqualTo(ReportStatus.RESOLVED);
    assertThat(sampleReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);

    ArgumentCaptor<ReportUpdatedEvent> eventCaptor =
        ArgumentCaptor.forClass(ReportUpdatedEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("occupify.report.exchange"), eq("report.updated"), eventCaptor.capture());

    ReportUpdatedEvent publishedEvent = eventCaptor.getValue();
    assertThat(publishedEvent.reportId()).isEqualTo(reportId);
    assertThat(publishedEvent.status()).isEqualTo("RESOLVED");
    assertThat(publishedEvent.moderatedByAdminId()).isEqualTo(adminId);
    assertThat(publishedEvent.note()).isEqualTo("Violation confirmed, warning issued");
    assertThat(publishedEvent.updatedAt()).isNotNull();
  }
}
