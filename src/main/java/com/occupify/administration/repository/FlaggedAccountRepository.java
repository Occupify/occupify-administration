package com.occupify.administration.repository;

import com.occupify.administration.entity.FlaggedAccount;
import com.occupify.administration.enums.FlagStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FlaggedAccountRepository extends JpaRepository<FlaggedAccount, UUID> {
    Optional<FlaggedAccount> findFirstByUserIdAndStatus(UUID userId, FlagStatus status);
    List<FlaggedAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByUserIdAndStatus(UUID userId, FlagStatus status);
}

