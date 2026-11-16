package com.occupify.administration.service.impl;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.UpdateReportStatusRequest;
import com.occupify.administration.dto.response.AdminReportDetailResponse;
import com.occupify.administration.dto.response.AdminReportResponse;
import com.occupify.administration.entity.Report;
import com.occupify.administration.entity.ReportEvidence;
import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import com.occupify.administration.event.ReportUpdatedEvent;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.repository.ReportEvidenceRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.AdminReportService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminReportServiceImpl implements AdminReportService {

  private final ReportRepository reportRepository;
  private final ReportEvidenceRepository reportEvidenceRepository;
  private final ResponseFactory responseFactory;
  private final RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.report-exchange:occupify.report.exchange}")
  private String reportExchangeName;

  @Value("${app.rabbitmq.report-updated-routing-key:report.updated}")
  private String reportUpdatedRoutingKey;

  @Override
  public PageResponse<AdminReportResponse> listReports(
      Pageable pageable,
      ReportStatus status,
      ReportTargetType targetType,
      ReportReasonCategory reasonCategory) {
    log.info(
        "[Admin Report Service] Querying reports with status={}, targetType={}, reasonCategory={}, pageable={}",
        status,
        targetType,
        reasonCategory,
        pageable);

    Specification<Report> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (targetType != null) {
            predicates.add(cb.equal(root.get("targetType"), targetType));
          }
          if (reasonCategory != null) {
            predicates.add(cb.equal(root.get("reasonCategory"), reasonCategory));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<Report> reportPage = reportRepository.findAll(spec, pageable);

    List<AdminReportResponse> content =
        reportPage.getContent().stream().map(this::toAdminReportResponse).toList();

    return responseFactory.createPageResponse(
        "Reports retrieved successfully",
        content,
        reportPage.getNumber(),
        reportPage.getSize(),
        reportPage.getTotalElements(),
        reportPage.getTotalPages());
  }

  @Override
  public AdminReportDetailResponse getReportById(UUID id) {
    log.info("[Admin Report Service] Fetching report details for id: {}", id);

    Report report =
        reportRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new AdminException(
                        AdminErrorCode.REPORT_NOT_FOUND, "Report not found with id: " + id));

    List<ReportEvidence> evidences = reportEvidenceRepository.findByReportId(id);
    List<String> evidenceUrls =
        evidences.stream().map(ReportEvidence::getFileUrl).filter(url -> url != null).toList();

    return new AdminReportDetailResponse(
        report.getId(),
        report.getReporterUser() != null ? report.getReporterUser().getId() : null,
        report.getReporterUser() != null ? report.getReporterUser().getEmail() : null,
        report.getTargetType(),
        report.getTargetUser() != null ? report.getTargetUser().getId() : null,
        report.getTargetProject() != null ? report.getTargetProject().getId() : null,
        report.getReasonCategory(),
        report.getDescription(),
        report.getStatus(),
        report.getCreatedAt(),
        report.getUpdatedAt(),
        evidenceUrls);
  }

  @Override
  @Transactional
  public AdminReportResponse updateReportStatus(
      UUID id, UpdateReportStatusRequest request, AdminContext adminContext) {
    log.info(
        "[Admin Report Service] Updating report [{}] status to {} by admin [{}]",
        id,
        request.status(),
        adminContext != null ? adminContext.adminId() : null);

    Report report =
        reportRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new AdminException(
                        AdminErrorCode.REPORT_NOT_FOUND, "Report not found with id: " + id));

    report.setStatus(request.status());
    Instant now = Instant.now();
    report.setUpdatedAt(now);
    report = reportRepository.save(report);

    UUID adminId = adminContext != null ? adminContext.adminId() : null;
    ReportUpdatedEvent event =
        new ReportUpdatedEvent(
            report.getId(), report.getStatus().name(), adminId, request.note(), now);

    try {
      rabbitTemplate.convertAndSend(reportExchangeName, reportUpdatedRoutingKey, event);
      log.info(
          "[Admin Report Service] Dispatched ReportUpdatedEvent for report [{}] to exchange [{}] key [{}]",
          report.getId(),
          reportExchangeName,
          reportUpdatedRoutingKey);
    } catch (Exception e) {
      log.error(
          "[Admin Report Service] Failed to dispatch ReportUpdatedEvent for report [{}]: {}",
          report.getId(),
          e.getMessage(),
          e);
    }

    return toAdminReportResponse(report);
  }

  private AdminReportResponse toAdminReportResponse(Report report) {
    return new AdminReportResponse(
        report.getId(),
        report.getReporterUser() != null ? report.getReporterUser().getId() : null,
        report.getReporterUser() != null ? report.getReporterUser().getEmail() : null,
        report.getTargetType(),
        report.getTargetUser() != null ? report.getTargetUser().getId() : null,
        report.getTargetProject() != null ? report.getTargetProject().getId() : null,
        report.getReasonCategory(),
        report.getDescription(),
        report.getStatus(),
        report.getCreatedAt(),
        report.getUpdatedAt());
  }
}
