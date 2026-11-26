package com.occupify.administration.dto.request;

import jakarta.validation.constraints.Size;

public record AccountModerationRequest(
    @Size(max = 1000, message = "Reason must not exceed 1000 characters") String reason) {}
