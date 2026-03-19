# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Seoul Chonnom App (slcnapp) is a Spring Boot 3.4.0 application providing user authentication, schedule management, trip
management with file uploads, and quiz functionality. The project is currently transitioning from a monolithic
architecture to a Domain-Driven Design (DDD) structure with separated modules.

## Common Development Commands

### Build and Run

```bash
# Build all modules
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# View dependency tree
./gradlew dependencies
```

### Database

- MySQL with JPA/Hibernate using `spring.jpa.hibernate.ddl-auto=update`
- Redis for refresh token storage
- Configuration in `src/main/resources/application.properties` (dev) and `application.yml` (prod with env vars)

## Module Architecture (DDD Structure)

The project uses a **5-module Gradle multi-project** with clear dependency hierarchy:

```
slcn-boot (executable JAR)
  ↓ depends on
slcn-rest (REST API controllers)
  ↓ depends on
slcn-auth (authentication & security)
  ↓ depends on
slcn-aggregate (business logic & persistence)
  ↓ depends on
slcn-spec (domain contracts & DTOs)
```

### Module Responsibilities

**slcn-spec** (Specifications Layer)

- Domain entities: `User`, `Authority`, `Schedule`, `Trip`, `Quiz`
- Base entity classes: `Entity`, `DomainEntity` with UUID and timestamps
- Facade interfaces: `UserFacade`, `ScheduleFacade`, `TripFacade`
- SDOs (Service Data Objects): `*Rdo` (Response), `*Cdo` (Create), `*Udo` (Update)
- Minimal dependencies (Spring context & validation only)

**slcn-aggregate** (Implementation Layer)

- JPO (JPA Objects): `EntityJpo`, `DomainEntityJpo` extending spec entities
- Domain-specific exceptions organized by module:
    - User: `InvalidUserException`, `UserLoginFailCountOverException`, `InvalidAccessTokenException`,
      `InvalidRefreshTokenException`
    - Schedule: `ScheduleNotFoundException`, `InvalidScheduleDateException`, `InvalidScheduleRegisterRequestException`
    - Trip: `TripNotFoundException`
    - Depot (file): `FileExtException`, `FileSizeException`, `FilePathInvalidException`, `FileUploadException`
    - Common: `BadRequestException`, `InternalServerErrorException`, `PayloadTooLargeException`,
      `UnsupportedMediaTypeException`
- Dependencies: JPA, Spring context, MySQL driver

**slcn-auth** (Authentication Layer)

- JWT token creation and validation (`JwtTokenProvider`)
- Refresh token management with Redis
- Spring Security integration
- Login failure tracking and account locking
- Dependencies: Redis, JWT (io.jsonwebtoken:jjwt 0.12.6)

**slcn-rest** (API Layer)

- REST controllers and HTTP endpoints
- Request/response DTOs
- Swagger/OpenAPI documentation (`*ControllerDocs` interfaces)
- Base path configurable via `application.yml`
- Dependencies: Spring Web, Security, Swagger

**slcn-boot** (Bootstrap Layer)

- Spring Boot application entry point (`SlcnappApplication.java`)
- Aggregates all modules into executable JAR
- Spring Boot Gradle plugin applied here

## Key Architectural Patterns

### Service-Repository Pattern

Services handle business logic with `@Transactional`:

- `UserService`: Registration, authentication, token management
- `ScheduleService`: CRUD operations with soft delete
- `TripService`: Trip management and file uploads

Repositories use Spring Data JPA:

- Custom query methods (e.g., `findByUsername`)
- Date-range queries for schedules
- No custom repository implementations currently

### Exception Handling

Centralized in `CommonExceptionHandler` with:

- Domain-specific exception hierarchy
- Standardized response format: `{ success: boolean, message: string, data: object }`
- HTTP status code mapping

### Authentication Flow

1. `POST /user/login` → validate credentials → issue JWT access token
2. Store refresh token in Redis with TTL
3. Return refresh token as HTTP-only cookie
4. `GET /user/token` → validate refresh token from cookie → issue new access token
5. `JwtAuthenticationFilter` intercepts requests → validates token → sets Spring Security context

### Soft Delete Pattern

Schedule entity uses `isVisible` boolean flag:

- `hideSchedule()` sets `isVisible = false`
- Hard delete available via separate endpoint
- See `docs/database-entity-analysis.md` for improvement recommendations (unified `SoftDeletableEntity`)

