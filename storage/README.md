# Storage

Storage is the internal service boundary for binary object operations. It is
called by File Service and has no public Gateway route. MinIO object names are
opaque UUIDs; original file names live only in File metadata.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | Local HTTP port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |
| `MINIO_URL` | `http://localhost:9000` | MinIO API URL |
| `MINIO_ROOT_USER` | `minio` | MinIO access key |
| `MINIO_ROOT_PASSWORD` | `minioadmin` | MinIO secret key |

Real environment variables override values from `.env`. The local `.env` file is ignored by Git and must not contain committed credentials.

## Local startup

Create the local file and start the service from this directory:

```bash
cp .env.example .env
./mvnw spring-boot:run
```

Run its test suite with:

```bash
./mvnw test
```

The internal contract is `POST /objects`, `GET /objects/{storageKey}`, and
`DELETE /objects/{storageKey}`. The delete operation is reserved for upload
compensation and future permanent cleanup; normal trash operations do not call
it.

The example port matches the port published for this service by Docker
Compose. To use this local service from a Dockerized File Service, set
`STORAGE_SERVICE_URL=http://host.docker.internal:8083` for the File container.

See the [root README](../README.md#hybrid-development) for the complete mixed
startup flow.
