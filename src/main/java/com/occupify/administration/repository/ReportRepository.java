package com.occupify.administration.repository;

import com.occupify.administration.entity.Report;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository
    extends JpaRepository<Report, UUID>, JpaSpecificationExecutor<Report> {
  long countByTargetUserId(UUID targetUserId);

  long countByTargetProjectId(UUID targetProjectId);

  List<Report> findByTargetUserIdOrderByCreatedAtDesc(UUID targetUserId);

  List<Report> findByTargetProjectIdOrderByCreatedAtDesc(UUID targetProjectId);
}
