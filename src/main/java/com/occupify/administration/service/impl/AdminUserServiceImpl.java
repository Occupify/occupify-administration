package com.occupify.administration.service.impl;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserContractSummaryResponse;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.entity.Contract;
import com.occupify.administration.entity.FlaggedAccount;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.AuditStatus;
import com.occupify.administration.enums.ContractStatus;
import com.occupify.administration.enums.FlagStatus;
import com.occupify.administration.enums.TargetService;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.UserStatusChangedEvent;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.producer.AdminOutboxService;
import com.occupify.administration.repository.ContractRepository;
import com.occupify.administration.repository.FlaggedAccountRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.repository.SystemAuditLogRepository;
import com.occupify.administration.repository.UserRepository;
import com.occupify.administration.security.AdminContext;
import com.occupify.administration.service.AdminUserService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserServiceImpl implements AdminUserService {

  private final UserRepository userRepository;
  private final ContractRepository contractRepository;
  private final ReportRepository reportRepository;
  private final FlaggedAccountRepository flaggedAccountRepository;
  private final SystemAuditLogRepository auditLogRepository;
  private final AdminOutboxService adminOutboxService;
  private final ResponseFactory responseFactory;

  @Value("${app.rabbitmq.user-exchange:occupify.user.exchange}")
  private String userExchangeName = "occupify.user.exchange";

  @Value("${app.rabbitmq.user-status-changed-routing-key:user.status.changed}")
  private String userStatusChangedRoutingKey = "user.status.changed";

  @Override
  public PageResponse<AdminUserResponse> getUsers(
      AdminUserFilterRequest filter, Pageable pageable) {
    log.info("[Admin] Querying users with filter: {}, pageable: {}", filter, pageable);

    Specification<User> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (filter != null) {
            if (StringUtils.hasText(filter.keyword())) {
              String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
              predicates.add(
                  cb.or(
                      cb.like(cb.lower(root.get("email")), pattern),
                      cb.like(cb.lower(root.get("fullName")), pattern)));
            }

            if (filter.status() != null) {
              predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            if (filter.role() != null) {
              predicates.add(cb.equal(root.get("role"), filter.role()));
            }
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<User> userPage = userRepository.findAll(spec, pageable);

    List<AdminUserResponse> content =
        userPage.getContent().stream().map(this::toAdminUserResponse).toList();

    return responseFactory.createPageResponse(
        "Users retrieved successfully",
        content,
        userPage.getNumber(),
        userPage.getSize(),
        userPage.getTotalElements(),
        userPage.getTotalPages());
  }

  @Override
  public AdminUserDetailResponse getUserDetail(UUID id) {
    log.info("[Admin] Fetching user details for id: {}", id);

    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new AdminException(
                        AdminErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

    Optional<FlaggedAccount> flaggedOpt =
        flaggedAccountRepository.findFirstByUserIdAndStatus(id, FlagStatus.PENDING);
    boolean isFlagged = flaggedOpt.isPresent();
    String flagReason = flaggedOpt.map(FlaggedAccount::getFlagReason).orElse(null);

    long completedContracts =
        contractRepository.countByUserIdAndStatus(id, ContractStatus.COMPLETED);
    long totalContracts = contractRepository.countByUserId(id);
    long disputeCount = reportRepository.countByTargetUserId(id);

    List<AdminUserContractSummaryResponse> recentContracts =
        contractRepository.findRecentContractsByUserId(id).stream()
            .limit(10)
            .map(this::toContractSummaryResponse)
            .toList();

    return new AdminUserDetailResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getAvatarUrl(),
        user.getRole(),
        user.getStatus(),
        user.getStatusReason(),
        user.getCreatedAt(),
        user.getDeletedAt(),
        isFlagged,
        flagReason,
        completedContracts,
        totalContracts,
        disputeCount,
        recentContracts);
  }

  @Override
  @Transactional
  public void deleteUser(UUID id, @Nullable String reason, AdminContext adminContext) {
    UUID adminId = adminContext != null ? adminContext.adminId() : null;
    log.info("[Admin] Deleting user [{}] by admin [{}]", id, adminId);

    if (adminId != null && adminId.equals(id)) {
      throw new AdminException(AdminErrorCode.CANNOT_MODIFY_SELF);
    }

    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new AdminException(
                        AdminErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

    if (user.getRole() == UserRole.ADMIN) {
      throw new AdminException(AdminErrorCode.CANNOT_MODIFY_ADMIN);
    }

    if (user.getStatus() == UserStatus.DELETED) {
      throw new AdminException(
          AdminErrorCode.INVALID_USER_STATUS, "User account is already deleted");
    }

    Instant now = Instant.now();
    String normalizedReason = normalizeReason(reason);

    user.setStatus(UserStatus.DELETED);
    user.setDeletedAt(now);
    user.setStatusReason(normalizedReason);
    userRepository.save(user);

    recordAuditLog(adminId, "USER_DELETED", TargetService.IDENTITY, "USER", id.toString(), now);

    UserStatusChangedEvent event =
        new UserStatusChangedEvent(
            user.getId(), UserStatus.DELETED.name(), normalizedReason, adminId, now);
    adminOutboxService.enqueueAndDispatch(
        "USER",
        user.getId(),
        userExchangeName,
        userStatusChangedRoutingKey,
        AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED,
        event);
  }

  @Override
  @Transactional
  public AdminUserResponse lockUser(UUID id, @Nullable String reason, AdminContext adminContext) {
    UUID adminId = adminContext != null ? adminContext.adminId() : null;
    log.info("[Admin] Locking user [{}] by admin [{}]", id, adminId);

    if (adminId != null && adminId.equals(id)) {
      throw new AdminException(AdminErrorCode.CANNOT_MODIFY_SELF);
    }

    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new AdminException(
                        AdminErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

    if (user.getRole() == UserRole.ADMIN) {
      throw new AdminException(AdminErrorCode.CANNOT_MODIFY_ADMIN);
    }

    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new AdminException(
          AdminErrorCode.INVALID_USER_STATUS,
          "Only ACTIVE user accounts can be locked. Current status: " + user.getStatus());
    }

    Instant now = Instant.now();
    String normalizedReason = normalizeReason(reason);

    user.setStatus(UserStatus.BANNED);
    user.setStatusReason(normalizedReason);
    user = userRepository.save(user);

    recordAuditLog(adminId, "USER_LOCKED", TargetService.IDENTITY, "USER", id.toString(), now);

    UserStatusChangedEvent event =
        new UserStatusChangedEvent(
            user.getId(), UserStatus.BANNED.name(), normalizedReason, adminId, now);
    adminOutboxService.enqueueAndDispatch(
        "USER",
        user.getId(),
        userExchangeName,
        userStatusChangedRoutingKey,
        AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED,
        event);

    return toAdminUserResponse(user);
  }

  @Override
  @Transactional
  public AdminUserResponse unlockUser(UUID id, AdminContext adminContext) {
    UUID adminId = adminContext != null ? adminContext.adminId() : null;
    log.info("[Admin] Unlocking user [{}] by admin [{}]", id, adminId);

    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new AdminException(
                        AdminErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

    if (user.getStatus() != UserStatus.BANNED) {
      throw new AdminException(
          AdminErrorCode.INVALID_USER_STATUS,
          "Only locked (BANNED) user accounts can be unlocked. Current status: "
              + user.getStatus());
    }

    Instant now = Instant.now();

    user.setStatus(UserStatus.ACTIVE);
    user.setStatusReason(null);
    user = userRepository.save(user);

    recordAuditLog(adminId, "USER_UNLOCKED", TargetService.IDENTITY, "USER", id.toString(), now);

    UserStatusChangedEvent event =
        new UserStatusChangedEvent(user.getId(), UserStatus.ACTIVE.name(), null, adminId, now);
    adminOutboxService.enqueueAndDispatch(
        "USER",
        user.getId(),
        userExchangeName,
        userStatusChangedRoutingKey,
        AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED,
        event);

    return toAdminUserResponse(user);
  }

  private void recordAuditLog(
      @Nullable UUID adminId,
      String action,
      TargetService targetService,
      String targetEntity,
      String targetId,
      Instant createdAt) {
    User actor = adminId != null ? userRepository.findById(adminId).orElse(null) : null;
    SystemAuditLog auditLog =
        SystemAuditLog.builder()
            .id(UUID.randomUUID())
            .actor(actor)
            .actorRole(ActorRole.ADMIN)
            .action(action)
            .targetService(targetService)
            .targetEntity(targetEntity)
            .targetId(targetId)
            .status(AuditStatus.SUCCESS)
            .createdAt(createdAt)
            .build();
    auditLogRepository.save(auditLog);
  }

  private String normalizeReason(@Nullable String reason) {
    return StringUtils.hasText(reason) ? reason.trim() : null;
  }

  private AdminUserResponse toAdminUserResponse(User user) {
    boolean isFlagged =
        flaggedAccountRepository.existsByUserIdAndStatus(user.getId(), FlagStatus.PENDING);
    long completedContracts =
        contractRepository.countByUserIdAndStatus(user.getId(), ContractStatus.COMPLETED);
    long disputeCount = reportRepository.countByTargetUserId(user.getId());

    return new AdminUserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getAvatarUrl(),
        user.getRole(),
        user.getStatus(),
        user.getStatusReason(),
        isFlagged,
        completedContracts,
        disputeCount,
        user.getCreatedAt());
  }

  private AdminUserContractSummaryResponse toContractSummaryResponse(Contract contract) {
    return new AdminUserContractSummaryResponse(
        contract.getId(),
        contract.getTitle(),
        contract.getRole(),
        contract.getAgreedRate(),
        contract.getSalaryType(),
        contract.getStatus(),
        contract.getStartedAt(),
        contract.getFinishedAt(),
        contract.getCreatedAt());
  }
}
