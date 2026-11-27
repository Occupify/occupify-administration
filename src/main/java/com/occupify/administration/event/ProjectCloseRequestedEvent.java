package com.occupify.administration.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ProjectCloseRequestedEvent(
    UUID projectId, UUID requestedByAdminId, String reason, Instant requestedAt)
    implements Serializable {

  public ProjectCloseRequestedEvent {
    if (requestedAt == null) {
      requestedAt = Instant.now();
    }
  }
}
