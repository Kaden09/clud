# Sharing Service

The Sharing Service owns public links for Clud files. It creates one active
link per owner and file, supports optional expiration and revocation, and
publishes the versioned `FileShared` event.

Public links expose file metadata, safe inline preview, and explicit download.
Sharing streams bytes from Storage Service by an opaque internal key; it never
accesses MinIO directly and never exposes `storageKey` to public callers.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Local HTTP port |
| `SHARING_DATABASE_URL` | `jdbc:postgresql://localhost:5432/clud` | JDBC URL reachable from the local JVM |
| `SHARING_DATABASE_USERNAME` | `clud` | PostgreSQL username |
| `SHARING_DATABASE_PASSWORD` | `clud` | PostgreSQL password |
| `FILE_SERVICE_URL` | `http://localhost:8082` | Internal File Service address |
| `STORAGE_SERVICE_URL` | `http://localhost:8083` | Internal Storage Service address |
| `SHARING_HTTP_CONNECT_TIMEOUT` | `2s` | File Service connection timeout |
| `SHARING_HTTP_READ_TIMEOUT` | `5s` | File Service response timeout |
| `SHARING_STORAGE_HTTP_CONNECT_TIMEOUT` | `2s` | Storage connection timeout |
| `SHARING_STORAGE_HTTP_READ_TIMEOUT` | `30s` | Storage streaming read timeout |
| `SHARING_PUBLIC_BASE_URL` | `http://localhost:8084/public` | Prefix returned with a newly created token |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |
| `FILE_EVENTS_TOPIC` | `file.events.v1` | File lifecycle input topic |
| `SHARING_EVENTS_TOPIC` | `sharing.events.v1` | Sharing output topic |

Real environment variables override values from `.env`. The local `.env` file
is ignored by Git and must not contain committed credentials.

## Local startup

Start PostgreSQL, Kafka, and File Service, then run Sharing locally:

```bash
cp .env.example .env
./mvnw spring-boot:run
```

Run the integration tests with Docker available:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

## Ownership contract

Management endpoints receive the trusted `X-User-ID` UUID from the
authenticated Gateway. Public endpoints require only the unguessable token.

Sharing calls File Service before creating a link and before attempting a
public download. The file must exist, belong to the stored owner, be active,
and have type `FILE`.

## Endpoints

The Gateway removes `/api/sharing` before forwarding requests.

| Method | Public path | Purpose |
| --- | --- | --- |
| `POST` | `/api/sharing/links` | Create or rotate the active link for a file |
| `GET` | `/api/sharing/links?fileId={fileId}` | Read active link metadata |
| `DELETE` | `/api/sharing/links/{linkId}` | Revoke a link |
| `GET` | `/api/sharing/public/{token}` | Read public file metadata and action URLs |
| `GET` | `/api/sharing/public/{token}/preview` | Stream a safe preview with inline disposition |
| `GET` | `/api/sharing/public/{token}/download` | Stream the file with attachment disposition |

Create a link:

```bash
curl -X POST http://localhost/api/sharing/links \
  -H 'Content-Type: application/json' \
  -H 'X-User-ID: 6fd1d302-303b-40b2-99e1-a2a1c8b89477' \
  -d '{
    "fileId":"ad2a878f-4514-4076-96db-99c989a6f277",
    "expiresAt":"2026-09-30T18:00:00Z"
  }'
```

`expiresAt` is optional and, when present, must be in the future. Creating a
new link revokes the previous link for the same owner and file. The public URL
is returned only on creation because the plaintext token is never stored.

## Persistence and tokens

Flyway owns the `sharing_service` schema inside the shared `clud` database.
Hibernate uses `ddl-auto: validate`. The database stores only the SHA-256 hash
of each 256-bit random token. A partial unique index permits only one
non-revoked link for an `(owner_id, file_id)` pair.

## Events

After the database transaction commits, Sharing publishes `FileShared` to
`SHARING_EVENTS_TOPIC`. The version 1 payload contains `eventId`, `eventType`,
`eventVersion`, `occurredAt`, `shareId`, `fileId`, `ownerId`, and `expiresAt`.

Sharing consumes `FileDeleted` from `FILE_EVENTS_TOPIC` and idempotently revokes
the matching owner's active link. Other file lifecycle events are ignored.

## OpenAPI

When the service runs directly, Swagger UI is available at:

```text
http://localhost:8084/docs
```

The OpenAPI document is available at `/v3/api-docs`.

## Preview policy

Inline preview is limited to PDF, JSON, plain text, CSV, common raster images,
audio, and video. Active document formats such as HTML, SVG, and XML return
`415 PREVIEW_NOT_SUPPORTED`; they can still be downloaded. Preview responses
use `nosniff`, a sandbox Content Security Policy, and `Cache-Control: no-store`.
