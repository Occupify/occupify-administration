package com.occupify.administration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import com.occupify.administration.entity.Project;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.ProjectStatus;
import com.occupify.administration.enums.TargetService;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ProjectCloseRequestedEvent;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.producer.AdminOutboxService;
import com.occupify.administration.repository.ProjectRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.repository.SystemAuditLogRepository;
import com.occupify.administration.repository.UserRepository;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.impl.AdminProjectServiceImpl;
import java.time.Instant;
import java.util.Collections;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminProjectServiceImplTest {

  @Mock private ProjectRepository projectRepository;

  @Mock private ReportRepository reportRepository;

  @Mock private UserRepository userRepository;

  @Mock private SystemAuditLogRepository auditLogRepository;

  @Mock private AdminOutboxService adminOutboxService;

  @Spy private ResponseFactory responseFactory = new ResponseFactory();

  @InjectMocks private AdminProjectServiceImpl projectService;

  private Project sampleProject;
  private User sampleEmployer;
  private UUID projectId;
  private AdminContext adminContext;

  @BeforeEach
  void setUp() {
    projectId = UUID.randomUUID();
    adminContext = new AdminContext(UUID.randomUUID(), "admin@occupify.app", UserRole.ADMIN);
    sampleEmployer =
        User.builder()
            .id(UUID.randomUUID())
            .email("employer@occupify.app")
            .fullName("Nexus Fintech")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .build();

    sampleProject =
        Project.builder()
            .id(projectId)
            .employer(sampleEmployer)
            .title("Spring Boot Microservices")
            .description("Build scalable microservices architecture")
            .status(ProjectStatus.OPEN)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("getProjects - should return paginated list of projects")
  void getProjects_shouldReturnPaginatedProjects() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<Project> page = new PageImpl<>(List.of(sampleProject), pageable, 1);

    when(projectRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(reportRepository.countByTargetProjectId(any())).thenReturn(2L);

    AdminProjectFilterRequest filter =
        new AdminProjectFilterRequest("Spring", ProjectStatus.OPEN, null);
    PageResponse<AdminProjectResponse> result = projectService.getProjects(filter, pageable);

    assertThat(result).isNotNull();
    assertThat(result.data()).hasSize(1);
    AdminProjectResponse response = result.data().get(0);
    assertThat(response.id()).isEqualTo(projectId);
    assertThat(response.title()).isEqualTo("Spring Boot Microservices");
    assertThat(response.employer().fullName()).isEqualTo("Nexus Fintech");
    assertThat(response.reportCount()).isEqualTo(2L);
  }

  @Test
  @DisplayName("getProjectDetail - should return full detail when project exists")
  void getProjectDetail_shouldReturnDetail() {
    when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(sampleProject));
    when(reportRepository.countByTargetProjectId(any())).thenReturn(0L);
    when(reportRepository.findByTargetProjectIdOrderByCreatedAtDesc(any()))
        .thenReturn(Collections.emptyList());

    AdminProjectDetailResponse result = projectService.getProjectDetail(projectId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(projectId);
    assertThat(result.title()).isEqualTo("Spring Boot Microservices");
    assertThat(result.employer().email()).isEqualTo("employer@occupify.app");
    assertThat(result.reports()).isEmpty();
  }

  @Test
  @DisplayName("getProjectDetail - should throw PROJECT_NOT_FOUND when project does not exist")
  void getProjectDetail_shouldThrow_whenProjectNotFound() {
    when(projectRepository.findById(eq(projectId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.getProjectDetail(projectId))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.PROJECT_NOT_FOUND.code());
  }

  @Test
  @DisplayName("closeProject - should mark OPEN project CLOSED, log audit, and enqueue event")
  void closeProject_shouldCloseOpenProject() {
    when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(sampleProject));
    when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userRepository.findById(adminContext.adminId())).thenReturn(Optional.empty());
    when(reportRepository.countByTargetProjectId(projectId)).thenReturn(1L);

    AdminProjectResponse result =
        projectService.closeProject(projectId, "Fraudulent listing", adminContext);

    assertThat(result.status()).isEqualTo(ProjectStatus.CLOSED);
    assertThat(result.closedAt()).isNotNull();

    ArgumentCaptor<SystemAuditLog> auditCaptor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getAction()).isEqualTo("PROJECT_CLOSED");
    assertThat(auditCaptor.getValue().getActorRole()).isEqualTo(ActorRole.ADMIN);
    assertThat(auditCaptor.getValue().getTargetService()).isEqualTo(TargetService.CORE);

    ArgumentCaptor<ProjectCloseRequestedEvent> eventCaptor =
        ArgumentCaptor.forClass(ProjectCloseRequestedEvent.class);
    verify(adminOutboxService)
        .enqueueAndDispatch(
            eq("PROJECT"),
            eq(projectId),
            eq("occupify.project.exchange"),
            eq("project.close.requested"),
            eq(AdminOutboxService.EVENT_TYPE_PROJECT_CLOSE_REQUESTED),
            eventCaptor.capture());
    assertThat(eventCaptor.getValue().projectId()).isEqualTo(projectId);
    assertThat(eventCaptor.getValue().reason()).isEqualTo("Fraudulent listing");
  }

  @Test
  @DisplayName("closeProject - should return idempotently when project is already CLOSED")
  void closeProject_shouldBeIdempotent_whenAlreadyClosed() {
    sampleProject.setStatus(ProjectStatus.CLOSED);
    sampleProject.setClosedAt(Instant.now());
    when(projectRepository.findById(eq(projectId))).thenReturn(Optional.of(sampleProject));
    when(reportRepository.countByTargetProjectId(projectId)).thenReturn(0L);

    AdminProjectResponse result = projectService.closeProject(projectId, null, adminContext);

    assertThat(result.status()).isEqualTo(ProjectStatus.CLOSED);
    verify(projectRepository, never()).save(any());
    verify(auditLogRepository, never()).save(any());
    verify(adminOutboxService, never())
        .enqueueAndDispatch(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("closeProject - should throw PROJECT_NOT_FOUND when project does not exist")
  void closeProject_shouldThrow_whenProjectNotFound() {
    when(projectRepository.findById(eq(projectId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> projectService.closeProject(projectId, null, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.PROJECT_NOT_FOUND.code());
  }
}
