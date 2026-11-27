package com.occupify.administration.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record UserStatusChangedEvent(
    UUID userId, String status, String reason, UUID changedByAdminId, Instant occurredAt)
    implements Serializable {

  public UserStatusChangedEvent {
    if (occurredAt == null) {
      occurredAt = Instant.now();
    }
  }
}
