# Clud

Clud is an educational cloud file storage system built as five Spring Boot
services. The repository contains the applications, Nginx, and the local
infrastructure needed to run the complete system.

## Architecture

```text
Client -> Nginx -> Gateway
                    |
                    +-> Identity
                    +-> File
                    +-> Storage
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

### Root variables

| Variable group | Purpose |
| --- | --- |
| `NGINX_PORT`, `*_PORT` | Ports published from containers to the host |
| `*_SERVICE_URL` | Addresses used by Gateway inside the Compose network |
| `POSTGRES_*` | PostgreSQL database, credentials, and host port |
| `REDIS_PORT` | Redis host port |
| `MINIO_*` | MinIO credentials and API/console ports |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka address supplied to application containers |
| `KAFKA_NODE_ID`, `KAFKA_PROCESS_ROLES`, `KAFKA_PORT` | Local Kafka node configuration |

Do not place production credentials in `.env.example`.

## Full Docker startup

Create the root environment file and start the complete stack:

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

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

The same approach works for Identity, Storage, or Sharing by replacing the
corresponding service URL. Compose maps `host.docker.internal` to the Linux
host for Gateway.

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

## Gateway routes

| Public path | Destination |
| --- | --- |
| `/api/identity/**` | Identity |
| `/api/files/**` | File |
| `/api/storage/**` | Storage |
| `/api/sharing/**` | Sharing |

Gateway removes the first two path segments before forwarding a request. It
also preserves an incoming `X-Request-ID` or generates one when absent.

## File metadata service

The File Service persists files and folders in the `file_service` PostgreSQL
schema. It supports directory browsing, rename, move, recursive trash, restore,
and versioned Kafka lifecycle events. File bytes remain owned by the future
Storage Service.

Business requests require an `X-User-ID` UUID. This is a temporary development
contract until Identity authentication allows Gateway to supply a trusted user
header. See [the File Service documentation](file/README.md) for endpoints,
examples, persistence rules, and event payloads.

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
