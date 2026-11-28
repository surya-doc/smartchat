# SmartChat

[![CI](https://github.com/surya-doc/smartchat/actions/workflows/ci.yml/badge.svg)](https://github.com/surya-doc/smartchat/actions/workflows/ci.yml)

A real-time chat platform: public and private rooms, direct messages, replies, reactions, typing indicators, presence and notifications, built with **Spring Boot 4, STOMP over WebSocket, MySQL, Redis and Kafka**.

> 🚧 In active development. See the [roadmap](#roadmap) below.

## Features

- **Auth:** register/login with JWT; every new user joins `#general`
- **Rooms:** public rooms (join instantly) and private rooms (invite links or admin-approved join requests)
- **Messaging:** real-time room messages and DMs over STOMP; replies, edit, delete (admins can moderate), emoji reactions, search
- **Presence:** online/offline from server-side WebSocket session events, shared across instances in Redis, correct across tabs, and self-healing if an instance crashes
- **Horizontal scaling:** WebSocket events fan out through Redis Pub/Sub, so users on different instances still see each other's messages
- **Rate limiting:** per-user message limit shared by all instances (HTTP 429 + `Retry-After`)
- **Event pipeline:** messages emit events through a transactional outbox to Kafka; consumers are idempotent, retry with backoff and park poison messages in a dead-letter topic
- **Unread counts:** per user and room, maintained asynchronously by a Kafka consumer in Redis
- **Notifications:** stored, and pushed live to the user's open sessions
- **Security:** JWT on REST and on STOMP CONNECT; room membership enforced on every read, write and subscription
- **Errors:** consistent RFC 9457 ProblemDetail JSON for every failure

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 17, Spring Boot 4, Spring Security, Spring WebSocket (STOMP), Spring Data JPA |
| Data | MySQL 8.4 with Flyway migrations (`ddl-auto: validate`) |
| Real-time & state | Redis (Pub/Sub fan-out, presence, rate limiting, cache) |
| Events | Kafka (transactional outbox, idempotent consumers, retries, DLT) |
| Tests | JUnit 5, Mockito, Testcontainers, MockMvc |
| CI | GitHub Actions |

## Run it locally

Prerequisites: JDK 17+, Docker Desktop.

```bash
git clone https://github.com/surya-doc/smartchat.git
cd smartchat
docker compose up -d          # MySQL on 3307, Redis on 6379, Kafka on 9092
./mvnw spring-boot:run        # Windows PowerShell: .\mvnw spring-boot:run
```

The API runs on `http://localhost:8080` using the `dev` profile (`src/main/resources/application-dev.yml`).

Run the tests (Docker must be running):

```bash
./mvnw verify
```

## Configuration

`application.yml` reads all environment-specific values from environment variables:

| Variable | Purpose |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection |
| `JWT_SECRET` | HS256 signing key, at least 32 bytes |
| `JWT_EXPIRATION_MS` | Token lifetime (default 24h) |
| `FRONTEND_URL` | Used to build invite links |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins |
| `REDIS_URL` | Redis connection, e.g. `redis://localhost:6379` or `rediss://user:pass@host:port` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers (default `localhost:9092`) |

## API overview

Ready-to-run requests: [`docs/api-examples.http`](docs/api-examples.http) (VS Code REST Client or IntelliJ HTTP Client).

| Method | Path | Description |
|---|---|---|
| POST | `/api/auth/register` | Create an account, returns a JWT |
| POST | `/api/auth/login` | Log in, returns a JWT |
| GET | `/api/users/me` | Current user's profile |
| GET | `/api/users/online` | Usernames online right now |
| GET | `/api/rooms` | Public rooms |
| GET | `/api/rooms/mine` | Rooms I'm a member of |
| GET | `/api/rooms/unread` | Unread count per room for me (`{ roomId: count }`) |
| POST | `/api/rooms/{id}/read` | Mark a room as read |
| POST | `/api/rooms` | Create a room (`isPrivate` for private) |
| POST | `/api/rooms/{id}/join` | Join a public room / request to join a private one |
| GET | `/api/rooms/{id}/messages` | Latest 50 messages (members only) |
| POST | `/api/rooms/{id}/messages` | Post a message (REST alternative to STOMP `/app/chat.send`) |
| GET | `/api/rooms/{id}/messages/search?q=` | Search messages |
| POST | `/api/rooms/{id}/invites` | Create an invite link (admins, private rooms) |
| POST | `/api/rooms/invites/{token}/accept` | Accept an invite |
| GET | `/api/rooms/{id}/join-requests` | Pending requests (admins) |
| POST | `/api/rooms/{id}/join-requests/{reqId}/approve` \| `reject` | Review a request |
| PUT / DELETE | `/api/messages/{id}` | Edit / delete a message |
| POST | `/api/messages/{id}/reactions` | Toggle an emoji reaction |
| POST / GET | `/api/dm`, `/api/dm/{userId}` | Send a DM / read a conversation |
| GET | `/api/notifications`, `/api/notifications/unread` | Notifications |

### WebSocket (STOMP)

Connect to `/ws` (SockJS) with header `Authorization: Bearer <jwt>` on CONNECT.

| Direction | Destination | Payload |
|---|---|---|
| send | `/app/chat.send` | `{ roomId, content, replyToId? }` |
| send | `/app/dm.send` | `{ receiverId, content }` |
| send | `/app/chat.typing` | `{ roomId, typing }` |
| subscribe | `/topic/room.{id}` | new or updated message (upsert by `id`) |
| subscribe | `/topic/room.{id}.typing` | `{ roomId, username, typing }` |
| subscribe | `/topic/presence` | `{ username, online }` |
| subscribe | `/user/queue/dm` | direct messages |
| subscribe | `/user/queue/notifications` | notifications |
| subscribe | `/user/queue/errors` | `{ message }` when a frame is rejected |

## Roadmap

- [x] Flyway-managed schema, ProblemDetail errors, room-level authorization on REST and STOMP, integration tests, CI
- [x] Redis: shared presence, rate limiting, caching, cross-instance WebSocket fan-out
- [x] Kafka: transactional outbox, unread counters, idempotent consumers, retries and a dead-letter topic
- [ ] Angular + PrimeNG frontend
- [ ] Google sign-in (OAuth2) and Stripe Checkout for Premium
- [ ] Deployment with a live demo, architecture diagram and API docs (OpenAPI)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions and the development workflow.
