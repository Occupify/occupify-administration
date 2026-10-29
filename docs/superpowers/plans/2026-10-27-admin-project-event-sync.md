# User Create Project Event Synchronization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Synchronize project creation (and closure) domain events from `occupify-core` to `occupify-administration` via a dedicated RabbitMQ topic exchange, populating the replicated `projects` table and creating audit logs.

**Architecture:** `occupify-core` publishes `ProjectCreatedEvent` (with enriched `description` and `status`) to `occupify.project.exchange` (`TopicExchange`). `occupify-administration` binds `occupify.administration.project.queue` (with DLX) to receive `project.created` and `project.closed`, safely persists to `ProjectRepository` with employer user fallback, and records entries in `system_audit_logs`.

**Tech Stack:** Java 25 / Spring Boot 3.3.4, Spring AMQP (RabbitMQ), Spring Data JPA, Jackson 2 with JavaTimeModule, JUnit 5, Mockito, AssertJ.

**Spec:** [`occupify-administration/docs/superpowers/specs/2026-10-27-admin-project-event-sync-design.md`](../specs/2026-10-27-admin-project-event-sync-design.md)

## Global Constraints
- Target branch in `occupify-administration`: `OC-94-95` (do not switch or create new branch).
- Do not commit or push to remote without explicit user command.
- Follow existing patterns established in `ContractEventListener` and `UserEventListener`.
- Defensive coding: Never fail or throw unhandled exceptions if the employer user does not yet exist in Admin's local `users` table; stub the user to satisfy the foreign key constraint.

## Review Focus
1. **Employer User Missing in Admin:** If `event.employerId()` does not exist in Admin's `users` table, listener creates fallback user record before saving project.
2. **Null/Empty Event Fields:** If event has null `projectId` or null `employerId`, log a warning and discard without throwing uncaught exceptions.
3. **Date Conversion:** Convert `LocalDateTime` from Core event to `Instant` in Admin entity safely without zone issues.
4. **Idempotent Updates:** If `ProjectCreatedEvent` arrives for an already existing `projectId`, it updates title, description, and status rather than failing on duplicate PK.
5. **Closed Project Lifecycle:** When `ProjectClosedEvent` arrives, status transitions to `CLOSED` and `closedAt` is populated.

---

### Task 1: Update `occupify-core` to Publish Enriched Project Events to `occupify.project.exchange`

**Files:**
- Modify: `occupify-core/src/main/resources/application.yml:38-46`
- Modify: `occupify-core/src/main/java/com/occupify/core/config/RabbitMQConfig.java:30-75`
- Modify: `occupify-core/src/main/java/com/occupify/core/event/ProjectCreatedEvent.java:1-9`
- Modify: `occupify-core/src/main/java/com/occupify/core/producer/impl/ProjectEventProducerImpl.java:15-37`
- Modify: `occupify-core/src/main/java/com/occupify/core/service/impl/ProjectServiceImpl.java:85-93`

**Interfaces:**
- Produces: `ProjectCreatedEvent(UUID projectId, String title, String description, String status, UUID employerId, String employerEmail, LocalDateTime createdAt)`
- AMQP Exchange: `occupify.project.exchange` (TopicExchange), routing key `project.created`

- [x] **Step 1: Update `ProjectCreatedEvent.java` in `occupify-core`**
Add `description` and `status` fields to the record:
```java
package com.occupify.core.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectCreatedEvent(
    UUID projectId,
    String title,
    String description,
    String status,
    UUID employerId,
    String employerEmail,
    LocalDateTime createdAt)
    implements Serializable {}
```

- [x] **Step 2: Update configuration in `occupify-core/src/main/resources/application.yml`**
Add `project-exchange: ${RABBITMQ_PROJECT_EXCHANGE:occupify.project.exchange}` under `app.rabbitmq`.

- [x] **Step 3: Declare `projectExchange` bean in `RabbitMQConfig.java`**
Add:
```java
@Value("${app.rabbitmq.project-exchange:occupify.project.exchange}")
private String projectExchangeName;

@Bean
public TopicExchange projectExchange() {
  return new TopicExchange(projectExchangeName, true, false);
}
```

- [x] **Step 4: Update `ProjectEventProducerImpl.java`**
Inject `projectExchange` and publish to it:
```java
@Value("${app.rabbitmq.project-exchange:occupify.project.exchange}")
private String exchange;
```

- [x] **Step 5: Update `ProjectServiceImpl.java:createProject`**
Pass `saved.getDescription()` and `saved.getStatus().name()` into `new ProjectCreatedEvent(...)`.

- [x] **Step 6: Verify in `occupify-core`**
Run: `mvn test-compile -f occupify-core/pom.xml && mvn test -f occupify-core/pom.xml -Dtest=*Project*`
Expected: BUILD SUCCESS with 11 tests passing.