## Configuration Management

### Development (`application.properties`)

- Hardcoded credentials (MySQL, Redis, JWT secret)
- Swagger UI enabled
- File upload path configured via `upload.path`

### Production (`application.yml`)

- Environment variable injection: `${SLCN_MYSQL_URL}`, `${SLCN_REDIS_URL}`, `${SLCN_JWT_SECRETKEY}`, etc.
- Profile-based configuration
- Swagger disabled by default

### Key Properties

- `cookie.expire.time`: Refresh token TTL (default 14 days)
- `login.fail.limit.count`: Failed login attempts before lockout (default 5)
- `login.limit.clear.time`: Lockout duration in seconds (default 300)

## Database Schema Notes

### ID Strategies

- **User, Authority, Trip, Quiz**: `GenerationType.IDENTITY` (Integer, recommended to migrate to Long)
- **Schedule**: String UUID (performance consideration - see improvement docs)
- **RefreshToken**: Redis hash with User ID as key

### Entity Relationships

- User ↔ Authority: One-to-Many (currently EAGER, should be LAZY)
- Trip ↔ Quiz: One-to-Many with `CascadeType.ALL`
- Schedule: No foreign key relationships (uses String `calendarId`)

### Performance Considerations

- Missing indexes on frequently queried columns (`username`, `calendarId`, `start`/`end`, `date`)
- EAGER loading on `User.authorityList` causes N+1 queries
- UUID String keys in Schedule have indexing overhead
- See `docs/database-entity-analysis.md` for detailed analysis and migration strategy

## Security Architecture

### JWT Implementation

- Access tokens (short-lived, returned in response body)
- Refresh tokens (long-lived, stored in Redis, sent as HTTP-only cookie)
- JJWT library for token creation/validation
- Secret key configured per environment

### Authorization

- Role-based access control: `USER`, `ADMIN`
- User registration restricted to ADMIN role
- Authority entities linked to User via `user_id`

### File Upload

- Local filesystem storage at `upload.path`
- Image MIME type validation
- File serving via `GET /trip/file/{path}`
- Path traversal validation needed (see security docs)

## API Documentation

Swagger UI available at `/api-test` (dev profile only)

### Standardized Response Format

```json
{
  "success": true,
  "message": "Operation message",
  "data": { }
}
```

### Key Endpoints

- `POST /user/login` - Authentication
- `GET /user/token` - Refresh access token
- `POST /user/register` - User registration (ADMIN only)
- `GET /schedule/search?start={date}&end={date}` - Date range query
- `PUT /schedule/remove/{id}` - Soft delete
- `DELETE /schedule/remove/{id}` - Hard delete
- `POST /trip/file` - File upload
- `GET /trip/file/{path}` - File retrieval

## Testing Strategy

Currently minimal test coverage (basic application context test only). When adding tests:

- Use JUnit 5 platform
- Spring Boot Test for integration tests
- Spring Security Test for authentication tests
- Consider adding repository, service, and controller layer tests

## Known Technical Debt

See `docs/database-entity-analysis.md` and `docs/improvement-v2.md` for comprehensive analysis. Key issues:

### Critical

1. Hardcoded credentials in `application.properties`
2. CORS allows all origins (`allowedOrigins("*")`)
3. EAGER loading causing N+1 queries
4. Missing input validation (`@Valid` annotations)

### High Priority

1. No audit fields (createdAt, updatedAt) on most entities
2. Inconsistent ID types (Integer vs Long)
3. Missing database indexes on query columns
4. Insufficient test coverage

### Medium Priority

1. Inconsistent soft delete pattern
2. String-based dates and types (should use LocalDate and enums)
3. Unclear column names (info1, info2, button1, button2 in Trip)
4. Cascade type configurations need review

## Development Workflow Notes

### Transaction Management

- `@Transactional` on service methods
- `@Transactional(readOnly = true)` for queries
- Login failure counting uses `Propagation.REQUIRES_NEW` for isolation

### DTO Patterns

- Request DTOs: `*Request` classes
- Response DTOs: `*Response` classes
- Spec layer SDOs for inter-module communication

### Current Branch Context

- **Main branch**: `main` (stable)
- **Current branch**: `ddd` (DDD refactoring in progress)
- Recent work: Module separation, Auth module extraction, entity reorganization