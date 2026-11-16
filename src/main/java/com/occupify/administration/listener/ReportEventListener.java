package com.occupify.administration.listener;

import com.occupify.administration.entity.Project;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "${app.rabbitmq.report-queue:occupify.administration.report.queue}")
public class ReportEventListener {

  private final ReportRepository reportRepository;
  private final ReportEvidenceRepository reportEvidenceRepository;
  private final UserRepository userRepository;
  private final ProjectRepository projectRepository;

  @Transactional
  @RabbitHandler
  public void onReportCreated(ReportCreatedEvent event) {
    if (event == null || event.reportId() == null || event.reporterUserId() == null) {
      log.warn("[Admin Report Listener] Received invalid ReportCreatedEvent: {}", event);
      return;
    }

    if (reportRepository.existsById(event.reportId())) {
      log.info(
          "[Admin Report Listener] Report [{}] already exists, skipping duplicate event",
          event.reportId());
      return;
    }

    log.info(
        "[Admin Report Listener] Processing ReportCreatedEvent for report [{}], reporter [{}]",
        event.reportId(),
        event.reporterUserId());

    User reporterUser =
        userRepository
            .findById(event.reporterUserId())
            .orElseGet(
                () -> {
                  log.warn(
                      "[Admin Report Listener] Reporter [{}] not found; creating stub user",
                      event.reporterUserId());
                  return userRepository.save(
                      User.builder()
                          .id(event.reporterUserId())
                          .email("reporter-" + event.reporterUserId() + "@occupify.local")
                          .fullName("Reporter " + event.reporterUserId().toString().substring(0, 8))
                          .role(UserRole.USER)
                          .status(UserStatus.ACTIVE)
                          .createdAt(Instant.now())
                          .build());
                });

    User targetUser = null;
    if (event.targetUserId() != null) {
      final UUID targetId = event.targetUserId();
      targetUser =
          userRepository
              .findById(targetId)
              .orElseGet(
                  () -> {
                    log.info(
                        "[Admin Report Listener] Target user [{}] not found in admin database; creating stub user",
                        targetId);
                    return userRepository.save(
                        User.builder()
                            .id(targetId)
                            .email("user-" + targetId + "@occupify.local")
                            .fullName("User " + targetId.toString().substring(0, 8))
                            .role(UserRole.USER)
                            .status(UserStatus.ACTIVE)
                            .createdAt(Instant.now())
                            .build());
                  });
    }

    Project targetProject = null;
    if (event.targetProjectId() != null) {
      final UUID projId = event.targetProjectId();
      targetProject =
          projectRepository
              .findById(projId)
              .orElseGet(
                  () -> {
                    log.info(
                        "[Admin Report Listener] Target project [{}] not found in admin database; creating stub project",
                        projId);
                    return projectRepository.save(
                        Project.builder()
                            .id(projId)
                            .employer(reporterUser)
                            .title("Project " + projId.toString().substring(0, 8))
                            .status(com.occupify.administration.enums.ProjectStatus.OPEN)
                            .createdAt(Instant.now())
                            .build());
                  });
    }

    ReportTargetType targetType = parseTargetType(event.targetType());
    ReportReasonCategory reasonCategory = parseReasonCategory(event.reasonCategory());
    ReportStatus status = parseStatus(event.status());

    Instant createdAt = event.createdAt() != null ? event.createdAt() : Instant.now();

    Report report =
        Report.builder()
            .id(event.reportId())
            .reporterUser(reporterUser)
            .targetType(targetType)
            .targetUser(targetUser)
            .targetProject(targetProject)
            .reasonCategory(reasonCategory)
            .description(event.description())
            .status(status)
            .createdAt(createdAt)
            .updatedAt(createdAt)
            .build();

    reportRepository.save(report);

    if (event.evidenceUrls() != null && !event.evidenceUrls().isEmpty()) {
      for (String fileUrl : event.evidenceUrls()) {
        if (fileUrl != null && !fileUrl.isBlank()) {
          ReportEvidence evidence =
              ReportEvidence.builder()
                  .id(UUID.randomUUID())
                  .report(report)
                  .fileUrl(fileUrl.trim())
                  .createdAt(createdAt)
                  .build();
          reportEvidenceRepository.save(evidence);
        }
      }
    }

    log.info(
        "[Admin Report Listener] Successfully persisted report [{}] with {} evidences",
        report.getId(),
        event.evidenceUrls() != null ? event.evidenceUrls().size() : 0);
  }

  private ReportTargetType parseTargetType(String typeStr) {
    if (typeStr == null) {
      return ReportTargetType.USER;
    }
    try {
      return ReportTargetType.valueOf(typeStr.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("[Admin Report Listener] Unknown target type [{}], defaulting to USER", typeStr);
      return ReportTargetType.USER;
    }
  }

  private ReportReasonCategory parseReasonCategory(String categoryStr) {
    if (categoryStr == null) {
      return ReportReasonCategory.OTHER;
    }
    try {
      return ReportReasonCategory.valueOf(categoryStr.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn(
          "[Admin Report Listener] Unknown reason category [{}], defaulting to OTHER", categoryStr);
      return ReportReasonCategory.OTHER;
    }
  }

  private ReportStatus parseStatus(String statusStr) {
    if (statusStr == null) {
      return ReportStatus.PENDING;
    }
    try {
      return ReportStatus.valueOf(statusStr.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("[Admin Report Listener] Unknown status [{}], defaulting to PENDING", statusStr);
      return ReportStatus.PENDING;
    }
  }
}
