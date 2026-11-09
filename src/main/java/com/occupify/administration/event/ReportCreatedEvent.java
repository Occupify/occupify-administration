package com.occupify.administration.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportCreatedEvent(
    UUID reportId,
    UUID reporterUserId,
    String targetType,
    UUID targetUserId,
    UUID targetProjectId,
    String reasonCategory,
    String description,
    List<String> evidenceUrls,
    String status,
    Instant createdAt)
    implements Serializable {}
