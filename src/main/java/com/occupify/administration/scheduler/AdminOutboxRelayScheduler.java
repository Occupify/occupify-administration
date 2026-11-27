package com.occupify.administration.scheduler;

import com.occupify.administration.entity.OutboxEvent;
import com.occupify.administration.enums.OutboxStatus;
import com.occupify.administration.producer.AdminOutboxService;
import com.occupify.administration.repository.OutboxEventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminOutboxRelayScheduler {

  private final OutboxEventRepository outboxEventRepository;
  private final AdminOutboxService adminOutboxService;

  @Scheduled(fixedDelayString = "${app.outbox.relay-delay-ms:3000}")
  public void processPendingOutboxEvents() {
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findTop50ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxStatus.PENDING, AdminOutboxService.MAX_RETRY_COUNT);

    if (pendingEvents.isEmpty()) {
      return;
    }

    log.debug(
        "[Admin Outbox Scheduler] Processing {} pending outbox event(s)", pendingEvents.size());
    for (OutboxEvent event : pendingEvents) {
      adminOutboxService.relayDirectly(event);
    }
  }
}
