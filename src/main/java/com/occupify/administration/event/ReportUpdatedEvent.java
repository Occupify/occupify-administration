package com.occupify.administration.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ReportUpdatedEvent(
    UUID reportId, String status, UUID moderatedByAdminId, String note, Instant updatedAt)
    implements Serializable {}
