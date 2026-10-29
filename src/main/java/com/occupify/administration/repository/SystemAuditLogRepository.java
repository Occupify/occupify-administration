package com.occupify.administration.repository;

import com.occupify.administration.entity.SystemAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemAuditLogRepository extends JpaRepository<SystemAuditLog, UUID> {

  List<SystemAuditLog> findByTargetId(String targetId);
}
