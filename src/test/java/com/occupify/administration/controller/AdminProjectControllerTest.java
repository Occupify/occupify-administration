package com.occupify.administration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectEmployerResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import com.occupify.administration.enums.ProjectStatus;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.AdminProjectService;
import java.time.Instant;
import java.util.Collections;
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
class AdminProjectControllerTest {

  @Mock private AdminProjectService adminProjectService;

  @Spy private ResponseFactory responseFactory = new ResponseFactory();

  @InjectMocks private AdminProjectController controller;

  private AdminContext adminContext;

  @BeforeEach
  void setUp() {
    adminContext = new AdminContext(UUID.randomUUID(), "admin@occupify.app", UserRole.ADMIN);
  }

  @Test
  @DisplayName("GET /admin/projects - Should return 200 OK with paginated projects")
  void getProjects_shouldReturnPaginatedProjects() {
    AdminProjectFilterRequest filter =
        new AdminProjectFilterRequest("backend", ProjectStatus.OPEN, null);
    Pageable pageable = PageRequest.of(0, 20);

    AdminProjectEmployerResponse employer =
        new AdminProjectEmployerResponse(
            UUID.randomUUID(), "employer@example.com", "Employer Co", null);

    AdminProjectResponse projectResponse =
        new AdminProjectResponse(
            UUID.randomUUID(),
            "Spring Boot Microservice",
            "Description",
            ProjectStatus.OPEN,
            employer,
            Instant.now(),
            null,
            0);

    PageResponse<AdminProjectResponse> pageResponse =
        new PageResponse<>(
            200,
            "Projects retrieved successfully",
            List.of(projectResponse),
            new PageResponse.PagingInfo(0, 20, 1, 1));

    when(adminProjectService.getProjects(any(), any())).thenReturn(pageResponse);

    ResponseEntity<PageResponse<AdminProjectResponse>> response =
        controller.getProjects(filter, pageable, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().statusCode()).isEqualTo(200);
    assertThat(response.getBody().data()).hasSize(1);
    assertThat(response.getBody().data().get(0).title()).isEqualTo("Spring Boot Microservice");
  }

  @Test
  @DisplayName("GET /admin/projects/{id} - Should return project detail when project exists")
  void getProjectDetail_shouldReturnProjectDetail() {
    UUID projectId = UUID.randomUUID();
    AdminProjectEmployerResponse employer =
        new AdminProjectEmployerResponse(
            UUID.randomUUID(), "employer@example.com", "Employer Co", null);

    AdminProjectDetailResponse detail =
        new AdminProjectDetailResponse(
            projectId,
            "Spring Boot Microservice",
            "Detailed Description",
            ProjectStatus.OPEN,
            employer,
            Instant.now(),
            null,
            0,
            Collections.emptyList());

    when(adminProjectService.getProjectDetail(eq(projectId))).thenReturn(detail);

    var response = controller.getProjectDetail(projectId, adminContext);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().data()).isNotNull();
    assertThat(response.getBody().data().id()).isEqualTo(projectId);
    assertThat(response.getBody().data().title()).isEqualTo("Spring Boot Microservice");
  }

  @Test
  @DisplayName("GET /admin/projects/{id} - Should throw AdminException when project not found")
  void getProjectDetail_shouldThrow_whenProjectNotFound() {
    UUID projectId = UUID.randomUUID();
    when(adminProjectService.getProjectDetail(eq(projectId)))
        .thenThrow(new AdminException(AdminErrorCode.PROJECT_NOT_FOUND));

    assertThatThrownBy(() -> controller.getProjectDetail(projectId, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.PROJECT_NOT_FOUND.code());
  }
}
