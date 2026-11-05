package com.occupify.administration.dto.response;

import com.occupify.administration.enums.ContractStatus;
import com.occupify.administration.enums.SalaryType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminUserContractSummaryResponse(
    UUID id,
    String title,
    String role,
    BigDecimal agreedRate,
    SalaryType salaryType,
    ContractStatus status,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt
) {}

