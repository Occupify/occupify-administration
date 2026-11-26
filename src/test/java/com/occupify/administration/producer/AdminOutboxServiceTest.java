package com.occupify.administration.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.occupify.administration.entity.OutboxEvent;
import com.occupify.administration.enums.OutboxStatus;
import com.occupify.administration.event.UserStatusChangedEvent;
import com.occupify.administration.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class AdminOutboxServiceTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private RabbitTemplate rabbitTemplate;

  private AdminOutboxService outboxService;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    outboxService = new AdminOutboxService(outboxEventRepository, rabbitTemplate, objectMapper);
  }

  @Test
  @DisplayName("enqueueAndDispatch - should persist outbox event and mark SENT on successful relay")
  void enqueueAndDispatch_marksSentOnSuccess() {
    UUID userId = UUID.randomUUID();
    UserStatusChangedEvent event =
        new UserStatusChangedEvent(userId, "BANNED", "Abuse", UUID.randomUUID(), Instant.now());

    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OutboxEvent saved =
        outboxService.enqueueAndDispatch(
            "USER",
            userId,
            "occupify.user.exchange",
            "user.status.changed",
            AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED,
            event);

    assertThat(saved.getStatus()).isEqualTo(OutboxStatus.SENT);
    assertThat(saved.getSentAt()).isNotNull();
    verify(rabbitTemplate)
        .convertAndSend(
            eq("occupify.user.exchange"),
            eq("user.status.changed"),
            any(UserStatusChangedEvent.class));
  }

  @Test
  @DisplayName("relayDirectly - should keep PENDING and increment retryCount on RabbitMQ failure")
  void relayDirectly_incrementsRetryOnFailure() {
    UUID userId = UUID.randomUUID();
    UserStatusChangedEvent event =
        new UserStatusChangedEvent(userId, "DELETED", null, UUID.randomUUID(), Instant.now());

    when(outboxEventRepository.save(any(OutboxEvent.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    doThrow(new RuntimeException("Broker down"))
        .when(rabbitTemplate)
        .convertAndSend(any(String.class), any(String.class), any(Object.class));

    OutboxEvent saved =
        outboxService.enqueueAndDispatch(
            "USER",
            userId,
            "occupify.user.exchange",
            "user.status.changed",
            AdminOutboxService.EVENT_TYPE_USER_STATUS_CHANGED,
            event);

    assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    assertThat(saved.getRetryCount()).isEqualTo(1);
    assertThat(saved.getSentAt()).isNull();
  }
}
