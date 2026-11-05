package com.occupify.administration.dto.request;

import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;

public record AdminUserFilterRequest(
    String keyword,
    UserStatus status,
    UserRole role
) {}

