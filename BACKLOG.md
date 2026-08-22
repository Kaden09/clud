# Clud — Backlog

## About the Project

Clud is an educational cloud file storage service similar to Google Drive.

The main goal of the project is not to replicate the entire functionality of Google Drive, but to gain practical experience building a microservices-based system with Java and Spring Boot.

Main focus:

- microservices architecture;
- Spring Boot;
- service-to-service communication;
- PostgreSQL;
- object storage;
- message broker;
- API Gateway;
- authentication and authorization;
- Docker;
- testing;
- observability;
- CI/CD.

---

## Core Functionality

A user should be able to:

- register and log in;
- create folders;
- upload and download files;
- browse folder contents;
- rename and move files;
- delete and restore files;
- create a public link to a file.

This is sufficient for version 1.0.

---

## Architecture

Target structure:

### API Gateway

The single entry point to the system.

Responsibilities:

- routing;
- authentication;
- rate limiting;
- request/correlation ID.

### Identity Service

Responsible for:

- registration;
- login;
- refresh tokens;
- logout;
- users.

### File Service

Responsible for:

- files and folders;
- metadata;
- directory structure;
- rename;
- move;
- trash.

### Storage Service

Responsible for file binary data:

- upload;
- download;
- delete;
- MinIO / S3.

### Sharing Service

Responsible for:

- public links;
- expiration;
- revocation.

Do not create additional microservices without a specific reason.

---

## Technologies

### Backend

- Java 21+
- Spring Boot
- Spring Security
- Spring Data JPA

### Infrastructure

- PostgreSQL
- Redis
- MinIO
- RabbitMQ or Kafka
- Docker
- Docker Compose
- GitHub Actions

### API

- REST
- OpenAPI

### Testing

- JUnit
- Testcontainers
- integration tests
- E2E smoke tests

### Observability

- Spring Boot Actuator
- Micrometer
- Prometheus
- OpenTelemetry

---

## Microservices Practice

The project should provide practical experience with:

- independent services;
- separate data ownership per service;
- HTTP communication;
- asynchronous events;
- retries;
- timeouts;
- idempotency;
- eventual consistency;
- health checks;
- centralized logging;
- metrics;
- distributed tracing.

Minimum set of events:

- `UserRegistered`
- `FileUploaded`
- `FileDeleted`
- `FileShared`

---

## Phases

### Phase 1 — Foundation

- services;
- PostgreSQL;
- MinIO;
- Docker Compose;
- health checks.

### Phase 2 — Core

- authentication;
- folders;
- upload;
- download;
- rename;
- move;
- delete;
- restore.

### Phase 3 — Microservices

- API Gateway;
- service-to-service communication;
- message broker;
- events;
- retries;
- timeouts.

### Phase 4 — Sharing

- public links;
- expiration;
- revoke.

### Phase 5 — Production Practices

- integration tests;
- E2E tests;
- logs;
- metrics;
- tracing;
- CI.

---

## Definition of Done — Clud 1.0

Clud 1.0 is considered complete when:

- core file operations work;
- folders and Trash are implemented;
- authentication works;
- public sharing works;
- multiple independent microservices are used;
- an API Gateway is implemented;
- object storage is used;
- a message broker is used;
- integration and E2E tests are implemented;
- logging, metrics, and tracing are available;
- the entire system can be started with Docker Compose;
- CI successfully builds and tests the project.

Once these requirements are met, version 1.0 is considered complete.

---

## Out of Scope for 1.0

Do not implement the following unless there is a specific educational goal:

- Google Docs / Sheets;
- collaborative editing;
- comments;
- complex permissions;
- desktop/mobile applications;
- billing;
- AI;
- OCR;
- full-text search;
- custom file storage implementation;
- Kubernetes;
- multi-region deployment;
- dozens of additional microservices.

---

## Main Rule

> **“Clud is a project for learning microservices, not for endlessly copying Google Drive.”**

A new feature should only be added when it provides useful engineering experience.

Priority:

**Architecture
→ Correctness
→ Testing
→ Observability
→ Deployment
→ Features**
