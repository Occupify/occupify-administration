package com.occupify.administration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.administration.entity.Contract;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.AuditStatus;
import com.occupify.administration.enums.ContractStatus;
import com.occupify.administration.enums.SalaryType;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ContractAcceptedEvent;
import com.occupify.administration.event.ContractCompensationRequestedEvent;
import com.occupify.administration.event.ContractRejectedEvent;
import com.occupify.administration.repository.ContractRepository;
import com.occupify.administration.repository.SystemAuditLogRepository;
import com.occupify.administration.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractEventListenerTest {

  @Mock private ContractRepository contractRepository;
  @Mock private SystemAuditLogRepository auditLogRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private ContractEventListener contractEventListener;

  private final UUID employerId = UUID.randomUUID();
  private final UUID freelancerId = UUID.randomUUID();
  private final UUID contractId = UUID.randomUUID();
  private final UUID jobId = UUID.randomUUID();

  @Test
  @DisplayName("Should update contract to IN_PROGRESS and log CONTRACT_ACCEPTED audit entry")
  void handleContractAccepted_UpdatesStatusAndLogsAudit() {
    User employer = User.builder().id(employerId).email("emp@occupify.local").fullName("Employer").role(UserRole.USER).status(UserStatus.ACTIVE).build();
    User freelancer = User.builder().id(freelancerId).email("free@occupify.local").fullName("Freelancer").role(UserRole.USER).status(UserStatus.ACTIVE).build();
    Contract contract = Contract.builder().id(contractId).jobId(jobId).employer(employer).employee(freelancer).status(ContractStatus.IN_PROGRESS).salaryType(SalaryType.FIXED).build();

    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(userRepository.findById(freelancerId)).thenReturn(Optional.of(freelancer));

    ContractAcceptedEvent event =
        new ContractAcceptedEvent(
            UUID.randomUUID(), contractId, jobId, UUID.randomUUID(),
            employerId, freelancerId, "Job Title", new BigDecimal("500.00"),
            "FIXED", LocalDate.now(), LocalDate.now().plusDays(10), LocalDateTime.now());

    contractEventListener.handleContractAccepted(event);

    assertThat(contract.getStatus()).isEqualTo(ContractStatus.IN_PROGRESS);
    assertThat(contract.getStartedAt()).isNotNull();
    verify(contractRepository).save(contract);

    ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(captor.capture());

    SystemAuditLog audit = captor.getValue();
    assertThat(audit.getAction()).isEqualTo("CONTRACT_ACCEPTED");
    assertThat(audit.getActorRole()).isEqualTo(ActorRole.EMPLOYEE);
    assertThat(audit.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    assertThat(audit.getTargetId()).isEqualTo(contractId.toString());
  }

  @Test
  @DisplayName("Should mark contract as CANCELLED and log CONTRACT_REJECTED audit entry")
  void handleContractRejected_MarksCancelledAndLogsAudit() {
    Contract contract = Contract.builder().id(contractId).jobId(jobId).status(ContractStatus.IN_PROGRESS).build();
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));
    when(userRepository.findById(freelancerId)).thenReturn(Optional.empty());

    ContractRejectedEvent event =
        new ContractRejectedEvent(
            UUID.randomUUID(), contractId, jobId, employerId, freelancerId, "Not suitable", LocalDateTime.now());

    contractEventListener.handleContractRejected(event);

    assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
    assertThat(contract.getFinishedAt()).isNotNull();
    verify(contractRepository).save(contract);

    ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(captor.capture());

    SystemAuditLog audit = captor.getValue();
    assertThat(audit.getAction()).isEqualTo("CONTRACT_REJECTED");
    assertThat(audit.getStatus()).isEqualTo(AuditStatus.SUCCESS);
  }

  @Test
  @DisplayName("Should cancel contract and log CONTRACT_COMPENSATION_EXECUTED audit entry")
  void handleContractCompensation_RevertsToPendingAndLogsFailure() {
    Contract contract = Contract.builder().id(contractId).jobId(jobId).status(ContractStatus.IN_PROGRESS).build();
    when(contractRepository.findById(contractId)).thenReturn(Optional.of(contract));

    ContractCompensationRequestedEvent event =
        new ContractCompensationRequestedEvent(
            UUID.randomUUID(), contractId, "occupify-payment", "INSUFFICIENT_FUNDS", "Payment failed", LocalDateTime.now());

    contractEventListener.handleContractCompensation(event);

    assertThat(contract.getStatus()).isEqualTo(ContractStatus.CANCELLED);
    assertThat(contract.getStartedAt()).isNull();
    verify(contractRepository).save(contract);

    ArgumentCaptor<SystemAuditLog> captor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(captor.capture());

    SystemAuditLog audit = captor.getValue();
    assertThat(audit.getAction()).isEqualTo("CONTRACT_COMPENSATION_EXECUTED");
    assertThat(audit.getActorRole()).isEqualTo(ActorRole.SYSTEM);
    assertThat(audit.getStatus()).isEqualTo(AuditStatus.FAILED);
  }

  @Test
  @DisplayName("Should not synchronize contract replica or audit log on ContractCreatedEvent (deferred until acceptance)")
  void handleContractCreated_DefersSynchronization() {
    com.occupify.administration.event.ContractCreatedEvent event =
        new com.occupify.administration.event.ContractCreatedEvent(
            UUID.randomUUID(),
            contractId,
            jobId,
            UUID.randomUUID(),
            employerId,
            freelancerId,
            "free@occupify.local",
            "Welcome aboard",
            "Backend Engineer",
            "Spring Specialist",
            BigDecimal.valueOf(30000000),
            "MONTHLY",
            LocalDateTime.now(),
            LocalDateTime.now().plusMonths(3),
            LocalDateTime.now());

    contractEventListener.handleContractCreated(event);

    verify(contractRepository, never()).save(any());
    verify(auditLogRepository, never()).save(any());
  }
}
