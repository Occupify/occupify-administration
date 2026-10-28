package com.occupify.administration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
class ProjectEventListenerTest {

  @Mock private ProjectRepository projectRepository;
  @Mock private UserRepository userRepository;
  @Mock private SystemAuditLogRepository auditLogRepository;

  private ProjectEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new ProjectEventListener(projectRepository, userRepository, auditLogRepository);
  }

  @Test
  @DisplayName("Should persist project and audit log when employer already exists")
  void handleProjectCreated_whenEmployerExists_persistsProjectAndAuditLog() {
    UUID projectId = UUID.randomUUID();
    UUID employerId = UUID.randomUUID();
    User existingEmployer =
        User.builder()
            .id(employerId)
            .email("employer@occupify.local")
            .fullName("Employer Name")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    when(userRepository.findById(employerId)).thenReturn(Optional.of(existingEmployer));
    when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

    ProjectCreatedEvent event =
        new ProjectCreatedEvent(
            projectId,
            "Web Dev Project",
            "Build fullstack web app",
            "OPEN",
            employerId,
            "employer@occupify.local",
            Instant.parse("2026-10-27T10:00:00Z"));

    listener.handleProjectCreated(event);

    ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
    verify(projectRepository).save(projectCaptor.capture());
    Project savedProject = projectCaptor.getValue();

    assertThat(savedProject.getId()).isEqualTo(projectId);
    assertThat(savedProject.getTitle()).isEqualTo("Web Dev Project");
    assertThat(savedProject.getDescription()).isEqualTo("Build fullstack web app");
    assertThat(savedProject.getStatus()).isEqualTo(ProjectStatus.OPEN);
    assertThat(savedProject.getEmployer()).isEqualTo(existingEmployer);

    ArgumentCaptor<SystemAuditLog> auditCaptor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(auditCaptor.capture());
    SystemAuditLog savedAudit = auditCaptor.getValue();

    assertThat(savedAudit.getAction()).isEqualTo("PROJECT_CREATED");
    assertThat(savedAudit.getActor()).isEqualTo(existingEmployer);
    assertThat(savedAudit.getActorRole()).isEqualTo(ActorRole.EMPLOYER);
    assertThat(savedAudit.getTargetService()).isEqualTo(TargetService.CORE);
    assertThat(savedAudit.getTargetEntity()).isEqualTo("PROJECT");
    assertThat(savedAudit.getTargetId()).isEqualTo(projectId.toString());
    assertThat(savedAudit.getStatus()).isEqualTo(AuditStatus.SUCCESS);
  }

  @Test
  @DisplayName("Should create fallback employer user when not found, then persist project")
  void handleProjectCreated_whenEmployerMissing_createsFallbackEmployerAndProject() {
    UUID projectId = UUID.randomUUID();
    UUID employerId = UUID.randomUUID();

    when(userRepository.findById(employerId)).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

    ProjectCreatedEvent event =
        new ProjectCreatedEvent(
            projectId,
            "Mobile App Project",
            "Flutter frontend",
            "OPEN",
            employerId,
            "mobile-emp@test.local",
            Instant.parse("2026-10-27T11:00:00Z"));

    listener.handleProjectCreated(event);

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User fallbackUser = userCaptor.getValue();

    assertThat(fallbackUser.getId()).isEqualTo(employerId);
    assertThat(fallbackUser.getEmail()).isEqualTo("mobile-emp@test.local");
    assertThat(fallbackUser.getRole()).isEqualTo(UserRole.USER);
    assertThat(fallbackUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

    verify(projectRepository).save(any(Project.class));
    verify(auditLogRepository).save(any(SystemAuditLog.class));
  }

  @Test
  @DisplayName("Should gracefully skip invalid or null ProjectCreatedEvent")
  void handleProjectCreated_whenInvalidEvent_skipsProcessing() {
    listener.handleProjectCreated(null);
    listener.handleProjectCreated(new ProjectCreatedEvent(null, "Title", "Desc", "OPEN", UUID.randomUUID(), "e@x.com", Instant.now()));
    listener.handleProjectCreated(new ProjectCreatedEvent(UUID.randomUUID(), "Title", "Desc", "OPEN", null, "e@x.com", Instant.now()));

    verifyNoInteractions(projectRepository);
    verifyNoInteractions(userRepository);
    verifyNoInteractions(auditLogRepository);
  }

  @Test
  @DisplayName("Should update project status to CLOSED and record audit log on ProjectClosedEvent")
  void handleProjectClosed_whenProjectExists_updatesStatusToClosedAndLogsAudit() {
    UUID projectId = UUID.randomUUID();
    UUID employerId = UUID.randomUUID();
    User employer =
        User.builder()
            .id(employerId)
            .email("emp@occupify.local")
            .fullName("Employer")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    Project existingProject =
        Project.builder()
            .id(projectId)
            .title("Existing Project")
            .employer(employer)
            .status(ProjectStatus.OPEN)
            .build();

    when(projectRepository.findById(projectId)).thenReturn(Optional.of(existingProject));
    when(userRepository.findById(employerId)).thenReturn(Optional.of(employer));

    ProjectClosedEvent event =
        new ProjectClosedEvent(
            projectId,
            "Existing Project",
            employerId,
            "emp@occupify.local",
            employerId,
            "EMPLOYER",
            Instant.parse("2026-10-27T12:00:00Z"));

    listener.handleProjectClosed(event);

    assertThat(existingProject.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    assertThat(existingProject.getClosedAt()).isNotNull();
    verify(projectRepository).save(existingProject);

    ArgumentCaptor<SystemAuditLog> auditCaptor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(auditCaptor.capture());
    SystemAuditLog savedAudit = auditCaptor.getValue();

    assertThat(savedAudit.getAction()).isEqualTo("PROJECT_CLOSED");
    assertThat(savedAudit.getActor()).isEqualTo(employer);
    assertThat(savedAudit.getTargetId()).isEqualTo(projectId.toString());
  }

  @Test
  @DisplayName("Should gracefully skip invalid or null ProjectClosedEvent")
  void handleProjectClosed_whenInvalidEvent_skipsProcessing() {
    listener.handleProjectClosed(null);
    listener.handleProjectClosed(
        new ProjectClosedEvent(
            null, "Title", UUID.randomUUID(), "e@x.com", UUID.randomUUID(), "EMPLOYER", Instant.now()));

    verifyNoInteractions(projectRepository);
    verifyNoInteractions(auditLogRepository);
  }
}
