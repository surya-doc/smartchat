# Contributing to SmartChat

## Stack

- **Backend:** Java 17, Spring Boot 4, Spring Security (JWT), STOMP over WebSocket, Spring Data JPA, MySQL 8.4, Flyway, Redis
- **Tests:** JUnit 5, Mockito, Testcontainers (real MySQL and Redis), MockMvc, Spring's STOMP client
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw verify` on every push

## Conventions

- **Layers:** controller → service → repository. Controllers stay thin; services return DTOs, never entities.
- **Errors:** throw `ResourceNotFoundException` (404), `ForbiddenException` (403), `ConflictException` (409) or `BadRequestException` (400). `GlobalExceptionHandler` turns them into RFC 9457 ProblemDetail JSON. Never throw a bare `RuntimeException`.
- **Identity:** the Spring Security principal name is the user's **email**, both for REST (`@AuthenticationPrincipal UserDetails#getUsername`) and STOMP (`Principal#getName`). User WebSocket destinations are addressed by email.
- **Authorization:** room access goes through `RoomAccessService` (REST, STOMP handlers, and SUBSCRIBE in `StompAuthChannelInterceptor`).
- **Real-time:** services publish `RoomMessageEvent` / `UserQueueEvent`; `RealtimeEventRelay` pushes them **after the transaction commits** through `WebSocketPublisher`, which goes via Redis Pub/Sub so every instance delivers to its own sessions. Only `RealtimeRedisSubscriber` talks to `SimpMessagingTemplate`.
- **Shared state lives in Redis**, never in instance memory (presence, rate limits). Key prefix: `smartchat:`.
- **Schema:** Flyway owns it (`src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`. Never edit a migration that has been merged; add a new `V<n>__...sql`.
- **Queries:** avoid N+1 (use `@EntityGraph` or batch queries); use bulk `@Modifying` updates for mass changes.
- **Config:** everything environment-specific comes from env vars (`application.yml`); local values live in `application-dev.yml`. Personal overrides go in `application-local.yml` (gitignored).
- **Tests:** API tests extend `IntegrationTestBase` (shared context + MySQL container); each test registers its own users so tests stay independent.

## Workflow

- One branch and one pull request per feature, named after what it does: `feature/<functionality>` (e.g. `feature/redis-presence-fanout-rate-limiting`), `fix/<problem>`, `docs/<topic>`.
- Pull requests are squash-merged. CI must be green before review.
- PR descriptions explain the design decisions, not just the diff.

## Run locally

```bash
docker compose up -d        # MySQL :3307, Redis :6379, Kafka :9092
./mvnw spring-boot:run      # http://localhost:8080 (dev profile)
./mvnw verify               # all tests (needs Docker for Testcontainers)
```
