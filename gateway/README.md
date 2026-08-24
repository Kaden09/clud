# Gateway

Gateway is the public API entry point for Clud. It routes requests to Identity,
File, Storage, and Sharing and propagates an `X-Request-ID`.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Local HTTP port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | Identity base URL |
| `FILE_SERVICE_URL` | `http://localhost:8082` | File base URL |
| `STORAGE_SERVICE_URL` | `http://localhost:8083` | Storage base URL |
| `SHARING_SERVICE_URL` | `http://localhost:8084` | Sharing base URL |

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

When Gateway runs locally, the service URLs in its `.env` point to the
published host ports of Docker containers. When Gateway runs in Docker, the
root `.env` supplies Docker-network addresses instead.

See the [root README](../README.md#hybrid-development) for complete mixed
Docker and local startup examples.
