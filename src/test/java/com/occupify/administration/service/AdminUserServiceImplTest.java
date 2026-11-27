package com.occupify.administration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
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
import com.occupify.administration.service.impl.AdminUserServiceImpl;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

  @Mock private UserRepository userRepository;

  @Mock private ContractRepository contractRepository;

  @Mock private ReportRepository reportRepository;

  @Mock private FlaggedAccountRepository flaggedAccountRepository;

  @Mock private SystemAuditLogRepository auditLogRepository;

  @Mock private AdminOutboxService adminOutboxService;

  @Spy private ResponseFactory responseFactory = new ResponseFactory();

  @InjectMocks private AdminUserServiceImpl userService;

  private User sampleUser;
  private UUID userId;
  private AdminContext adminContext;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    adminContext = new AdminContext(UUID.randomUUID(), "admin@occupify.app", UserRole.ADMIN);
    sampleUser =
        User.builder()
            .id(userId)
            .email("test@occupify.app")
            .fullName("Test User")
            .avatarUrl("https://example.com/avatar.png")
            .role(UserRole.USER)
            .status(UserStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("getUsers - should return paginated list of users")
  void getUsers_shouldReturnPaginatedUsers() {
    Pageable pageable = PageRequest.of(0, 10);
    Page<User> page = new PageImpl<>(List.of(sampleUser), pageable, 1);

    when(userRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
    when(flaggedAccountRepository.existsByUserIdAndStatus(any(), any())).thenReturn(false);
    when(contractRepository.countByUserIdAndStatus(any(), any())).thenReturn(5L);
    when(reportRepository.countByTargetUserId(any())).thenReturn(1L);

    AdminUserFilterRequest filter = new AdminUserFilterRequest("test", UserStatus.ACTIVE, null);
    PageResponse<AdminUserResponse> result = userService.getUsers(filter, pageable);

    assertThat(result).isNotNull();
    assertThat(result.data()).hasSize(1);
    AdminUserResponse userResponse = result.data().get(0);
    assertThat(userResponse.id()).isEqualTo(userId);
    assertThat(userResponse.email()).isEqualTo("test@occupify.app");
    assertThat(userResponse.totalCompletedContracts()).isEqualTo(5L);
    assertThat(userResponse.disputeCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("getUserDetail - should return full detail when user exists")
  void getUserDetail_shouldReturnDetail() {
    sampleUser.setStatusReason("Previous warning");
    when(userRepository.findById(eq(userId))).thenReturn(Optional.of(sampleUser));
    when(flaggedAccountRepository.findFirstByUserIdAndStatus(any(), any()))
        .thenReturn(Optional.empty());
    when(contractRepository.countByUserIdAndStatus(any(), any())).thenReturn(3L);
    when(contractRepository.countByUserId(any())).thenReturn(7L);
    when(reportRepository.countByTargetUserId(any())).thenReturn(0L);
    when(contractRepository.findRecentContractsByUserId(any())).thenReturn(Collections.emptyList());

    AdminUserDetailResponse result = userService.getUserDetail(userId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(userId);
    assertThat(result.email()).isEqualTo("test@occupify.app");
    assertThat(result.statusReason()).isEqualTo("Previous warning");
    assertThat(result.totalCompletedContracts()).isEqualTo(3L);
    assertThat(result.totalContracts()).isEqualTo(7L);
    assertThat(result.isFlagged()).isFalse();
  }

  @Test
  @DisplayName("getUserDetail - should throw USER_NOT_FOUND when user does not exist")
  void getUserDetail_shouldThrow_whenUserNotFound() {
    when(userRepository.findById(eq(userId))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getUserDetail(userId))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.USER_NOT_FOUND.code());
  }

  @Test
  @DisplayName("deleteUser - should soft delete user, store reason, log audit, and enqueue event")
  void deleteUser_shouldSoftDeleteAndEnqueueEvent() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
    when(userRepository.findById(adminContext.adminId())).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    userService.deleteUser(userId, "  Fraudulent behavior  ", adminContext);

    assertThat(sampleUser.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(sampleUser.getDeletedAt()).isNotNull();
    assertThat(sampleUser.getStatusReason()).isEqualTo("Fraudulent behavior");

    ArgumentCaptor<SystemAuditLog> auditCaptor = ArgumentCaptor.forClass(SystemAuditLog.class);
    verify(auditLogRepository).save(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getAction()).isEqualTo("USER_DELETED");
    assertThat(auditCaptor.getValue().getActorRole()).isEqualTo(ActorRole.ADMIN);
    assertThat(auditCaptor.getValue().getTargetService()).isEqualTo(TargetService.IDENTITY);

    ArgumentCaptor<UserStatusChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(UserStatusChangedEvent.class);
    verify(adminOutboxService)
        .enqueueAndDispatch(
            eq("USER"),
            eq(userId),
            eq("occupify.user.exchange"),
            eq("user.status.changed"),
            eq(AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED),
            eventCaptor.capture());
    assertThat(eventCaptor.getValue().status()).isEqualTo("DELETED");
    assertThat(eventCaptor.getValue().reason()).isEqualTo("Fraudulent behavior");
    assertThat(eventCaptor.getValue().changedByAdminId()).isEqualTo(adminContext.adminId());
  }

  @Test
  @DisplayName("deleteUser - should accept null or blank reason")
  void deleteUser_shouldAcceptNullReason() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
    when(userRepository.findById(adminContext.adminId())).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    userService.deleteUser(userId, "   ", adminContext);

    assertThat(sampleUser.getStatus()).isEqualTo(UserStatus.DELETED);
    assertThat(sampleUser.getStatusReason()).isNull();
  }

  @Test
  @DisplayName("deleteUser - should reject self-deletion")
  void deleteUser_shouldRejectSelf() {
    assertThatThrownBy(() -> userService.deleteUser(adminContext.adminId(), null, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.CANNOT_MODIFY_SELF.code());

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("deleteUser - should reject deleting another admin")
  void deleteUser_shouldRejectAdminTarget() {
    sampleUser.setRole(UserRole.ADMIN);
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

    assertThatThrownBy(() -> userService.deleteUser(userId, "test", adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.CANNOT_MODIFY_ADMIN.code());
  }

  @Test
  @DisplayName("deleteUser - should reject already deleted account")
  void deleteUser_shouldRejectAlreadyDeleted() {
    sampleUser.setStatus(UserStatus.DELETED);
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

    assertThatThrownBy(() -> userService.deleteUser(userId, null, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.INVALID_USER_STATUS.code());
  }

  @Test
  @DisplayName("lockUser - should transition ACTIVE user to BANNED with reason")
  void lockUser_shouldLockActiveUser() {
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
    when(userRepository.findById(adminContext.adminId())).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(flaggedAccountRepository.existsByUserIdAndStatus(any(), any())).thenReturn(false);
    when(contractRepository.countByUserIdAndStatus(any(), any())).thenReturn(0L);
    when(reportRepository.countByTargetUserId(any())).thenReturn(0L);

    AdminUserResponse result = userService.lockUser(userId, "Suspicious activity", adminContext);

    assertThat(result.status()).isEqualTo(UserStatus.BANNED);
    assertThat(result.statusReason()).isEqualTo("Suspicious activity");
    verify(auditLogRepository).save(any(SystemAuditLog.class));
    verify(adminOutboxService)
        .enqueueAndDispatch(
            eq("USER"),
            eq(userId),
            eq("occupify.user.exchange"),
            eq("user.status.changed"),
            eq(AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED),
            any(UserStatusChangedEvent.class));
  }

  @Test
  @DisplayName("lockUser - should reject locking non-ACTIVE account")
  void lockUser_shouldRejectNonActiveUser() {
    sampleUser.setStatus(UserStatus.BANNED);
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

    assertThatThrownBy(() -> userService.lockUser(userId, "dup", adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.INVALID_USER_STATUS.code());
  }

  @Test
  @DisplayName("lockUser - should reject locking self or another admin")
  void lockUser_shouldRejectSelfAndAdmin() {
    assertThatThrownBy(() -> userService.lockUser(adminContext.adminId(), null, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.CANNOT_MODIFY_SELF.code());

    sampleUser.setRole(UserRole.ADMIN);
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
    assertThatThrownBy(() -> userService.lockUser(userId, null, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.CANNOT_MODIFY_ADMIN.code());
  }

  @Test
  @DisplayName("unlockUser - should transition BANNED user back to ACTIVE and clear statusReason")
  void unlockUser_shouldUnlockBannedUser() {
    sampleUser.setStatus(UserStatus.BANNED);
    sampleUser.setStatusReason("Previous ban");
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
    when(userRepository.findById(adminContext.adminId())).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    when(flaggedAccountRepository.existsByUserIdAndStatus(any(), any())).thenReturn(false);
    when(contractRepository.countByUserIdAndStatus(any(), any())).thenReturn(0L);
    when(reportRepository.countByTargetUserId(any())).thenReturn(0L);

    AdminUserResponse result = userService.unlockUser(userId, adminContext);

    assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(result.statusReason()).isNull();

    ArgumentCaptor<UserStatusChangedEvent> eventCaptor =
        ArgumentCaptor.forClass(UserStatusChangedEvent.class);
    verify(adminOutboxService)
        .enqueueAndDispatch(
            eq("USER"),
            eq(userId),
            eq("occupify.user.exchange"),
            eq("user.status.changed"),
            eq(AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED),
            eventCaptor.capture());
    assertThat(eventCaptor.getValue().status()).isEqualTo("ACTIVE");
    assertThat(eventCaptor.getValue().reason()).isNull();
  }

  @Test
  @DisplayName("unlockUser - should reject unlocking non-BANNED account")
  void unlockUser_shouldRejectNonBannedUser() {
    sampleUser.setStatus(UserStatus.ACTIVE);
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

    assertThatThrownBy(() -> userService.unlockUser(userId, adminContext))
        .isInstanceOf(AdminException.class)
        .hasFieldOrPropertyWithValue("code", AdminErrorCode.INVALID_USER_STATUS.code());
  }
}
