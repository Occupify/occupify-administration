package com.occupify.administration.dto.response;

import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String fullName,
    String avatarUrl,
    UserRole role,
    UserStatus status,
    boolean isFlagged,
    long totalCompletedContracts,
    long disputeCount,
    Instant createdAt
) {}

