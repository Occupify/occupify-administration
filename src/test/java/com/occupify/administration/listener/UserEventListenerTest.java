package com.occupify.administration.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.occupify.administration.entity.User;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.UserActivatedEvent;
import com.occupify.administration.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

  @Mock private UserRepository userRepository;

  private UserEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new UserEventListener(userRepository);
  }

  @Test
  @DisplayName("Should persist new user when receiving UserActivatedEvent")
  void shouldPersistNewUser() {
    UUID userId = UUID.randomUUID();
    UserActivatedEvent event =
        new UserActivatedEvent(
            userId,
            "TEST@OCCUPIFY.COM",
            "Jane Doe",
            "USER",
            "ACTIVE",
            Instant.parse("2026-10-25T10:00:00Z"));

    when(userRepository.findById(userId)).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    listener.handleUserActivated(event);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getId()).isEqualTo(userId);
    assertThat(saved.getEmail()).isEqualTo("test@occupify.com");
    assertThat(saved.getFullName()).isEqualTo("Jane Doe");
    assertThat(saved.getRole()).isEqualTo(UserRole.FREELANCER);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2026-10-25T10:00:00Z"));
  }

  @Test
  @DisplayName("Should update existing user and map roles correctly")
  void shouldUpdateExistingUser() {
    UUID userId = UUID.randomUUID();
    User existing =
        User.builder()
            .id(userId)
            .email("old@occupify.com")
            .fullName("Old Name")
            .role(UserRole.FREELANCER)
            .status(UserStatus.INACTIVE)
            .createdAt(Instant.now().minusSeconds(3600))
            .build();

    UserActivatedEvent event =
        new UserActivatedEvent(
            userId, "new@occupify.com", "Admin User", "ADMIN", "ACTIVE", Instant.now());

    when(userRepository.findById(userId)).thenReturn(Optional.of(existing));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    listener.handleUserActivated(event);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();

    assertThat(saved.getId()).isEqualTo(userId);
    assertThat(saved.getEmail()).isEqualTo("new@occupify.com");
    assertThat(saved.getFullName()).isEqualTo("Admin User");
    assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  @DisplayName("Should ignore invalid or null event without throwing exception")
  void shouldIgnoreInvalidEvent() {
    listener.handleUserActivated(null);
    listener.handleUserActivated(new UserActivatedEvent(null, "test@occupify.com", "Test", "USER", "ACTIVE", Instant.now()));
    listener.handleUserActivated(new UserActivatedEvent(UUID.randomUUID(), "", "Test", "USER", "ACTIVE", Instant.now()));

    verify(userRepository, never()).save(any());
  }
}
