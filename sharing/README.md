# Sharing Service

The Sharing Service owns public links for Clud files. It creates one active
link per owner and file, supports optional expiration and revocation, and
publishes the versioned `FileShared` event.

Public links currently validate completely but return `501 Not Implemented`
when the download reaches the Storage boundary. The placeholder is intentional:
Storage Service does not yet expose the stable internal streaming contract that
Sharing needs. Sharing never accesses MinIO directly and never exposes a
`storageKey` to public callers.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Local HTTP port |
| `SHARING_DATABASE_URL` | `jdbc:postgresql://localhost:5432/clud` | JDBC URL reachable from the local JVM |
| `SHARING_DATABASE_USERNAME` | `clud` | PostgreSQL username |
| `SHARING_DATABASE_PASSWORD` | `clud` | PostgreSQL password |
| `FILE_SERVICE_URL` | `http://localhost:8082` | Internal File Service address |
| `STORAGE_SERVICE_URL` | `http://localhost:8083` | Reserved internal Storage Service address |
| `SHARING_HTTP_CONNECT_TIMEOUT` | `2s` | File Service connection timeout |
| `SHARING_HTTP_READ_TIMEOUT` | `5s` | File Service response timeout |
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

Management endpoints require `X-User-ID` with a UUID value. This matches the
temporary File Service contract. In the completed architecture, only the
authenticated Gateway may create this header.

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
| `GET` | `/api/sharing/public/{token}` | Validate a public link and eventually stream its file |

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

## Pending Storage integration

`PendingStorageDownloadGateway` is the only intentional download stub. Replace
it after Storage defines an internal endpoint that accepts an opaque
`storageKey` and streams bytes with content type, length, and disposition.
Until then, a valid `/public/{token}` request returns the structured error code
`STORAGE_INTEGRATION_PENDING` with HTTP status `501`.
