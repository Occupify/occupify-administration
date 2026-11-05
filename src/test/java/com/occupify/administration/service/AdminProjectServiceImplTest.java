package com.occupify.administration.service;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import com.occupify.administration.entity.Project;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ProjectStatus;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.repository.ProjectRepository;
import com.occupify.administration.repository.ReportRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProjectServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ReportRepository reportRepository;

    @Spy
    private ResponseFactory responseFactory = new ResponseFactory();

    @InjectMocks
    private AdminProjectServiceImpl projectService;

    private Project sampleProject;
    private User sampleEmployer;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        sampleEmployer = User.builder()
                .id(UUID.randomUUID())
                .email("employer@occupify.app")
                .fullName("Nexus Fintech")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        sampleProject = Project.builder()
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

        AdminProjectFilterRequest filter = new AdminProjectFilterRequest("Spring", ProjectStatus.OPEN, null);
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
        when(reportRepository.findByTargetProjectIdOrderByCreatedAtDesc(any())).thenReturn(Collections.emptyList());

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
}

