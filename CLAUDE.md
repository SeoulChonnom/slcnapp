# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application called "slcnapp" (Seoul Chonnom App) that provides:

- User authentication and authorization with JWT tokens
- Schedule management system
- Trip management with file upload capabilities
- Quiz functionality related to trips

## Common Development Commands

### Build and Run

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test
```

### Database Setup

- The application uses MySQL database with JPA/Hibernate
- Database configuration is in `src/main/resources/application.properties`
- DDL auto-update is enabled (`spring.jpa.hibernate.ddl-auto=update`)

## Architecture

### Core Modules

1. **User Module** (`com.seoulchonnom.slcnapp.user`)
    - JWT-based authentication
    - User registration (requires ADMIN authority)
    - Token refresh mechanism using cookies
    - Role-based access control (USER, ADMIN)

2. **Schedule Module** (`com.seoulchonnom.slcnapp.schedule`)
    - Schedule CRUD operations
    - Date-based filtering
    - Soft delete functionality (hide/show)
    - Hard delete for permanent removal

3. **Trip Module** (`com.seoulchonnom.slcnapp.trip`)
    - Trip information management
    - File upload for images (logo, maps)
    - Quiz functionality
    - File serving endpoint

4. **Common Module** (`com.seoulchonnom.slcnapp.common`)
    - Security configuration
    - JWT authentication filter
    - Exception handling
    - Response standardization
    - Swagger/OpenAPI documentation

### Key Technologies

- **Spring Boot 3.4.0** with Java 17
- **Spring Security** with JWT authentication
- **Spring Data JPA** with MySQL
- **Spring Data Redis** for caching
- **Swagger/OpenAPI** for API documentation
- **Lombok** for boilerplate reduction

### Security Configuration

- JWT-based stateless authentication
- Role-based authorization (USER, ADMIN)
- CORS enabled for preflight requests
- Swagger UI accessible without authentication
- User registration restricted to ADMIN role

### File Upload

- Files are stored locally at the path specified in `upload.path` property
- Supports image files with MIME type validation
- File serving through `/trip/file` endpoint

### Database Schema

- MySQL database with Hibernate auto-update
- Entity relationships managed through JPA annotations
- Audit fields and soft delete patterns used

## Development Notes

### Testing

- Uses JUnit 5 platform
- Spring Boot Test and Spring Security Test included
- Run tests with `./gradlew test`

### API Documentation

- Swagger UI available at `/api-test` endpoint
- Controller documentation interfaces follow naming pattern `*ControllerDocs`

### Configuration

- Main configuration in `application.properties`
- Redis and MySQL connection settings
- JWT secret key and cookie expiration settings
- File upload path configuration

### Authentication Flow

1. User login via `/user/login` endpoint
2. JWT access token returned in response
3. Refresh token stored in HTTP-only cookie
4. Token refresh via `/user/token` endpoint using cookie

### Error Handling

- Centralized exception handling in `CommonExceptionHandler`
- Custom exceptions for different modules
- Standardized error response format