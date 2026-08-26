# Identity

Identity is the service boundary for authentication, users, and access identities.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | Local HTTP port |
| `IDENTITY_DATABASE_URL` | `jdbc:postgresql://localhost:5432/clud` | JDBC URL reachable from the local JVM |
| `IDENTITY_DATABASE_USERNAME` | `clud` | PostgreSQL username |
| `IDENTITY_DATABASE_PASSWORD` | `clud` | PostgreSQL password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |
| `IDENTITY_SHOW_SQL` | `false` | Enables Hibernate SQL logging for local diagnostics |
| `JWT_SECRET` | Base64 value | Base64-encoded JWT signing key of at least 32 decoded bytes |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | `900000` | Access-token lifetime in milliseconds |
| `JWT_REFRESH_TOKEN_EXPIRATION_MS` | `604800000` | Refresh-token lifetime in milliseconds |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated trusted browser origins |
| `COOKIE_SECURE` | `false` | Set to `true` when HTTPS is used |
| `COOKIE_SAME_SITE` | `Lax` | Refresh-cookie SameSite policy |
| `COOKIE_PATH` | `/api/auth` | Public path on which the refresh cookie is sent |

Real environment variables override values from `.env`. The local `.env` file is ignored by Git and must not contain committed credentials. Hibernate always uses `validate`; Flyway is the only component allowed to change the schema.

## Local startup

Create the local environment file and start the service from this directory:

```bash
cp .env.example .env
./mvnw spring-boot:run
```

Run its test suite with Docker available:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

The integration test starts PostgreSQL 17 with Testcontainers, applies Flyway migrations, and then validates the mapped entities with Hibernate.

The example port matches the port published for this service by Docker Compose. To route a Dockerized Gateway to this locally running service, set `IDENTITY_SERVICE_URL=http://host.docker.internal:8081` for the Gateway container.

See the [root README](../README.md#hybrid-development) for the complete mixed startup flow.
