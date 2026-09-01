# File Service

The File Service owns Clud file and folder metadata. It stores the directory
tree, validates rename and move operations, and manages the trash lifecycle.
File bytes remain the responsibility of the Storage Service; `storageKey` is an
opaque internal reference and is never returned to clients. Upload and download
requests enter through File Service and are proxied to Storage.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | Local HTTP port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |
| `STORAGE_SERVICE_URL` | `http://localhost:8083` | Internal Storage base URL |
| `FILE_STORAGE_HTTP_CONNECT_TIMEOUT` | `2s` | Storage connection timeout |
| `FILE_STORAGE_HTTP_READ_TIMEOUT` | `30s` | Storage upload/download read timeout |

Real environment variables override values from `.env`. The local `.env`
file is ignored by Git and must not contain committed credentials.

## Local startup

Create the local environment file and start the service from this directory:

```bash
cp .env.example .env
./mvnw spring-boot:run
```

The example port matches the port published by Docker Compose. To route a
Dockerized Gateway to this locally running service, set
`FILE_SERVICE_URL=http://host.docker.internal:8082` for the Gateway container.
See the [root README](../README.md#hybrid-development) for the complete mixed
startup flow.

## Ownership contract

Every business endpoint requires the trusted `X-User-ID` UUID supplied by the
authenticated Gateway. Client-provided values are removed by Gateway.

Requests sent through Nginx and the Gateway use `/api/files` as the public
prefix. The Gateway removes that prefix before forwarding the request.

## Endpoints

| Method | Public path | Purpose |
| --- | --- | --- |
| `POST` | `/api/files/folders` | Create a folder |
| `POST` | `/api/files/files` | Upload multipart field `file`; optional `parentId` query parameter |
| `GET` | `/api/files/files/{fileId}/content` | Download file bytes by public file ID |
| `GET` | `/api/files/nodes/{nodeId}` | Read a file or folder |
| `GET` | `/api/files/nodes?parentId={id}` | List folder contents; omit `parentId` for root |
| `PATCH` | `/api/files/nodes/{nodeId}` | Rename a node |
| `POST` | `/api/files/nodes/{nodeId}/move` | Move a node; use `null` parent for root |
| `DELETE` | `/api/files/nodes/{nodeId}` | Move a node tree to trash |
| `GET` | `/api/files/trash` | List top-level trash items |
| `POST` | `/api/files/trash/{nodeId}/restore` | Restore a trash item and its descendants |

List endpoints accept zero-based `page` and `size`; `size` must be between 1
and 100.

## Examples

Create a root folder:

```bash
curl -X POST http://localhost/api/files/folders \
  -H 'Content-Type: application/json' \
  -H 'X-User-ID: 6fd1d302-303b-40b2-99e1-a2a1c8b89477' \
  -d '{"name":"Documents"}'
```

Upload a file into a folder:

```bash
curl -X POST \
  'http://localhost/api/files/files?parentId=ad2a878f-4514-4076-96db-99c989a6f277' \
  -H 'X-User-ID: 6fd1d302-303b-40b2-99e1-a2a1c8b89477' \
  -F 'file=@report.pdf'
```

## Persistence

Flyway owns the `file_service` PostgreSQL schema. Active sibling names are
unique per owner and folder, case-insensitively. Deletion is soft and recursive;
only the selected root is displayed as a trash item. Moving a file to trash does
not delete its Storage object, so restore and later permanent cleanup remain
possible. Optimistic locking protects concurrent metadata updates.

## Events

The service publishes JSON events to `FILE_EVENTS_TOPIC`, which defaults to
`file.events.v1`. Events are sent after the database transaction commits.

- `FileUploaded` is published when complete file metadata is registered.
- `FileDeleted` is published for every file moved to trash, including files in
  a deleted folder tree.

Each payload contains `eventId`, `eventType`, `eventVersion`, `occurredAt`,
`fileId`, `ownerId`, and `storageKey`.

## Testing

Integration tests use PostgreSQL 17 through Testcontainers:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Docker must be available while running the integration test suite.
