# File Service

The File Service owns Clud file and folder metadata. It stores the directory
tree, validates rename and move operations, and manages the trash lifecycle.
File bytes remain the responsibility of the Storage Service; `storageKey` is an
opaque reference to an object managed by that service.

## Ownership contract

Every business endpoint requires `X-User-ID` with a UUID value. Until Identity
authentication is implemented, callers provide it manually. In the completed
architecture, only the authenticated Gateway may create this header.

Requests sent through Nginx and the Gateway use `/api/files` as the public
prefix. The Gateway removes that prefix before forwarding the request.

## Endpoints

| Method | Public path | Purpose |
| --- | --- | --- |
| `POST` | `/api/files/folders` | Create a folder |
| `POST` | `/api/files/files` | Register uploaded file metadata |
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

Register metadata after a Storage Service upload:

```bash
curl -X POST http://localhost/api/files/files \
  -H 'Content-Type: application/json' \
  -H 'X-User-ID: 6fd1d302-303b-40b2-99e1-a2a1c8b89477' \
  -d '{
    "name":"report.pdf",
    "parentId":"ad2a878f-4514-4076-96db-99c989a6f277",
    "storageKey":"objects/2026/report.pdf",
    "contentType":"application/pdf",
    "sizeBytes":48192
  }'
```

## Persistence

Flyway owns the `file_service` PostgreSQL schema. Active sibling names are
unique per owner and folder, case-insensitively. Deletion is soft and recursive;
only the selected root is displayed as a trash item. Optimistic locking protects
concurrent metadata updates.

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
