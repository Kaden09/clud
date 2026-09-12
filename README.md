# Clud

Clud is an educational cloud file storage system built as five Spring Boot
services. The repository contains the applications, Nginx, and the local
infrastructure needed to run the complete system.

## Architecture

```text
Client -> Nginx -> Gateway
                    |
                    +-> Identity
                    +-> File -> Storage -> MinIO
                    +-> Sharing
                              |
             PostgreSQL / Redis / Kafka / MinIO
```

All Docker containers share the default Compose network. Nginx forwards public
traffic to Gateway, which selects the destination service by request path.

## Services

| Service | Documentation | Host port |
| --- | --- | --- |
| Gateway | [gateway/README.md](gateway/README.md) | `8080` |
| Identity | [identity/README.md](identity/README.md) | `8081` |
| File | [file/README.md](file/README.md) | `8082` |
| Storage | [storage/README.md](storage/README.md) | `8083` |
| Sharing | [sharing/README.md](sharing/README.md) | `8084` |

## Prerequisites

- Docker with Docker Compose
- Java 21 or newer for running services outside Docker

## Environment files

The repository uses two kinds of dotenv files:

- the root `.env` configures Docker Compose and container-to-container
  addresses;
- `<service>/.env` configures a service started locally from its own
  directory.

Create only the files required for the way you are running the project:

```bash
cp .env.example .env
cp file/.env.example file/.env
```

Every service has its own `.env.example` and README with the supported
variables. Quoted dotenv values are supported. Real environment variables take
precedence over values from `.env`. All `.env` files are ignored by Git.
When starting a service from an IDE, set its service directory as the working
directory so that the matching local `.env` file is loaded.

### Root variables

| Variable group | Purpose |
| --- | --- |
| `NGINX_HOST_PORT`, `*_HOST_PORT` | Ports published from containers to the host |
| `*_SERVICE_URL` | Internal service addresses inside the Compose network |
| `POSTGRES_*` | PostgreSQL database, credentials, and host port |
| `REDIS_HOST`, `REDIS_PORT` | Redis address supplied to application containers |
| `REDIS_HOST_PORT` | Redis port published to the host |
| `MINIO_*` | MinIO credentials and API/console ports |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka address supplied to application containers |
| `KAFKA_NODE_ID`, `KAFKA_PROCESS_ROLES`, `KAFKA_PORT` | Local Kafka node configuration |
| `IDENTITY_*`, `JWT_*`, `CORS_*`, `COOKIE_*` | Identity database and sessions plus shared Gateway token verification and browser CORS |
| `SHARING_*` | Sharing database, public URL, timeouts, and event topic |
| `PROMETHEUS_PORT`, `GRAFANA_*`, `LOKI_PORT`, `TEMPO_PORT` | Local observability endpoints and Grafana credentials |

Do not place production credentials in `.env.example`.

## Full Docker startup

Create the root environment file and start the complete stack:

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

Published ports are bound to `127.0.0.1` and are available only on the local
machine.

Follow logs or stop the stack:

```bash
docker compose logs -f
docker compose down
```

Use `docker compose down -v` only when the local data volumes should also be
deleted.

## Hybrid development

Docker Compose can run selected services while one application runs directly
on the host.

### Run Gateway locally

Start infrastructure and all downstream services:

```bash
docker compose up -d postgres redis minio kafka identity file storage sharing
```

Then start Gateway with its local addresses:

```bash
cd gateway
cp .env.example .env
./mvnw spring-boot:run
```

When Gateway runs locally, Nginx is intentionally bypassed because its current
upstream points to the Docker `gateway` service. Start test requests directly
at `http://localhost:8080`.

### Run File locally with Gateway in Docker

Start infrastructure and the remaining downstream services:

```bash
docker compose up -d postgres redis minio kafka identity storage sharing
```

Start File on the host:

```bash
cd file
cp .env.example .env
./mvnw spring-boot:run
```

From another shell at the repository root, point the Dockerized Gateway to the
host service and start the public path:

