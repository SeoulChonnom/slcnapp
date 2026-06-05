# Architecture Guide

> This document is a short architectural brief for AI coding agents and developers.
> `docs/module.md` is the detailed source for module responsibilities, package rules, DTO placement, and change patterns.
> If this document and code differ, verify the implementation before changing behavior.

---

## 1. Purpose

### What this project is

- A Spring Boot backend organized as a modular monolith.
- The codebase is split into five Gradle modules: `slcn-spec`, `slcn-aggregate`, `slcn-auth`, `slcn-rest`, and `slcn-boot`.
- Main business areas currently present in code are user registration/authentication, trip content, schedule management, and file handling.

### How to use this document

- Use this file to understand the system quickly.
- Use `docs/module.md` when deciding where new code should live or how a module/package should be structured.
- Treat `slcn-spec` as the shared contract boundary with the widest blast radius.

---

## 2. System Summary

### High-level summary

- Architecture style: modular monolith
- Runtime: Java 17 + Spring Boot 3.x
- Primary persistence: PostgreSQL via Spring Data JPA / Hibernate
- Optional infrastructure already present: Redis
- Optional future extensions reflected in `module.md`: QueryDSL, MongoDB

### Module overview

| Module | Role |
| --- | --- |
| `slcn-spec` | Shared contracts, shared domain models, common exception contract |
| `slcn-aggregate` | Business logic, persistence, store layer, domain-oriented flow |
| `slcn-auth` | Authentication/authorization helpers, JWT, security handlers, auth flow |
| `slcn-rest` | HTTP adapters and common web exception handling |
| `slcn-boot` | Application entrypoint and top-level Spring configuration |

### Dependency direction

```text
slcn-boot -> slcn-rest -> slcn-auth -> slcn-aggregate -> slcn-spec
```

`slcn-rest` and `slcn-auth` also depend directly on `slcn-spec` for shared contracts.

For detailed dependency and package rules, see `docs/module.md`.

---

## 3. Runtime Flow

### Standard request flow

```text
HTTP Request
    ↓
slcn-rest Resource (implements Facade)
    ↓
Flow (aggregate or auth, when orchestration is needed)
    ↓
slcn-aggregate Logic
    ↓
slcn-aggregate Store
    ↓
Repository / Query / Doc model
    ↓
PostgreSQL or other backing store
```

### Authentication-oriented flow

```text
HTTP Request
    ↓
SecurityFilterChain in slcn-boot
    ↓
JwtAuthenticationFilter in slcn-auth
    ↓
JwtTokenProvider validation
    ↓
UserAuthDetailLogic / UserAuthStore
    ↓
aggregate.user.store.UserStore
    ↓
SecurityContextHolder
```

### User login and token reissue

- `UserResource` delegates orchestration to `slcn-auth.flow.UserFlow`.
- `UserFlow` combines `UserAuthLogic`, `UserLogic`, and `spec.user.mapper.UserMapper`.
- This is the current example of a flow that belongs in `auth` instead of `aggregate`, because it depends on the `auth -> aggregate` direction.

---

## 4. Persistence Summary

- Shared domain entities live in `slcn-spec` and stay free of JPA persistence annotations.
- JPA persistence models live in `slcn-aggregate/.../store/jpo`.
- MongoDB persistence models live in `slcn-aggregate/.../store/doc` and follow the `{Domain}Doc` naming rule.
- Store classes encapsulate repository access and mapping.
- JPO ↔ domain mapping lives under `slcn-aggregate/.../store/mapper`.
- Public domain-to-DTO mapping belongs in `slcn-spec/.../mapper`.

### Current storage facts

- Source of truth for business data: PostgreSQL
- ID generation source: `id_sequence` table via aggregate generator logic
- Redis is configured but not a confirmed business-system source of truth
- Some `doc/` structures exist for future document-store usage, but they are not a primary architectural concern yet

### Important persistence caution

- `ddl-auto=update` means JPA model changes are high risk.
- When changing write paths or mappings, verify JPOs, store mappers, repositories, and transaction boundaries together.

---

## 5. Security Summary

### Current model

- Security chain is assembled in `slcn-boot`.
- JWT parsing, token creation, auth filter, access-denied handling, and authentication entrypoint are owned by `slcn-auth`.
- Custom header `X-AUTH-TOKEN` is supported, with `Authorization: Bearer ...` as fallback in token resolution.
- URL-level authorization remains centralized in `SecurityConfiguration`.

### Key boundaries

- `slcn-auth` owns authentication and authorization support components.
- `slcn-boot` wires those components into the application.
- `slcn-rest` should not own security handlers.

### Current cautions

- Security rules still contain an inline TODO and should be treated as evolving.
- Authentication failures currently return the configured error response through auth-owned handlers.

---

## 6. Error Handling Summary

- Shared error contract lives in `slcn-spec.common.exception`.
- `BusinessException` and `ErrorCode` are the shared exception boundary.
- Aggregate/domain exceptions build on that shared contract.
- `slcn-rest.common.handler.CommonExceptionHandler` maps `BusinessException` to HTTP responses.
- Security-specific failures are handled by auth-owned security handlers, not by controller advice.

For detailed exception-placement rules, see `docs/module.md`.

---

## 7. Testing and Risk Notes

### Current testing state

- `./gradlew test` passes.
- There are now focused tests in `slcn-auth` and `slcn-rest` for security/error-handler behavior.
- Coverage is still light relative to the architectural surface area.

### High-risk areas

- `slcn-spec` shared domain, facade, and exception-contract types
- `SecurityConfiguration` and `JwtTokenProvider`
- JPA JPO mappings and store mappers
- ID generation and persistence model changes

### Practical guidance

- Add focused tests when changing security, persistence mappings, shared DTOs, or flow orchestration.
- Do not infer live API behavior from `slcn-spec` contracts alone; verify that a REST adapter exists.
- Use `docs/module.md` as the implementation-placement reference before introducing new packages or moving responsibilities.
