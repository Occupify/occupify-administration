package com.occupify.administration.repository;

import com.occupify.administration.entity.Contract;
import com.occupify.administration.enums.ContractStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, UUID> {

  @Query(
      "SELECT COUNT(c) FROM Contract c WHERE (c.employer.id = :userId OR c.employee.id = :userId) AND c.status = :status")
  long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ContractStatus status);

  @Query("SELECT COUNT(c) FROM Contract c WHERE c.employer.id = :userId OR c.employee.id = :userId")
  long countByUserId(@Param("userId") UUID userId);

  @Query(
      "SELECT c FROM Contract c WHERE c.employer.id = :userId OR c.employee.id = :userId ORDER BY c.createdAt DESC")
  List<Contract> findRecentContractsByUserId(@Param("userId") UUID userId);
}
