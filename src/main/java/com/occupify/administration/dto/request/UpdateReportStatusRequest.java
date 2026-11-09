package com.occupify.administration.dto.request;

import com.occupify.administration.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateReportStatusRequest(
    @NotNull(message = "Status cannot be null") ReportStatus status, String note) {}
