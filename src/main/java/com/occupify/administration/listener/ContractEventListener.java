package com.occupify.administration.listener;

import com.occupify.administration.entity.Contract;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.AuditStatus;
import com.occupify.administration.enums.ContractStatus;
import com.occupify.administration.enums.SalaryType;
import com.occupify.administration.enums.SourceType;
import com.occupify.administration.enums.TargetService;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ContractAcceptedEvent;
import com.occupify.administration.event.ContractCompensationRequestedEvent;
import com.occupify.administration.event.ContractCreatedEvent;
import com.occupify.administration.event.ContractRejectedEvent;
import com.occupify.administration.repository.ContractRepository;
import com.occupify.administration.repository.SystemAuditLogRepository;
import com.occupify.administration.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "${app.rabbitmq.contract-queue:occupify.administration.contract.queue}")
public class ContractEventListener {

  private final ContractRepository contractRepository;
  private final SystemAuditLogRepository auditLogRepository;
  private final UserRepository userRepository;

  @Transactional
  @RabbitHandler
  public void handleContractCreated(ContractCreatedEvent event) {
    if (event == null || event.contractId() == null) {
      log.warn("[Admin Saga] Received invalid ContractCreatedEvent");
      return;
    }
    log.info(
        "[Admin Saga] Received ContractCreatedEvent for contractId: [{}]. Replica synchronization deferred until acceptance.",
        event.contractId());
    // Do not synchronize contract replica or record audit log at invitation stage
  }

  @Transactional
  @RabbitHandler
  public void handleContractAccepted(ContractAcceptedEvent event) {
    log.info(
        "[Admin Saga] Handling ContractAcceptedEvent for contractId: {}, employerId: {}, freelancerId: {}",
        event.contractId(),
        event.employerId(),
        event.freelancerId());

    Contract contract = getOrCreateContract(event);
    contract.setStatus(ContractStatus.IN_PROGRESS);
    contract.setStartedAt(Instant.now());
    contractRepository.save(contract);

    User freelancer =
        event.freelancerId() != null
            ? userRepository.findById(event.freelancerId()).orElse(null)
            : null;

    SystemAuditLog auditLog =
        SystemAuditLog.builder()
            .id(UUID.randomUUID())
            .actor(freelancer)
            .actorRole(ActorRole.EMPLOYEE)
            .action("CONTRACT_ACCEPTED")
            .targetService(TargetService.CORE)
            .targetEntity("CONTRACT")
            .targetId(event.contractId().toString())
            .status(AuditStatus.SUCCESS)
            .createdAt(Instant.now())
            .build();
    auditLogRepository.save(auditLog);

    log.info(
        "[Admin Saga] Successfully updated contract [{}] to IN_PROGRESS and recorded audit log",
        event.contractId());
  }

  @Transactional
  @RabbitHandler
  public void handleContractRejected(ContractRejectedEvent event) {
    log.info(
        "[Admin Saga] Handling ContractRejectedEvent for contractId: {}, reason: {}",
        event.contractId(),
        event.reason());

    contractRepository
        .findById(event.contractId())
        .ifPresent(
            contract -> {
              contract.setStatus(ContractStatus.CANCELLED);
              contract.setFinishedAt(Instant.now());
              contractRepository.save(contract);

              User freelancer =
                  event.freelancerId() != null
                      ? userRepository.findById(event.freelancerId()).orElse(null)
                      : null;

              SystemAuditLog auditLog =
                  SystemAuditLog.builder()
                      .id(UUID.randomUUID())
                      .actor(freelancer)
                      .actorRole(ActorRole.EMPLOYEE)
                      .action("CONTRACT_REJECTED")
                      .targetService(TargetService.CORE)
                      .targetEntity("CONTRACT")
                      .targetId(event.contractId().toString())
                      .status(AuditStatus.SUCCESS)
                      .createdAt(Instant.now())
                      .build();
              auditLogRepository.save(auditLog);

              log.info(
                  "[Admin Saga] Marked contract [{}] as CANCELLED and recorded rejection audit log",
                  event.contractId());
            });
  }

  @Transactional
  @RabbitHandler
  public void handleContractCompensation(ContractCompensationRequestedEvent event) {
    log.warn(
        "[Admin Saga] Handling compensation for contractId: {}, reason: {}",
        event.contractId(),
        event.reason());

    contractRepository
        .findById(event.contractId())
        .ifPresent(
            contract -> {
              contract.setStatus(ContractStatus.CANCELLED);
              contract.setStartedAt(null);
              contractRepository.save(contract);
            });

    SystemAuditLog auditLog =
        SystemAuditLog.builder()
            .id(UUID.randomUUID())
            .actor(null)
            .actorRole(ActorRole.SYSTEM)
            .action("CONTRACT_COMPENSATION_EXECUTED")
            .targetService(TargetService.ADMINISTRATION)
            .targetEntity("CONTRACT")
            .targetId(event.contractId().toString())
            .status(AuditStatus.FAILED)
            .createdAt(Instant.now())
            .build();
    auditLogRepository.save(auditLog);

    log.info(
        "[Admin Saga] Marked contract [{}] as CANCELLED and recorded compensation audit log",
        event.contractId());
  }

  private Contract getOrCreateContract(ContractAcceptedEvent event) {
    return contractRepository
        .findById(event.contractId())
        .orElseGet(
            () -> {
              User employer =
                  userRepository
                      .findById(event.employerId())
                      .orElseGet(
                          () ->
                              userRepository.save(
                                  User.builder()
                                      .id(event.employerId())
                                      .email("employer-" + event.employerId() + "@occupify.local")
                                      .fullName("Employer " + event.employerId())
                                      .role(UserRole.USER)
                                      .status(UserStatus.ACTIVE)
                                      .build()));

              User employee = null;
              if (event.freelancerId() != null) {
                employee =
                    userRepository
                        .findById(event.freelancerId())
                        .orElseGet(
                            () ->
                                userRepository.save(
                                    User.builder()
                                        .id(event.freelancerId())
                                        .email(
                                            "freelancer-"
                                                + event.freelancerId()
                                                + "@occupify.local")
                                        .fullName("Freelancer " + event.freelancerId())
                                        .role(UserRole.USER)
                                        .status(UserStatus.ACTIVE)
                                        .build()));
              }

              SalaryType st = SalaryType.FIXED;
              if (event.salaryType() != null) {
                try {
                  st = SalaryType.valueOf(event.salaryType().toUpperCase());
                } catch (Exception ignored) {
                }
              }

              Contract newContract =
                  Contract.builder()
                      .id(event.contractId())
                      .jobId(event.jobId() != null ? event.jobId() : UUID.randomUUID())
                      .employer(employer)
                      .employee(employee)
                      .sourceType(SourceType.JOB_BID)
                      .agreedRate(event.budget())
                      .salaryType(st)
                      .status(ContractStatus.IN_PROGRESS)
                      .paymentStatus("PENDING")
                      .build();
              return contractRepository.save(newContract);
            });
  }
}
