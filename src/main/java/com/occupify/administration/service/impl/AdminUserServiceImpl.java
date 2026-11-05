package com.occupify.administration.service.impl;

import com.occupify.administration.controller.ResponseFactory;
import com.occupify.administration.dto.base.PageResponse;
import com.occupify.administration.dto.request.AdminUserFilterRequest;
import com.occupify.administration.dto.response.AdminUserContractSummaryResponse;
import com.occupify.administration.dto.response.AdminUserDetailResponse;
import com.occupify.administration.dto.response.AdminUserResponse;
import com.occupify.administration.entity.Contract;
import com.occupify.administration.entity.FlaggedAccount;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ContractStatus;
import com.occupify.administration.enums.FlagStatus;
import com.occupify.administration.exception.AdminErrorCode;
import com.occupify.administration.exception.AdminException;
import com.occupify.administration.repository.ContractRepository;
import com.occupify.administration.repository.FlaggedAccountRepository;
import com.occupify.administration.repository.ReportRepository;
import com.occupify.administration.repository.UserRepository;
import com.occupify.administration.service.AdminUserService;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final ResponseFactory responseFactory;

    @Override
    public PageResponse<AdminUserResponse> getUsers(AdminUserFilterRequest filter, Pageable pageable) {
        log.info("[Admin] Querying users with filter: {}, pageable: {}", filter, pageable);

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (StringUtils.hasText(filter.keyword())) {
                    String pattern = "%" + filter.keyword().trim().toLowerCase() + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("email")), pattern),
                            cb.like(cb.lower(root.get("fullName")), pattern)
                    ));
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

        List<AdminUserResponse> content = userPage.getContent().stream()
                .map(this::toAdminUserResponse)
                .toList();

        return responseFactory.createPageResponse(
                "Users retrieved successfully",
                content,
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages()
        );
    }

    @Override
    public AdminUserDetailResponse getUserDetail(UUID id) {
        log.info("[Admin] Fetching user details for id: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new AdminException(AdminErrorCode.USER_NOT_FOUND, "User not found with id: " + id));

        Optional<FlaggedAccount> flaggedOpt = flaggedAccountRepository.findFirstByUserIdAndStatus(id, FlagStatus.PENDING);
        boolean isFlagged = flaggedOpt.isPresent();
        String flagReason = flaggedOpt.map(FlaggedAccount::getFlagReason).orElse(null);

        long completedContracts = contractRepository.countByUserIdAndStatus(id, ContractStatus.COMPLETED);
        long totalContracts = contractRepository.countByUserId(id);
        long disputeCount = reportRepository.countByTargetUserId(id);

        List<AdminUserContractSummaryResponse> recentContracts = contractRepository.findRecentContractsByUserId(id)
                .stream()
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
                user.getCreatedAt(),
                user.getDeletedAt(),
                isFlagged,
                flagReason,
                completedContracts,
                totalContracts,
                disputeCount,
                recentContracts
        );
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        boolean isFlagged = flaggedAccountRepository.existsByUserIdAndStatus(user.getId(), FlagStatus.PENDING);
        long completedContracts = contractRepository.countByUserIdAndStatus(user.getId(), ContractStatus.COMPLETED);
        long disputeCount = reportRepository.countByTargetUserId(user.getId());

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getStatus(),
                isFlagged,
                completedContracts,
                disputeCount,
                user.getCreatedAt()
        );
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
                contract.getCreatedAt()
        );
    }
}

