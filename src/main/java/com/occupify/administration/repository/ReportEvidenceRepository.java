package com.occupify.administration.repository;

import com.occupify.administration.entity.ReportEvidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, UUID> {
  List<ReportEvidence> findByReportId(UUID reportId);
}