---

### Task 2: Configure RabbitMQ Topology & Event DTOs in `occupify-administration`

**Files:**
- Modify: `occupify-administration/src/main/resources/application.yml:31-44`
- Modify: `occupify-administration/src/test/resources/application-test.yml:21-30`
- Create: `occupify-administration/src/main/java/com/occupify/administration/event/ProjectCreatedEvent.java`
- Create: `occupify-administration/src/main/java/com/occupify/administration/event/ProjectClosedEvent.java`
- Modify: `occupify-administration/src/main/java/com/occupify/administration/config/RabbitMQConfig.java`

**Interfaces:**
- Consumes: `ProjectCreatedEvent`, `ProjectClosedEvent`
- AMQP Queue: `occupify.administration.project.queue` bound to `occupify.project.exchange` with `project.created` and `project.closed`

- [x] **Step 1: Create event DTO records in `occupify-administration`**
Create `ProjectCreatedEvent.java`:
```java
package com.occupify.administration.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectCreatedEvent(
    UUID projectId,
    String title,
    String description,
    String status,
    UUID employerId,
    String employerEmail,
    LocalDateTime createdAt)
    implements Serializable {}
```

Create `ProjectClosedEvent.java`:
```java
package com.occupify.administration.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectClosedEvent(
    UUID projectId,
    String title,
    UUID employerId,
    String employerEmail,
    LocalDateTime closedAt)
    implements Serializable {}
```

- [x] **Step 2: Add properties to `occupify-administration/src/main/resources/application.yml` and `application-test.yml`**
Add under `app.rabbitmq`:
```yaml
    project-exchange: ${RABBITMQ_PROJECT_EXCHANGE:occupify.project.exchange}
    project-queue: ${RABBITMQ_PROJECT_QUEUE:occupify.administration.project.queue}
    project-created-routing-key: ${RABBITMQ_PROJECT_CREATED_ROUTING_KEY:project.created}
    project-closed-routing-key: ${RABBITMQ_PROJECT_CLOSED_ROUTING_KEY:project.closed}
```

- [x] **Step 3: Update `RabbitMQConfig.java` in `occupify-administration`**
Declare `projectExchange`, `adminProjectQueue` (with dead letter exchange/routing key args), and bindings for `project.created` and `project.closed`.
Register `ProjectCreatedEvent` and `ProjectClosedEvent` class name mappings in `jackson2JsonMessageConverter`.

- [x] **Step 4: Verify compilation in `occupify-administration`**
Run: `mvn test-compile -f occupify-administration/pom.xml`
Expected: BUILD SUCCESS.

---

### Task 3: Implement `ProjectEventListener` in `occupify-administration`

**Files:**
- Create: `occupify-administration/src/main/java/com/occupify/administration/listener/ProjectEventListener.java`

**Interfaces:**
- Consumes: `ProjectCreatedEvent`, `ProjectClosedEvent`
- Repositories: `ProjectRepository`, `UserRepository`, `SystemAuditLogRepository`

