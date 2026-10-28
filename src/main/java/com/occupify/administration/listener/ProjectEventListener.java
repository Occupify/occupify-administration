package com.occupify.administration.listener;

import com.occupify.administration.entity.Project;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.AuditStatus;
import com.occupify.administration.enums.ProjectStatus;
import com.occupify.administration.enums.TargetService;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ProjectClosedEvent;
import com.occupify.administration.event.ProjectCreatedEvent;
import com.occupify.administration.repository.ProjectRepository;
import com.occupify.administration.repository.SystemAuditLogRepository;
import com.occupify.administration.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "${app.rabbitmq.project-queue:occupify.administration.project.queue}")
public class ProjectEventListener {

  private final ProjectRepository projectRepository;
  private final UserRepository userRepository;
  private final SystemAuditLogRepository auditLogRepository;

  @Transactional
  @RabbitHandler
  public void handleProjectCreated(ProjectCreatedEvent event) {
    if (event == null || event.projectId() == null || event.employerId() == null) {
      log.warn("[Admin Project Listener] Received invalid ProjectCreatedEvent: {}", event);
      return;
    }

    log.info(
        "[Admin Project Listener] Processing ProjectCreatedEvent for project [{}] ('{}')",
        event.projectId(),
        event.title());

    User employer =
        userRepository
            .findById(event.employerId())
            .orElseGet(
                () -> {
                  String email =
                      StringUtils.hasText(event.employerEmail())
                          ? event.employerEmail().trim().toLowerCase()
                          : "employer-" + event.employerId() + "@occupify.local";
                  log.info(
                      "[Admin Project Listener] Employer [{}] not found; creating stub user with email {}",
                      event.employerId(),
                      email);
                  return userRepository.save(
                      User.builder()
                          .id(event.employerId())
                          .email(email)
                          .fullName("Employer " + event.employerId().toString().substring(0, 8))
                          .role(UserRole.USER)
                          .status(UserStatus.ACTIVE)
                          .createdAt(Instant.now())
                          .build());
                });

    Project project =
        projectRepository
            .findById(event.projectId())
            .orElseGet(() -> Project.builder().id(event.projectId()).build());

    project.setEmployer(employer);
    project.setTitle(StringUtils.hasText(event.title()) ? event.title().trim() : "Untitled Project");
    project.setDescription(event.description());
    project.setStatus(parseProjectStatus(event.status()));

    Instant createdInstant =
        event.createdAt() != null ? event.createdAt() : Instant.now();
    project.setCreatedAt(createdInstant);

    projectRepository.save(project);

    SystemAuditLog auditLog =
        SystemAuditLog.builder()
            .id(UUID.randomUUID())
            .actor(employer)
            .actorRole(ActorRole.EMPLOYER)
            .action("PROJECT_CREATED")
            .targetService(TargetService.CORE)
            .targetEntity("PROJECT")
            .targetId(event.projectId().toString())
            .status(AuditStatus.SUCCESS)
            .createdAt(Instant.now())
            .build();
    auditLogRepository.save(auditLog);

    log.info(
        "[Admin Project Listener] Successfully persisted project [{}] and audit log",
        event.projectId());
  }

  @Transactional
  @RabbitHandler
  public void handleProjectClosed(ProjectClosedEvent event) {
    if (event == null || event.projectId() == null) {
      log.warn("[Admin Project Listener] Received invalid ProjectClosedEvent: {}", event);
      return;
    }

    log.info(
        "[Admin Project Listener] Processing ProjectClosedEvent for project [{}]", event.projectId());

    projectRepository
        .findById(event.projectId())
        .ifPresent(
            project -> {
              project.setStatus(ProjectStatus.CLOSED);
              Instant closedInstant =
                  event.closedAt() != null ? event.closedAt() : Instant.now();
              project.setClosedAt(closedInstant);
              projectRepository.save(project);

              User employer =
                  event.employerId() != null
                      ? userRepository.findById(event.employerId()).orElse(project.getEmployer())
                      : project.getEmployer();

              SystemAuditLog auditLog =
                  SystemAuditLog.builder()
                      .id(UUID.randomUUID())
                      .actor(employer)
                      .actorRole(ActorRole.EMPLOYER)
                      .action("PROJECT_CLOSED")
                      .targetService(TargetService.CORE)
                      .targetEntity("PROJECT")
                      .targetId(event.projectId().toString())
                      .status(AuditStatus.SUCCESS)
                      .createdAt(Instant.now())
                      .build();
              auditLogRepository.save(auditLog);

              log.info(
                  "[Admin Project Listener] Successfully marked project [{}] as CLOSED",
                  event.projectId());
            });
  }

  private ProjectStatus parseProjectStatus(String statusStr) {
    if (!StringUtils.hasText(statusStr)) {
      return ProjectStatus.OPEN;
    }
    try {
      return ProjectStatus.valueOf(statusStr.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("[Admin Project Listener] Unknown project status [{}], defaulting to OPEN", statusStr);
      return ProjectStatus.OPEN;
    }
  }
}
