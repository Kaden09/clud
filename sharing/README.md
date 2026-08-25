# Sharing

Sharing is the service boundary for public links and resource sharing.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8084` | Local HTTP port |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |

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

The example port matches the port published for this service by Docker
Compose. To route a Dockerized Gateway to this locally running service, set
`SHARING_SERVICE_URL=http://host.docker.internal:8084` for the Gateway
container.

See the [root README](../README.md#hybrid-development) for the complete mixed
startup flow.
