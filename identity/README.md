# Identity Service

Identity owns users, authentication, and refresh sessions. The service has one user type and no role or admin model.

## API

Gateway removes the `/api/identity` prefix before forwarding requests.

| Method | Public path | Purpose |
| --- | --- | --- |
| `POST` | `/api/identity/auth/register` | Register a user |
| `POST` | `/api/identity/auth/login` | Issue an access token and refresh cookie |
| `POST` | `/api/identity/auth/refresh` | Rotate the refresh session and issue a new access token |
| `POST` | `/api/identity/auth/logout` | Revoke the refresh session and clear its cookie |
| `GET` | `/api/identity/user/me` | Return the authenticated user |

Access tokens are bearer JWTs. Refresh tokens are HttpOnly cookies whose SHA-256 hashes are stored in PostgreSQL. Reusing a rotated token revokes all active sessions for that user.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | Local HTTP port |
| `IDENTITY_DATABASE_URL` | `jdbc:postgresql://localhost:5432/clud` | JDBC URL |
| `IDENTITY_DATABASE_USERNAME` | `clud` | PostgreSQL username |
| `IDENTITY_DATABASE_PASSWORD` | `clud` | PostgreSQL password |
| `IDENTITY_SHOW_SQL` | `false` | Hibernate SQL logging |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address |
| `IDENTITY_EVENTS_TOPIC` | `identity.events.v1` | User lifecycle event topic |
| `JWT_SECRET` | Base64 value | Signing key with at least 32 decoded bytes |
| `JWT_ISSUER` | `clud-identity` | Issuer written to every token |
| `JWT_AUDIENCE` | `clud-api` | Audience written to and required on every token |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | `900000` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_EXPIRATION_MS` | `604800000` | Refresh-token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Trusted browser origins |
| `COOKIE_SECURE` | `false` | Enable for HTTPS |
| `COOKIE_PATH` | `/api/identity/auth` | Public browser cookie path |

Refresh cookies are always `HttpOnly` and `SameSite=Lax`; CSRF token handling is intentionally out of scope for the pet-project MVP. Real environment variables override `.env`. Flyway owns the `identity_service` schema; Hibernate only validates it.

## Local startup

Start PostgreSQL and Kafka, then run:

```bash
cp .env.example .env
JWT_SECRET=$(openssl rand -base64 32)
# Set JWT_SECRET in .env to the generated value.
./mvnw spring-boot:run
```

Run the integration suite with Docker available:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Swagger UI is available directly from Identity at `http://localhost:8081/swagger-ui.html` and through Gateway at `http://localhost:8080/api/identity/swagger-ui.html`. The OpenAPI document is available at `/v3/api-docs` and through Gateway at `/api/identity/v3/api-docs`.

To use a locally running Identity with Dockerized Gateway, set `IDENTITY_SERVICE_URL=http://host.docker.internal:8081`. The refresh cookie keeps the public `/api/identity/auth` path in both modes.

## Events

After the registration transaction commits, Identity publishes `UserRegistered` version 1. The payload contains `eventId`, `eventType`, `eventVersion`, `occurredAt`, `userId`, and `email`. Passwords and tokens are never included.