```bash
FILE_SERVICE_URL=http://host.docker.internal:8082 \
  docker compose up -d gateway nginx
```

The same approach works for Identity or Sharing by replacing the corresponding
Gateway service URL. For a locally running Storage service, point File Service
at `http://host.docker.internal:8083`. Compose maps
`host.docker.internal` to the Linux host.

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

Application readiness is available at `/actuator/health/readiness`.

Kafka has separate addresses:

- containers connect to `kafka:29092`;
- applications running on the host connect to `localhost:9092`.

## OpenAPI

Every service exposes Swagger UI at `/docs` and its OpenAPI document at
`/v3/api-docs`.

| Service | Swagger UI | OpenAPI document |
| --- | --- | --- |
| Gateway | `http://localhost:8080/docs` | `http://localhost:8080/v3/api-docs` |
| Identity | `http://localhost:8081/docs` | `http://localhost:8081/v3/api-docs` |
| File | `http://localhost:8082/docs` | `http://localhost:8082/v3/api-docs` |
| Storage | `http://localhost:8083/docs` | `http://localhost:8083/v3/api-docs` |
| Sharing | `http://localhost:8084/docs` | `http://localhost:8084/v3/api-docs` |

Gateway Swagger UI also lists the routed Identity, File, and Sharing
specifications. Storage remains internal and is intentionally not routed through
Gateway.

## Gateway routes

| Public path | Destination |
| --- | --- |
| `/api/identity/**` | Identity |
| `/api/files/**` | File |
| `/api/sharing/**` | Sharing |

Gateway removes the first two path segments before forwarding a request. It
also preserves an incoming `X-Request-ID` or generates one when absent.
Gateway validates Identity access tokens for protected API routes, removes any
client-provided `X-User-ID`, and forwards the authenticated token subject as
the trusted user header. Refresh cookies use the public browser path
`/api/identity/auth`, because cookie matching happens before Gateway strips
the service prefix.

## Identity service

Identity owns registration, login, refresh-token rotation, logout, and the current user profile. Access tokens are bearer JWTs; refresh tokens are HttpOnly cookies backed by hashed PostgreSQL sessions. Successful registration publishes the versioned `UserRegistered` event. See [the Identity Service documentation](identity/README.md) for its API and local setup.

## File metadata service

The File Service persists files and folders in the `file_service` PostgreSQL
schema. It supports directory browsing, rename, move, recursive trash, restore,
and versioned Kafka lifecycle events. It orchestrates binary uploads and
downloads through internal Storage Service UUID keys; clients use only `fileId`.

Business requests receive a trusted `X-User-ID` UUID from Gateway after access
token validation. Client-provided values are removed before routing. See
[the File Service documentation](file/README.md) for endpoints, examples,
persistence rules, and event payloads.

## Public sharing service

The Sharing Service persists one active public link per owner and file in the
`sharing_service` PostgreSQL schema. It validates ownership and file state
through File Service, supports optional expiration and revocation, publishes
`FileShared`, and consumes `FileDeleted` to revoke links.

The public token resolves to safe metadata first. Separate preview and download
endpoints stream bytes from Storage without exposing the internal object key;
active formats such as HTML and SVG are download-only. See
[the Sharing Service documentation](sharing/README.md) for the API, preview
policy, token storage, and events.

## Testing

Each service has an independent Maven Wrapper:

```bash
cd gateway
./mvnw test
```

Use the same command inside `identity`, `file`, `storage`, or `sharing`.
GitHub Actions builds and tests all five services independently on pull
requests and pushes to `dev` or `main`.

## Data

PostgreSQL, Redis, Kafka, and MinIO use named Docker volumes. A regular
`docker compose down` preserves them; `docker compose down -v` deletes them.

## API error contract

All five applications use the same JSON error contract for controller, HTTP routing,
security, and servlet error responses. Successful DTOs, streams, and empty responses
retain their existing formats.

```json
{
  "timestamp": "2026-09-06T19:00:00Z",
  "status": 404,
  "code": "NOT_FOUND",
  "message": "The requested endpoint does not exist",
  "path": "/unknown"
}
```

