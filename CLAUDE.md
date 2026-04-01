# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What is sMockin

sMockin is a service mocking tool for development and QA. It provides REST/HTTP mocking with dynamic rules and JavaScript support, AWS S3 bucket mocking, SMTP email server mocking, HTTP traffic proxying, and ngrok tunnel integration. It has a web UI served at `http://localhost:8000/index.html`.

## Build & Run Commands

```bash
# Build (produces executable JAR)
mvn clean install

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=RestfulMockDAOTest

# Run a single test method
mvn test -Dtest=RestfulMockDAOTest#methodName

# Start the application (sets up ~/.smockin directory, initializes H2 DB, starts on port 8000)
./run.sh

# First-time setup only
./install.sh
```

## Architecture

**Spring Boot 2.7.18** application using **Jetty** (not Tomcat), **H2** embedded database, and **Spring Data JPA**.

### Two main packages under `com.smockin`:

- **`admin`** — Web UI backend: REST controllers, services, JPA entities/DAOs, DTOs, auth interceptor. Controllers are `@Controller` + `@ResponseBody` (not `@RestController`). Auth uses JWT Bearer tokens with role-based access (ADMIN, REGULAR_USER).

- **`mockserver`** — Core mock engines that run independently:
  - `MockedRestServerEngine` — HTTP mocking via Spark Java (separate from the admin Jetty server)
  - `MockedS3ServerEngine` — S3 mocking via s3proxy
  - `MockedMailServerEngine` — SMTP mocking via GreenMail
  - Processing pipeline: request → rule matching (`RuleEngine`) → JavaScript execution (`JavaScriptResponseHandler` using GraalVM Polyglot) → response

### Key patterns:
- Interface + `Impl` suffix for all services (e.g., `RestfulMockService` / `RestfulMockServiceImpl`)
- DAOs extend `JpaRepository` with custom query methods
- All REST endpoints require auth except static resources and `/auth` — controlled via `AuthInterceptor`
- User data stored at `~/.smockin/` (database, logs, config)

### Database:
- H2 file-based DB at `~/.smockin/db/data/smockin_db.mv.db`
- Connection config at `~/.smockin/db/db.properties`
- Hibernate DDL-auto: `update` (schema auto-managed)

## Testing

- **JUnit 4** with `SpringRunner` for integration tests and `MockitoJUnitRunner` for unit tests
- **Mockito 4.11** for mocking
- Tests are in `src/test/java/com/smockin/`

## Key Dependencies

- **Spark Java 2.9.4** — lightweight HTTP server for the mock engine (separate from Spring's Jetty)
- **GraalVM Polyglot 25.0.2** — JavaScript execution in dynamic mock responses (recently migrated from Nashorn)
- **s3proxy 2.6.0** — S3 API emulation
- **GreenMail 1.6.15** — SMTP server emulation
- **java-ngrok 2.1.0** — public tunnel support
- **Lombok** — used throughout for `@Getter`, `@Setter`, etc.
- **OpenAPI Generator 7.13.0** and **RAML Parser 2** — API spec import
