# Clud

Clud is an educational cloud file storage system built as a set of Spring Boot
services. The current foundation provides containerized applications, a reverse
proxy, and the local infrastructure required for further development.

## Architecture

```text
Client -> Nginx -> Gateway

Gateway / Identity / File / Storage / Sharing
                         |
        PostgreSQL / Redis / Kafka / MinIO
```

All containers share the default Docker Compose network. Nginx forwards public
HTTP traffic to the gateway. Application routing and business endpoints will be
implemented in later phases.

## Prerequisites

- Docker with Docker Compose
- Java 21 or newer for running services outside Docker

## Local startup

Create the local environment file once:

```bash
cp .env.example .env
```

Build and start the complete stack:

```bash
docker compose up -d --build
```

Check container and health statuses:

```bash
docker compose ps
```

Follow logs:

```bash
docker compose logs -f
```

Stop the stack while preserving data:

```bash
docker compose down
```

Stop the stack and remove all local data volumes:

```bash
docker compose down -v
```

## Local ports

| Component | Address |
| --- | --- |
| Nginx | `http://localhost:80` |
| Gateway | `http://localhost:8080` |
| Identity | `http://localhost:8081` |
| File | `http://localhost:8082` |
| Storage | `http://localhost:8083` |
| Sharing | `http://localhost:8084` |
| PostgreSQL | `localhost:5432` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |

The application services expose readiness information at
`/actuator/health/readiness`. A `404` response from the application root is
expected until controllers are implemented in the downstream services.

## Gateway routes

Nginx forwards public traffic to the gateway. The gateway removes the first two
path segments and proxies requests as follows:

| Public path | Destination |
| --- | --- |
| `/api/identity/**` | Identity service |
| `/api/files/**` | File service |
| `/api/storage/**` | Storage service |
| `/api/sharing/**` | Sharing service |

For example, `/api/files/folders/123` is forwarded to `/folders/123` on the file
service. Query parameters, request bodies, response statuses, and headers are
preserved. The gateway also preserves an incoming `X-Request-ID` header or
generates one when it is absent, then sends the same value to the destination
service and returns it in the response.

Destination URLs are configured through `IDENTITY_SERVICE_URL`,
`FILE_SERVICE_URL`, `STORAGE_SERVICE_URL`, and `SHARING_SERVICE_URL`. Docker
Compose reads their container-network values from `.env`. When running the
gateway directly on the host, export the same variables with the corresponding
`localhost` ports listed above.

Kafka uses separate listeners:

- applications inside Docker connect to `kafka:29092`;
- tools running on the host connect to `localhost:9092`.

## Running tests

Each service has an independent Maven build and Maven Wrapper. For example:

```bash
cd gateway
./mvnw test
```

Use the same command from `identity`, `file`, `storage`, or `sharing` to test a
specific service.

## Configuration and data

Local defaults are documented in `.env.example`. Do not commit `.env` or place
production credentials in the example file.

PostgreSQL, Redis, Kafka, and MinIO store their state in named Docker volumes.
Regular `docker compose down` preserves those volumes; `docker compose down -v`
deletes them.
