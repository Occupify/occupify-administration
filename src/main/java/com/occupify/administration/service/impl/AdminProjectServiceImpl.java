package com.occupify.administration.service.impl;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminProjectFilterRequest;
import com.occupify.administration.dto.response.AdminProjectDetailResponse;
import com.occupify.administration.dto.response.AdminProjectEmployerResponse;
import com.occupify.administration.dto.response.AdminProjectReportItemResponse;
import com.occupify.administration.dto.response.AdminProjectResponse;
import com.occupify.administration.entity.Project;
import com.occupify.administration.entity.Report;
import com.occupify.administration.entity.User;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.repository.ProjectRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.service.AdminProjectService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminProjectServiceImpl implements AdminProjectService {

    private final ProjectRepository projectRepository;
    private final ReportRepository reportRepository;
    private final ResponseFactory responseFactory;

    @Override
    public PageResponse<AdminProjectResponse> getProjects(AdminProjectFilterRequest filter, Pageable pageable) {
        log.info("[Admin] Querying projects with filter: {}, pageable: {}", filter, pageable);

        Specification<Project> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (StringUtils.hasText(filter.keyword())) {
                    String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("title")), pattern),
                            cb.like(cb.lower(root.get("description")), pattern)
                    ));
                }

                if (filter.status() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.status()));
                }

                if (filter.employerId() != null) {
                    predicates.add(cb.equal(root.get("employer").get("id"), filter.employerId()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Project> projectPage = projectRepository.findAll(spec, pageable);

        List<AdminProjectResponse> content = projectPage.getContent().stream()
                .map(this::toAdminProjectResponse)
                .toList();

        return responseFactory.createPageResponse(
                "Projects retrieved successfully",
                content,
                projectPage.getNumber(),
                projectPage.getSize(),
                projectPage.getTotalElements(),
                projectPage.getTotalPages()
        );
    }

    @Override
    public AdminProjectDetailResponse getProjectDetail(UUID id) {
        log.info("[Admin] Fetching project details for id: {}", id);

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new AdminException(AdminErrorCode.PROJECT_NOT_FOUND, "Project not found with id: " + id));

        long reportCount = reportRepository.countByTargetProjectId(id);
        List<AdminProjectReportItemResponse> reports = reportRepository.findByTargetProjectIdOrderByCreatedAtDesc(id)
                .stream()
                .map(this::toReportItemResponse)
                .toList();

        AdminProjectEmployerResponse employerResponse = toEmployerResponse(project.getEmployer());

        return new AdminProjectDetailResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getStatus(),
                employerResponse,
                project.getCreatedAt(),
                project.getClosedAt(),
                reportCount,
                reports
        );
    }

    private AdminProjectResponse toAdminProjectResponse(Project project) {
        long reportCount = reportRepository.countByTargetProjectId(project.getId());
        AdminProjectEmployerResponse employerResponse = toEmployerResponse(project.getEmployer());

        return new AdminProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                project.getStatus(),
                employerResponse,
                project.getCreatedAt(),
                project.getClosedAt(),
                reportCount
        );
    }

    private AdminProjectEmployerResponse toEmployerResponse(User employer) {
        if (employer == null) {
            return null;
        }
        return new AdminProjectEmployerResponse(
                employer.getId(),
                employer.getEmail(),
                employer.getFullName(),
                employer.getAvatarUrl()
        );
    }

    private AdminProjectReportItemResponse toReportItemResponse(Report report) {
        UUID reporterId = report.getReporterUser() != null ? report.getReporterUser().getId() : null;
        String reporterName = report.getReporterUser() != null ? report.getReporterUser().getFullName() : "Anonymous";

        return new AdminProjectReportItemResponse(
                report.getId(),
                reporterId,
                reporterName,
                report.getReasonCategory(),
                report.getDescription(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}

