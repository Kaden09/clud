# Gateway

Gateway is the public API entry point for Clud. It authenticates bearer access
tokens, routes requests to Identity, File, and Sharing, propagates an
`X-Request-ID`, and supplies downstream services with a trusted `X-User-ID`.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Local HTTP port |
| `JWT_SECRET` | Base64 value | Shared HS256 verification key used by Identity and Gateway |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated trusted browser origins |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address reachable from the local JVM |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | Identity base URL |
| `FILE_SERVICE_URL` | `http://localhost:8082` | File base URL |
| `SHARING_SERVICE_URL` | `http://localhost:8084` | Sharing base URL |

Real environment variables override values from `.env`. The local `.env` file is ignored by Git and must not contain committed credentials.

## Authentication

Gateway validates HS256 access tokens issued by Identity. The token subject must
be a UUID and the `type` claim must be `access`. Client-provided
`X-User-ID` values are always removed; after successful authentication,
Gateway forwards the user ID from the token subject under that header.

The following paths are public:

- `/api/identity/auth/**`
- `/api/sharing/public/**`
- `/actuator/health/**`

All other `/api/**` requests require `Authorization: Bearer <access-token>`.
Refresh tokens and tokens with invalid signatures, subjects, or expiration are
rejected with a structured `401` response.

Storage has no public Gateway route. Clients upload and download binary content
through File Service endpoints, and internal File metadata routes are denied.

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

A locally running Gateway is tested directly through `http://localhost:8080`.
Nginx is bypassed because its current upstream points to the Docker `gateway`
service.

See the [root README](../README.md#hybrid-development) for complete mixed
Docker and local startup examples.
