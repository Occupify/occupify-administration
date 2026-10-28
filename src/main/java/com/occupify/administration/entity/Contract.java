package com.occupify.administration.entity;

import com.occupify.administration.enums.ContractStatus;
import com.occupify.administration.enums.SalaryType;
import com.occupify.administration.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contracts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {

  @Id
  private UUID id;

  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "employer_id", nullable = false)
  private User employer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "employee_id")
  private User employee;

  @Column(name = "title", length = 255)
  private String title;

  @Column(name = "role", length = 100)
  private String role;

  @Column(name = "freelancer_email")
  private String freelancerEmail;

  @Column(name = "message", columnDefinition = "TEXT")
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", length = 50)
  private SourceType sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(name = "agreed_rate", precision = 15, scale = 2)
  private BigDecimal agreedRate;

  @Enumerated(EnumType.STRING)
  @Column(name = "salary_type", length = 50)
  private SalaryType salaryType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private ContractStatus status;

  @Column(name = "payment_status", length = 50)
  private String paymentStatus;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;
}