- [x] **Step 1: Write `ProjectEventListener.java`**
Implement:
```java
package com.occupify.administration.listener;

import com.occupify.administration.entity.Project;
import com.occupify.administration.entity.SystemAuditLog;
import com.occupify.administration.entity.User;
import com.occupify.administration.enums.ActorRole;
import com.occupify.administration.enums.AuditStatus;
import com.occupify.administration.enums.ProjectStatus;
import com.occupify.administration.enums.TargetService;
import com.occupify.administration.enums.UserRole;
import com.occupify.administration.enums.UserStatus;
import com.occupify.administration.event.ProjectClosedEvent;
import com.occupify.administration.event.ProjectCreatedEvent;
import com.occupify.administration.repository.ProjectRepository;
import com.occupify.administration.repository.SystemAuditLogRepository;
import com.occupify.administration.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = "${app.rabbitmq.project-queue:occupify.administration.project.queue}")
public class ProjectEventListener {

  private final ProjectRepository projectRepository;
  private final UserRepository userRepository;
  private final SystemAuditLogRepository auditLogRepository;

  @Transactional
  @RabbitHandler
  public void handleProjectCreated(ProjectCreatedEvent event) {
    if (event == null || event.projectId() == null || event.employerId() == null) {
      log.warn("[Admin Project Listener] Received invalid ProjectCreatedEvent: {}", event);
      return;
    }

    log.info("[Admin Project Listener] Processing ProjectCreatedEvent for project [{}] ('{}')", event.projectId(), event.title());

    User employer = userRepository.findById(event.employerId())
        .orElseGet(() -> {
          String email = StringUtils.hasText(event.employerEmail())
              ? event.employerEmail().trim().toLowerCase()
              : "employer-" + event.employerId() + "@occupify.local";
          log.info("[Admin Project Listener] Employer [{}] not found; creating stub user with email {}", event.employerId(), email);
          return userRepository.save(
              User.builder()
                  .id(event.employerId())
                  .email(email)
                  .fullName("Employer " + event.employerId().toString().substring(0, 8))
                  .role(UserRole.CLIENT)
                  .status(UserStatus.ACTIVE)
                  .createdAt(Instant.now())
                  .build());
        });

    Project project = projectRepository.findById(event.projectId())
        .orElseGet(() -> Project.builder().id(event.projectId()).build());

    project.setEmployer(employer);
    project.setTitle(StringUtils.hasText(event.title()) ? event.title().trim() : "Untitled Project");
    project.setDescription(event.description());
    project.setStatus(parseProjectStatus(event.status()));

    Instant createdInstant = event.createdAt() != null
        ? event.createdAt().toInstant(ZoneOffset.UTC)
        : Instant.now();
    project.setCreatedAt(createdInstant);

    projectRepository.save(project);

    SystemAuditLog auditLog = SystemAuditLog.builder()
        .id(UUID.randomUUID())
        .actor(employer)
        .actorRole(ActorRole.EMPLOYER)
        .action("PROJECT_CREATED")
        .targetService(TargetService.CORE)
        .targetEntity("PROJECT")
        .targetId(event.projectId().toString())
        .status(AuditStatus.SUCCESS)
        .createdAt(Instant.now())
        .build();
    auditLogRepository.save(auditLog);

    log.info("[Admin Project Listener] Successfully persisted project [{}] and audit log", event.projectId());
  }

  @Transactional
  @RabbitHandler
  public void handleProjectClosed(ProjectClosedEvent event) {
    if (event == null || event.projectId() == null) {
      log.warn("[Admin Project Listener] Received invalid ProjectClosedEvent: {}", event);
      return;
    }

    log.info("[Admin Project Listener] Processing ProjectClosedEvent for project [{}]", event.projectId());

    projectRepository.findById(event.projectId()).ifPresent(project -> {
      project.setStatus(ProjectStatus.CLOSED);
      Instant closedInstant = event.closedAt() != null
          ? event.closedAt().toInstant(ZoneOffset.UTC)
          : Instant.now();
      project.setClosedAt(closedInstant);
      projectRepository.save(project);

      User employer = event.employerId() != null
          ? userRepository.findById(event.employerId()).orElse(project.getEmployer())
          : project.getEmployer();

      SystemAuditLog auditLog = SystemAuditLog.builder()
          .id(UUID.randomUUID())
          .actor(employer)
          .actorRole(ActorRole.EMPLOYER)
          .action("PROJECT_CLOSED")
          .targetService(TargetService.CORE)
          .targetEntity("PROJECT")
          .targetId(event.projectId().toString())
          .status(AuditStatus.SUCCESS)
          .createdAt(Instant.now())
          .build();
      auditLogRepository.save(auditLog);

      log.info("[Admin Project Listener] Successfully marked project [{}] as CLOSED", event.projectId());
    });
  }

  private ProjectStatus parseProjectStatus(String statusStr) {
    if (!StringUtils.hasText(statusStr)) {
      return ProjectStatus.OPEN;
    }
    try {
      return ProjectStatus.valueOf(statusStr.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("[Admin Project Listener] Unknown project status [{}], defaulting to OPEN", statusStr);
      return ProjectStatus.OPEN;
    }
  }
}
```

- [x] **Step 2: Verify compilation**
Run: `mvn test-compile -f occupify-administration/pom.xml`
Expected: BUILD SUCCESS.

---

### Task 4: Unit Testing & Verification in `occupify-administration`

**Files:**
- Create: `occupify-administration/src/test/java/com/occupify/administration/listener/ProjectEventListenerTest.java`

- [x] **Step 1: Write `ProjectEventListenerTest.java`**
Implement unit tests covering:
1. `handleProjectCreated_whenEmployerExists_persistsProjectAndAuditLog`
2. `handleProjectCreated_whenEmployerMissing_createsFallbackEmployerAndProject`
3. `handleProjectCreated_whenInvalidEvent_skipsProcessing`
4. `handleProjectClosed_whenProjectExists_updatesStatusToClosedAndLogsAudit`
5. `handleProjectClosed_whenInvalidEvent_skipsProcessing`

- [x] **Step 2: Run test suite in `occupify-administration`**
Run: `mvn test -f occupify-administration/pom.xml -Dtest=ProjectEventListenerTest`
Expected: All 5 tests PASS.

- [x] **Step 3: Run full test suite in `occupify-administration`**
Run: `mvn test -f occupify-administration/pom.xml`
Expected: All tests PASS.

- [x] **Step 4: Run full test suite in `occupify-core`**
Run: `mvn test -f occupify-core/pom.xml`
Expected: All tests PASS.
