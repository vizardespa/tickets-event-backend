# Tickets & Events API

A small REST API for managing **events** and the **tickets** purchased for them, built with
[Ktor](https://ktor.io/) (Kotlin) using the Netty engine and in‑memory storage.

## Tech stack

- **Kotlin** 1.9.24
- **Ktor** 2.3.12 (Netty engine)
- **kotlinx.serialization** for JSON
- **Gradle** (Kotlin DSL) with the application plugin
- In‑memory storage using `ConcurrentHashMap` (no database required)
- Content negotiation (JSON), Status Pages (error handling), CORS

## API

Base URL: `http://localhost:8080`

### Health
| Method | Path      | Description                        |
|--------|-----------|------------------------------------|
| GET    | `/health` | Health check → `{"status":"ok"}`   |

### Events
| Method | Path                | Description        |
|--------|---------------------|--------------------|
| GET    | `/api/events`       | List all events    |
| GET    | `/api/events/{id}`  | Get an event by id |
| POST   | `/api/events`       | Create an event    |
| PUT    | `/api/events/{id}`  | Update an event    |
| DELETE | `/api/events/{id}`  | Delete an event    |

### Tickets
| Method | Path                 | Description                       |
|--------|----------------------|-----------------------------------|
| GET    | `/api/tickets`       | List all tickets                  |
| GET    | `/api/tickets/{id}`  | Get a ticket by id                |
| POST   | `/api/tickets`       | Purchase a ticket for an event    |
| DELETE | `/api/tickets/{id}`  | Cancel a ticket                   |

## Data models

**Event**

```json
{
  "id": "uuid",
  "name": "Kotlin Conf 2026",
  "description": "Annual conference for Kotlin developers.",
  "venue": "Amsterdam Convention Center",
  "date": "2026-05-14T09:00:00Z",
  "totalSeats": 500,
  "availableSeats": 499,
  "price": 299.0,
  "createdAt": "2026-01-01T10:00:00Z"
}
```

**Ticket**

```json
{
  "id": "uuid",
  "eventId": "uuid",
  "ownerName": "Ada Lovelace",
  "ownerEmail": "ada@example.com",
  "seatNumber": 1,
  "status": "ACTIVE",
  "purchasedAt": "2026-01-01T10:05:00Z"
}
```

`status` is one of `ACTIVE` or `CANCELLED`.

## Request examples

Create an event:

```bash
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Show",
    "description": "A great show",
    "venue": "Main Hall",
    "date": "2026-09-01T19:00:00Z",
    "totalSeats": 100,
    "price": 25.0
  }'
```

Purchase a ticket (seatNumber optional — auto-assigned if omitted):

```bash
curl -X POST http://localhost:8080/api/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "<event-id>",
    "ownerName": "Ada Lovelace",
    "ownerEmail": "ada@example.com"
  }'
```

Cancel a ticket:

```bash
curl -X DELETE http://localhost:8080/api/tickets/<ticket-id>
```

## Running locally

Requires JDK 17.

```bash
./gradlew run
```

The server starts on `http://localhost:8080`. Override the port with the `PORT` env var.

Build a distributable:

```bash
./gradlew build
./gradlew installDist   # creates build/install/tickets-event-backend
```

## Running with Docker Compose

```bash
docker compose up --build
```

This builds the multi-stage image (Gradle builder → JRE 17 runtime) and starts the
service on port `8080` with a health check against `/health`.

Stop it with:

```bash
docker compose down
```

## Project structure

```
tickets-event-backend/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── Dockerfile
├── docker-compose.yml
└── src/main/
    ├── kotlin/com/tickets/
    │   ├── Application.kt          # entry point, plugins, routing
    │   ├── models/Models.kt        # data models
    │   ├── routes/EventRoutes.kt   # event endpoints
    │   ├── routes/TicketRoutes.kt  # ticket endpoints
    │   └── services/               # in-memory business logic
    └── resources/
        ├── application.conf        # Ktor config (port 8080)
        └── logback.xml             # logging config
```
