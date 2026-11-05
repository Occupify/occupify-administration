package com.occupify.administration.dto.response;

import java.util.UUID;

public record AdminProjectEmployerResponse(
    UUID id,
    String email,
    String fullName,
    String avatarUrl
) {}

