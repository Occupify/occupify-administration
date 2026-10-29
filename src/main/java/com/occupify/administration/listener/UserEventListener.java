package com.occupify.administration.listener;

import com.occupify.administration.entity.User;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.UserActivatedEvent;
import com.occupify.administration.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

  private final UserRepository userRepository;

  @RabbitListener(queues = "${app.rabbitmq.user-queue:occupify.administration.user.queue}")
  @Transactional
  public void handleUserActivated(UserActivatedEvent event) {
    if (event == null || event.userId() == null || !StringUtils.hasText(event.email())) {
      log.warn("[Admin Event Listener] Received invalid UserActivatedEvent: {}", event);
      return;
    }

    String email = event.email().trim().toLowerCase();
    log.info(
        "[Admin Event Listener] Processing UserActivatedEvent for user [{}] ({})",
        event.userId(),
        email);

    User user =
        userRepository
            .findById(event.userId())
            .orElseGet(
                () ->
                    User.builder()
                        .id(event.userId())
                        .createdAt(event.occurredAt() != null ? event.occurredAt() : Instant.now())
                        .build());

    user.setEmail(email);
    String fullName =
        StringUtils.hasText(event.fullName())
            ? event.fullName().trim()
            : "User " + event.userId().toString().substring(0, 8);
    user.setFullName(fullName);
    user.setRole(parseRole(event.role()));
    user.setStatus(parseStatus(event.status()));

    User savedUser = userRepository.save(user);
    log.info(
        "[Admin Event Listener] Successfully persisted user [{}] ({}) with role [{}] and status [{}]",
        savedUser.getId(),
        savedUser.getEmail(),
        savedUser.getRole(),
        savedUser.getStatus());
  }

  private UserRole parseRole(String roleStr) {
    if (!StringUtils.hasText(roleStr)) {
      return UserRole.USER;
    }
    String normalized = roleStr.trim().toUpperCase();
    return switch (normalized) {
      case "ADMIN" -> UserRole.ADMIN;
      default -> {
        try {
          yield UserRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
          log.warn("[Admin Event Listener] Unknown user role [{}], defaulting to USER", roleStr);
          yield UserRole.USER;
        }
      }
    };
  }

  private UserStatus parseStatus(String statusStr) {
    if (!StringUtils.hasText(statusStr)) {
      return UserStatus.ACTIVE;
    }
    String normalized = statusStr.trim().toUpperCase();
    return switch (normalized) {
      case "ACTIVE" -> UserStatus.ACTIVE;
      case "INACTIVE" -> UserStatus.INACTIVE;
      case "DELETED" -> UserStatus.DELETED;
      default -> {
        try {
          yield UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException e) {
          log.warn("[Admin Event Listener] Unknown user status [{}], defaulting to ACTIVE", statusStr);
          yield UserStatus.ACTIVE;
        }
      }
    };
  }
}
