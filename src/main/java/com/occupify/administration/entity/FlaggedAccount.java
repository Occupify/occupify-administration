package com.occupify.administration.entity;

import com.occupify.administration.enums.FlagActionTaken;
import com.occupify.administration.enums.FlagStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "flagged_accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlaggedAccount {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "admin_id")
  private User admin;

  @Column(name = "flag_reason", columnDefinition = "TEXT", nullable = false)
  private String flagReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private FlagStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_taken", nullable = false, length = 50)
  private FlagActionTaken actionTaken;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (status == null) {
      status = FlagStatus.PENDING;
    }
    if (actionTaken == null) {
      actionTaken = FlagActionTaken.NONE;
    }
  }
}
