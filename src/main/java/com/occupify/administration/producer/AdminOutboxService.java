package com.occupify.administration.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.occupify.administration.entity.OutboxEvent;
import com.occupify.administration.enums.OutboxStatus;
import com.occupify.administration.event.ProjectCloseRequestedEvent;
import com.occupify.administration.event.UserStatusChangedEvent;
import com.occupify.administration.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOutboxService {

  public static final int MAX_RETRY_COUNT = 5;
  public static final String EVENT_TYPE_USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
  public static final String EVENT_TYPE_PROJECT_CLOSE_REQUESTED = "PROJECT_CLOSE_REQUESTED";

  private final OutboxEventRepository outboxEventRepository;
  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public OutboxEvent enqueueAndDispatch(
      String aggregateType,
      UUID aggregateId,
      String exchangeName,
      String routingKey,
      String eventType,
      Object eventPayload) {
    String serializedPayload;
    try {
      serializedPayload = objectMapper.writeValueAsString(eventPayload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize outbox event payload: " + eventType, e);
    }

    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType(aggregateType)
            .aggregateId(aggregateId)
            .exchangeName(exchangeName)
            .routingKey(routingKey)
            .eventType(eventType)
            .payload(serializedPayload)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .createdAt(Instant.now())
            .build();

    OutboxEvent saved = outboxEventRepository.save(outboxEvent);
    scheduleRelayAfterCommit(saved);
    return saved;
  }

  public void relayDirectly(OutboxEvent event) {
    if (event == null || event.getStatus() == OutboxStatus.SENT) {
      return;
    }

    try {
      Object payloadToSend = deserializePayload(event);
      rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), payloadToSend);
      event.setStatus(OutboxStatus.SENT);
      event.setSentAt(Instant.now());
      outboxEventRepository.save(event);
      log.info(
          "[Admin Outbox] Relayed event [{}] ({}) to exchange [{}] with routingKey [{}]",
          event.getId(),
          event.getEventType(),
          event.getExchangeName(),
          event.getRoutingKey());
    } catch (Exception e) {
      log.error(
          "[Admin Outbox] Failed to relay event [{}] ({}): {}",
          event.getId(),
          event.getEventType(),
          e.getMessage(),
          e);
      int nextRetry = event.getRetryCount() + 1;
      event.setRetryCount(nextRetry);
      if (nextRetry >= MAX_RETRY_COUNT) {
        event.setStatus(OutboxStatus.FAILED);
      }
      outboxEventRepository.save(event);
    }
  }

  private void scheduleRelayAfterCommit(OutboxEvent saved) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              relayDirectly(saved);
            }
          });
    } else {
      relayDirectly(saved);
    }
  }

  private Object deserializePayload(OutboxEvent event) throws JsonProcessingException {
    if (EVENT_TYPE_USER_STATUS_CHANGED.equalsIgnoreCase(event.getEventType())) {
      return objectMapper.readValue(event.getPayload(), UserStatusChangedEvent.class);
    }
    if (EVENT_TYPE_PROJECT_CLOSE_REQUESTED.equalsIgnoreCase(event.getEventType())) {
      return objectMapper.readValue(event.getPayload(), ProjectCloseRequestedEvent.class);
    }
    return event.getPayload();
  }
}
