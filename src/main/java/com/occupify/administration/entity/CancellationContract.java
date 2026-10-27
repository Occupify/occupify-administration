package com.occupify.administration.entity;

import com.occupify.administration.enums.CancellationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cancellation_contracts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationContract {

  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contract_id", nullable = false)
  private Contract contract;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "requested_by_user_id", nullable = false)
  private User requestedByUser;

  @Column(columnDefinition = "TEXT")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CancellationStatus status;
}
