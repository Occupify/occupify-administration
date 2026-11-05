package com.occupify.administration.dto.response;

import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
    UUID id,
    String email,
    String fullName,
    String avatarUrl,
    UserRole role,
    UserStatus status,
    Instant createdAt,
    Instant deletedAt,
    boolean isFlagged,
    String flagReason,
    long totalCompletedContracts,
    long totalContracts,
    long disputeCount,
    List<AdminUserContractSummaryResponse> recentContracts
) {}

