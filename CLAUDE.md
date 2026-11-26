# SmartChat: project brief

Real-time chat app built as a portfolio project by Surya Dana, a full-stack Java developer with 3+ years of experience. The bar is **production quality that a 3-year engineer can defend in interviews**, not a tutorial project. Every design decision should have a reason you can explain.

## Stack

- **Backend:** Java 17, Spring Boot 4, Spring Security (JWT), STOMP over WebSocket, Spring Data JPA, MySQL 8.4, Flyway
- **Coming:** Redis (presence, unread counts, rate limiting, cache, cross-instance fan-out), Kafka (chat events), Google OAuth2, Stripe Checkout (test mode), Angular + PrimeNG frontend
- **Tests:** JUnit 5, Mockito, Testcontainers (real MySQL), MockMvc
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw verify` on every push

## Conventions

- **Layers:** controller → service → repository. Controllers stay thin; services return DTOs, never entities.
- **Errors:** throw `ResourceNotFoundException` (404), `ForbiddenException` (403), `ConflictException` (409) or `BadRequestException` (400). `GlobalExceptionHandler` turns them into RFC 9457 ProblemDetail JSON. Never throw a bare `RuntimeException`.
- **Identity:** the Spring Security principal name is the user's **email**, both for REST (`@AuthenticationPrincipal UserDetails#getUsername`) and STOMP (`Principal#getName`). User WebSocket destinations are addressed by email.
- **Authorization:** room access goes through `RoomAccessService` (REST, STOMP handlers, and SUBSCRIBE in `StompAuthChannelInterceptor`).
- **Real-time:** services publish `RoomMessageEvent` / `UserQueueEvent`; `RealtimeEventRelay` pushes them **after the transaction commits**. Don't call `SimpMessagingTemplate` from services.
- **Schema:** Flyway owns it (`src/main/resources/db/migration`). Hibernate runs with `ddl-auto: validate`. Never edit a migration that has been merged; add a new `V<n>__...sql`.
- **Queries:** avoid N+1 (use `@EntityGraph` or batch queries); use bulk `@Modifying` updates for mass changes.
- **Config:** everything environment-specific comes from env vars (`application.yml`); local values live in `application-dev.yml`.
- **Tests:** API tests extend `IntegrationTestBase` (shared context + MySQL container); each test registers its own users.

## Workflow

- One branch and one PR per day (`day-N/<topic>`). CI must be green before review.
- Every PR description explains the design decisions and lists interview questions about the change.
- Surya has 2–3 hours each evening: he pulls the PR, runs it, reviews it, and does a ~15-min interview-style Q&A on it.

## 7-day plan

| Day | Scope |
|---|---|
| 1 | Foundations: config, Docker Compose, Flyway V2, error handling, security fixes, tests, CI ✅ |
| 2 | Redis (presence, unread counts, rate limiting, cache, cross-instance fan-out) + Kafka (chat events, idempotent consumers, retries + DLT) |
| 3 | Angular core: auth, room list, live chat with STOMP, history pagination |
| 4 | Angular features: DMs, typing, presence, reactions, replies, notifications, search |
| 5 | Google OAuth2 login (issues our JWT) + Stripe Checkout Premium with verified webhook |
| 6 | Deploy: Aiven free tier (Kafka, MySQL, Valkey), backend container host, Angular on Netlify |
| 7 | More tests, OpenAPI docs, README with architecture diagram, demo account, polish |

## Run locally

```bash
docker compose up -d        # MySQL :3307, Redis :6379, Kafka :9092
./mvnw spring-boot:run      # http://localhost:8080 (dev profile)
./mvnw verify               # all tests (needs Docker for Testcontainers)
```