- `timestamp`: UTC time when the error was created.
- `status`: the actual HTTP response status, repeated in the body.
- `code`: the canonical Spring `HttpStatus` enum name for that status. It is derived,
  never assigned independently. There is no separate domain error catalogue.
- `message`: a safe explanation of this particular failure. Clients should not parse
  it or depend on its exact wording to make decisions.
- `path`: the request URI seen by the application producing the error, without the query.
- `fieldErrors`: an optional map from field names to validation messages. It is omitted
  when empty, including security and server errors. It never contains exception causes.

For example, both an unknown endpoint and a missing file use `404 / NOT_FOUND`, with
specific descriptions in `message`. Validation and malformed JSON use `400 / BAD_REQUEST`.
The removal of old domain codes is an intentional API contract change: clients using
`NODE_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `INVALID_ACCESS_TOKEN`, etc. must switch to
HTTP status / standard code handling.

| HTTP | Code | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Invalid JSON, parameters, or validation |
| 401 | `UNAUTHORIZED` | Missing, invalid, or expired authentication |
| 403 | `FORBIDDEN` | Access denied |
| 404 | `NOT_FOUND` | Endpoint or resource does not exist |
| 405 | `METHOD_NOT_ALLOWED` | Unsupported method; `Allow` is preserved |
| 406 | `NOT_ACCEPTABLE` | Unsupported response media type |
| 409 | `CONFLICT` | Email, name, or concurrent update conflict |
| 410 | `GONE` | Expired public link |
| 413 | `CONTENT_TOO_LARGE` | Request exceeds its size limit |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported request type or preview format |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected application/storage failure |
| 502 | `BAD_GATEWAY` | Gateway cannot reach upstream; File cannot reach Storage |
| 503 | `SERVICE_UNAVAILABLE` | Service/dependency temporarily unavailable |
| 504 | `GATEWAY_TIMEOUT` | Gateway upstream request timed out |

Validation example (the timestamp follows the same contract as above):

```json
{
  "timestamp": "2026-09-06T19:00:00Z",
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "Request validation failed",
  "path": "/auth/register",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "password": "size must be between 8 and 72"
  }
}
```

## Routing and security

Direct `GET /` on ports 8080–8084 returns `404 / NOT_FOUND`. Identity uses the registered
controller path patterns to let missing endpoints reach MVC; all registered protected
controllers remain authenticated by default. Matching ignores the method so an unsupported
method cannot bypass authentication. Actuator retains its own existing security rules.
Error dispatches may render their original error without being changed to 401.

Gateway continues authenticating `/api/**` before forwarding. An anonymous request inside
a protected API prefix can therefore receive 401 before the upstream route is examined.
With valid credentials, an unknown upstream endpoint returns 404. Gateway uses a two-second
connection timeout so unreachable services fail promptly. It does not query
other services to discover their controller mappings.

Gateway forwards completed upstream responses without rewriting their JSON. With prefix
stripping, a downstream error may contain `/user/me` while a locally generated Gateway
error contains `/api/identity/user/me`. This identifies the path seen by the producing
application. `X-Request-ID` continues to correlate requests through Gateway.

The servlet fallback returns JSON even for browser requests. HEAD error responses have
no body, as required by HTTP. Once a download has committed its response, an error cannot
replace the stream with JSON. Errors generated by Nginx itself (for example when Gateway
is stopped), a container rejecting an invalid HTTP request, or transport failures are
outside the applications' JSON contract.

## Verification

Each application has contract and MVC routing/binding tests. Identity additionally tests
unknown paths with absent, invalid, and valid tokens plus protected endpoints. Gateway
tests functional-route connection failures/timeouts and unchanged upstream error bodies.

The Compose smoke job starts the complete stack, waits for its health checks, and sends one
request through Nginx. Detailed error contract cases remain in the service tests. Gateway
integration tests deterministically
verify connection failures as `502 / BAD_GATEWAY` and timeouts as
`504 / GATEWAY_TIMEOUT`.
