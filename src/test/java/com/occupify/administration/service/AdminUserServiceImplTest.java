package com.occupify.administration.service;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.repository.ContractRepository;
import com.occupify.administration.repository.FlaggedAccountRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.repository.UserRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContractRepository contractRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private FlaggedAccountRepository flaggedAccountRepository;

    @Spy
    private ResponseFactory responseFactory = new ResponseFactory();

    @InjectMocks
    private AdminUserServiceImpl userService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = User.builder()
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
        when(userRepository.findById(eq(userId))).thenReturn(Optional.of(sampleUser));
        when(flaggedAccountRepository.findFirstByUserIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(contractRepository.countByUserIdAndStatus(any(), any())).thenReturn(3L);
        when(contractRepository.countByUserId(any())).thenReturn(7L);
        when(reportRepository.countByTargetUserId(any())).thenReturn(0L);
        when(contractRepository.findRecentContractsByUserId(any())).thenReturn(Collections.emptyList());

        AdminUserDetailResponse result = userService.getUserDetail(userId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("test@occupify.app");
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
}

