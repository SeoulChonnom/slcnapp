# Repository Guidelines

## Project Structure & Module Organization
`slcnapp` is a Gradle multi-module Spring Boot backend. Keep dependencies flowing downward: `slcn-boot -> slcn-rest -> slcn-auth -> slcn-aggregate -> slcn-spec`.

- `slcn-boot`: application entrypoint, Spring configuration, `application.yml`
- `slcn-rest`: HTTP resources and exception handlers
- `slcn-auth`: JWT, security filters, auth flows, Redis-backed session helpers
- `slcn-aggregate`: business logic, stores, JPA/Mongo persistence models
- `slcn-spec`: shared contracts, domain models, facade DTOs, mappers
- `docs/`: architecture and module rules; 프로젝트 전체 아키텍처는 `docs/architecture.md`를 먼저 참고하고, 코드 배치와 레이어 규칙은 `docs/module.md`를 기준으로 판단

Tests live beside each module under `src/test/java`. SQL and architecture notes live under `docs/`.
If documentation and implementation differ, verify the current code before changing behavior.

## Build, Test, and Development Commands
- `./gradlew test`: run the full test suite for all modules
- `./gradlew build`: compile, test, and package the project
- `./gradlew :slcn-rest:test`: run a single module’s tests
- `./gradlew :slcn-boot:bootRun --args='--spring.profiles.active=dev'`: start the API locally with the dev profile

Use the Gradle wrapper instead of a system Gradle install.

## Coding Style & Naming Conventions
Java 17 is required. Follow the existing Java style in this repository: tabs for indentation, one top-level class per file, and package names rooted at `com.seoulchonnom`.

Preserve the module roles above. Common naming patterns are:
- REST adapters: `*Resource`
- Business services: `*Logic`
- Orchestration: `*Flow`, `*QueryFlow`, `*AdminFlow`
- Persistence access: `*Store`, `*Repository`, `*Jpo`, `*Doc`
- Mappers: `*Mapper`
- DTOs: `*Cdo`, `*Udo`, `*Rdo`, `*Sdo`

Lombok and MapStruct are already in use; prefer matching existing patterns over introducing new frameworks. For multi-step orchestration, keep `Resource` thin and delegate to `Flow` instead of embedding coordination logic in controllers. No formatter or lint task is configured, so keep edits consistent with surrounding code.

## Testing Guidelines
Tests use JUnit 5 with Spring Boot Test, Mockito, and AssertJ. Name test classes `*Test` and use method names like `action_shouldExpectedResult`.

Add focused tests when changing security, persistence mappings, shared DTOs, or flow orchestration. Prefer module-scoped runs during development, then finish with `./gradlew test`. Do not assume a `slcn-spec` contract is live API behavior by itself; confirm the matching `Resource` adapter exists in `slcn-rest`.

## Commit & Pull Request Guidelines
Recent history follows Conventional Commits, usually with concise Korean summaries, for example `feat: trip 등록 검증 기능 추가` or `fix: UserLogin 오류 수정`. Keep the type prefix (`feat`, `fix`, `chore`) and describe the user-visible change.

Pull requests should explain the affected module(s), summarize behavior changes, list test coverage, and include request/response examples when an API contract changes.

## Security & Configuration Tips
Runtime configuration comes from environment variables in `slcn-boot/src/main/resources/application.yml`, including `SLCN_POSTGRESQL_*`, `SLCN_REDIS_*`, `SLCN_MONGODB_URL`, `SLCN_JWT_*`, and `SLCN_UPLOAD_PATH`. Do not commit secrets. Business data source of truth is PostgreSQL; Redis is used for auth/session support, not primary domain storage. Be careful with JPA model changes because `ddl-auto=update` is enabled. JWT resolution supports `X-AUTH-TOKEN` and `Authorization: Bearer ...`, so changes to token handling should be reviewed across `slcn-boot`, `slcn-auth`, and related tests together.
