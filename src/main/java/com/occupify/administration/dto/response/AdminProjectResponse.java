package com.occupify.administration.dto.response;

import com.occupify.administration.enums.ProjectStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminProjectResponse(
    UUID id,
    String title,
    String description,
    ProjectStatus status,
    AdminProjectEmployerResponse employer,
    Instant createdAt,
    Instant closedAt,
    long reportCount
) {}

