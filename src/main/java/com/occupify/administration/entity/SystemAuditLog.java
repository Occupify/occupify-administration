package com.occupify.administration.entity;

import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.AuditStatus;
import com.occupify.administration.enums.TargetService;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "system_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "actor_id")
  private User actor;

  @Enumerated(EnumType.STRING)
  @Column(name = "actor_role", nullable = false, length = 50)
  private ActorRole actorRole;

  @Column(nullable = false, length = 100)
  private String action;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_service", nullable = false, length = 50)
  private TargetService targetService;

  @Column(name = "target_entity", nullable = false, length = 100)
  private String targetEntity;

  @Column(name = "target_id", length = 100)
  private String targetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private AuditStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  public void prePersist() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
