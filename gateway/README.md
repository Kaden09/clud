# Gateway

Gateway is the public API entry point for Clud. It authenticates bearer access
tokens, routes requests to Identity, Drive, and Sharing, propagates an
`X-Request-ID`, and supplies downstream services with a trusted `X-User-ID`.

## Environment variables

| Variable | Example | Description |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Local HTTP port |
| `JWT_SECRET` | Base64 value | Shared HS256 verification key used by Identity and Gateway |
| `JWT_ISSUER` | `clud-identity` | Required token issuer |
| `JWT_AUDIENCE` | `clud-api` | Required token audience |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated trusted browser origins |
| `REDIS_HOST` | `localhost` | Redis host used for shared rate-limit counters |
| `REDIS_PORT` | `6379` | Redis port |
| `GATEWAY_HTTP_READ_TIMEOUT` | `30s` | Maximum wait between upstream response reads |
| `GATEWAY_TRUSTED_PROXIES` | Loopback and private networks | Regex of reverse-proxy addresses trusted for forwarded headers |
| `IDENTITY_SERVICE_URL` | `http://localhost:8081` | Identity base URL |
| `DRIVE_SERVICE_URL` | `http://localhost:8082` | Drive base URL |
| `SHARING_SERVICE_URL` | `http://localhost:8084` | Sharing base URL |

Real environment variables override values from `.env`. The local `.env` file is ignored by Git and must not contain committed credentials.

## Authentication

Gateway accepts only HS256 access tokens issued by Identity for the configured
audience. The token subject must be a UUID and the `type` claim must be `access`.
Signature, expiration, not-before time, issuer, and audience are validated. Client-provided
`X-User-ID` values are always removed; after successful authentication,
Gateway forwards the user ID from the token subject under that header.

The following paths are public:

- `/api/identity/auth/**`
- `/api/identity/v3/api-docs/**`
- `/api/identity/swagger-ui.html` and `/api/identity/swagger-ui/**`
- `/api/sharing/public/**`
- `/actuator/health/**`

All other `/api/**` requests require `Authorization: Bearer <access-token>`.
Refresh tokens and tokens with invalid signatures, subjects, or expiration are
rejected with a structured `401` response.

Prometheus scrapes `/actuator/prometheus` directly over the internal Docker
network. Nginx exposes `/actuator/health/**` but returns `404` for every other
`/actuator` path. Gateway also denies actuator endpoints other than health and
Prometheus, including for authenticated API users.

Storage has no public Gateway route. Clients upload and download binary content
through Drive Service endpoints, and internal Drive metadata routes are denied.

## Rate limiting

Gateway keeps one-minute fixed-window counters in Redis. Authenticated requests
are grouped by token subject; anonymous requests are grouped by the client IP
received from the trusted reverse proxy. The default limit is 60 requests per
minute, with separate limits of 10 for `/api/identity/auth/**` and 30 for
`/api/sharing/public/**`.

Successful limited requests include `X-RateLimit-Limit`,
`X-RateLimit-Remaining`, and `X-RateLimit-Reset`. Rejected requests additionally
include `Retry-After` and return `429`. The current `fail-open` setting preserves
API availability if Redis is unavailable. In production, Gateway must only be
exposed through the configured trusted reverse proxy because client IP limiting
relies on its rewritten `X-Forwarded-For` header.

## API documentation

Gateway does not generate its own OpenAPI document. It proxies the public
service specifications and the Identity Swagger UI used during development:

- `/api/identity/v3/api-docs`
- `/api/identity/swagger-ui.html`
- `/api/files/v3/api-docs`
- `/api/sharing/v3/api-docs`

These routes use the regular service route. Gateway derives the trusted
`X-Forwarded-Prefix: /api/identity` after `StripPrefix=2`, so Swagger redirects
and configuration retain the public Gateway path.

Storage documentation is intentionally not proxied because Storage has no
public Gateway route.

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
