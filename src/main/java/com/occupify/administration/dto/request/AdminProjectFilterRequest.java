package com.occupify.administration.dto.request;

import com.occupify.administration.enums.ProjectStatus;
import java.util.UUID;

public record AdminProjectFilterRequest(
    String keyword,
    ProjectStatus status,
    UUID employerId
) {}

