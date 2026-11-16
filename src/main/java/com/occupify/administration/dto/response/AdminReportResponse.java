package com.occupify.administration.dto.response;

import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import com.occupify.administration.enums.ReportTargetType;
import java.time.Instant;
import java.util.UUID;

public record AdminReportResponse(
    UUID id,
    UUID reporterId,
    String reporterEmail,
    ReportTargetType targetType,
    UUID targetUserId,
    UUID targetProjectId,
    ReportReasonCategory reasonCategory,
    String description,
    ReportStatus status,
    Instant createdAt,
    Instant updatedAt) {}
