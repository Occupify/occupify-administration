package com.occupify.administration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.occupify.administration.entity.Report;
import com.occupify.administration.entity.ReportEvidence;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ReportCreatedEvent;
import com.occupify.administration.repository.ProjectRepository;
import com.occupify.administration.repository.ReportEvidenceRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportEventListenerTest {

  @Mock private ReportRepository reportRepository;
  @Mock private ReportEvidenceRepository reportEvidenceRepository;
  @Mock private UserRepository userRepository;
  @Mock private ProjectRepository projectRepository;

  private ReportEventListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new ReportEventListener(
            reportRepository, reportEvidenceRepository, userRepository, projectRepository);
  }

  @Test
  @DisplayName("Should skip processing if report already exists (idempotency check)")
  void onReportCreated_whenReportAlreadyExists_skipsProcessing() {
    UUID reportId = UUID.randomUUID();
    ReportCreatedEvent event =
        new ReportCreatedEvent(
            reportId,
            UUID.randomUUID(),
            "USER",
            UUID.randomUUID(),
            null,
            "FRAUD",
            "Scam attempt",
            List.of(),
            "PENDING",
            Instant.now());

    when(reportRepository.existsById(reportId)).thenReturn(true);

    listener.onReportCreated(event);

    verify(reportRepository, never()).save(any());
    verify(reportEvidenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should persist report and evidences when event is valid")
  void onReportCreated_whenValidEvent_persistsReportAndEvidences() {
    UUID reportId = UUID.randomUUID();
    UUID reporterId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-09T09:00:00Z");

    User reporter =
        User.builder()
            .id(reporterId)
            .email("reporter@occupify.app")
            .fullName("Reporter User")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    User targetUser =
        User.builder()
            .id(targetUserId)
            .email("target@occupify.app")
            .fullName("Target User")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    when(reportRepository.existsById(reportId)).thenReturn(false);
    when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
    when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));

    ReportCreatedEvent event =
        new ReportCreatedEvent(
            reportId,
            reporterId,
            "USER",
            targetUserId,
            null,
            "FRAUD",
            "Financial fraud details",
            List.of(
                "https://evidence.example.com/screenshot1.png",
                "https://evidence.example.com/log.pdf"),
            "PENDING",
            createdAt);

    listener.onReportCreated(event);

    ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
    verify(reportRepository).save(reportCaptor.capture());

    Report savedReport = reportCaptor.getValue();
    assertThat(savedReport.getId()).isEqualTo(reportId);
    assertThat(savedReport.getReporterUser()).isEqualTo(reporter);
    assertThat(savedReport.getTargetType()).isEqualTo(ReportTargetType.USER);
    assertThat(savedReport.getTargetUser()).isEqualTo(targetUser);
    assertThat(savedReport.getTargetProject()).isNull();
    assertThat(savedReport.getReasonCategory()).isEqualTo(ReportReasonCategory.FRAUD);
    assertThat(savedReport.getDescription()).isEqualTo("Financial fraud details");
    assertThat(savedReport.getStatus()).isEqualTo(ReportStatus.PENDING);
    assertThat(savedReport.getCreatedAt()).isEqualTo(createdAt);

    ArgumentCaptor<ReportEvidence> evidenceCaptor = ArgumentCaptor.forClass(ReportEvidence.class);
    verify(reportEvidenceRepository, times(2)).save(evidenceCaptor.capture());
    List<ReportEvidence> savedEvidences = evidenceCaptor.getAllValues();
    assertThat(savedEvidences).hasSize(2);
    assertThat(savedEvidences.get(0).getFileUrl())
        .isEqualTo("https://evidence.example.com/screenshot1.png");
    assertThat(savedEvidences.get(1).getFileUrl())
        .isEqualTo("https://evidence.example.com/log.pdf");
  }

  @Test
  @DisplayName("Should create stub user if reporter does not exist")
  void onReportCreated_whenReporterNotFound_createsStubUser() {
    UUID reportId = UUID.randomUUID();
    UUID reporterId = UUID.randomUUID();
    Instant createdAt = Instant.now();

    when(reportRepository.existsById(reportId)).thenReturn(false);
    when(userRepository.findById(reporterId)).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ReportCreatedEvent event =
        new ReportCreatedEvent(
            reportId,
            reporterId,
            "PROJECT",
            null,
            UUID.randomUUID(),
            "OTHER",
            "Unknown issue",
            null,
            "PENDING",
            createdAt);

    listener.onReportCreated(event);

    verify(userRepository).save(any(User.class));
    verify(reportRepository).save(any(Report.class));
  }
}
