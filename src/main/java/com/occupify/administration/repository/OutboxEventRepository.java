package com.occupify.administration.repository;

import com.occupify.administration.entity.OutboxEvent;
import com.occupify.administration.enums.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  List<OutboxEvent> findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
      OutboxStatus status, int maxRetryCount);
}
