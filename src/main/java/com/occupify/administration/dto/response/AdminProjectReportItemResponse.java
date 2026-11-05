package com.occupify.administration.dto.response;

import com.occupify.administration.enums.ReportReasonCategory;
import com.occupify.administration.enums.ReportStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminProjectReportItemResponse(
    UUID id,
    UUID reporterId,
    String reporterName,
    ReportReasonCategory reasonCategory,
    String description,
    ReportStatus status,
    Instant createdAt
) {}

