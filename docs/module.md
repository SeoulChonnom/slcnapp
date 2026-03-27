# 멀티 모듈 아키텍처 가이드

> **이 문서의 목적**
> 멀티 모듈 Gradle 프로젝트에서 각 모듈과 패키지가 담당하는 역할, 의존 규칙, 코드 배치 기준을 정의합니다.
> 이 문서는 현재 `slcnapp`의 구조를 기준으로 작성하되, 추후 확장할 표준 구조를 함께 설명합니다.

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [모듈 구성 및 의존 관계](#2-모듈-구성-및-의존-관계)
3. [{service}-spec — 도메인 계약 레이어](#3-service-spec--도메인-계약-레이어)
4. [{service}-aggregate — 비즈니스 로직 레이어](#4-service-aggregate--비즈니스-로직-레이어)
5. [{service}-auth — 인증 인가 레이어](#5-service-auth--인증-인가-레이어)
6. [{service}-rest — 프레젠테이션 레이어](#6-service-rest--프레젠테이션-레이어)
7. [{service}-boot — 애플리케이션 부트스트랩](#7-service-boot--애플리케이션-부트스트랩)
8. [공통 아키텍처 패턴](#8-공통-아키텍처-패턴)
9. [요청 흐름](#9-요청-흐름)
10. [DTO 네이밍 컨벤션](#10-dto-네이밍-컨벤션)

---

## 1. 아키텍처 개요

이 아키텍처는 하나의 서비스를 최대 5개의 Gradle 모듈로 계층화합니다.
각 모듈은 단일 책임을 가지며, 의존 방향은 항상 상위 → 하위로만 흐릅니다.

- 기술 스택: Java 17+, Spring Boot 3.x, Hibernate/JPA
- 선택 기술: QueryDSL, MongoDB, Redis
- 빌드 시스템: Gradle 멀티 모듈

> **모듈 네이밍 규칙**
> 모든 모듈 이름은 `{service-name}-{layer}` 형태를 따릅니다.
> 예) `learning-spec`, `learning-aggregate`, `learning-auth`, `learning-rest`, `learning-boot`

### 모듈 구성 요약

| 모듈 | 역할 |
| --- | --- |
| `{service}-spec` | 도메인 계약, 공개 DTO, 공통 예외 계약 |
| `{service}-aggregate` | 도메인 로직, 영속성, 도메인 Flow, Store |
| `{service}-auth` | 인증/인가, JWT, Security 보조 컴포넌트 |
| `{service}-rest` | REST 엔드포인트, HTTP 요청/응답 처리 |
| `{service}-boot` | 애플리케이션 진입점, 최상위 통합 설정 |

---

## 2. 모듈 구성 및 의존 관계

### 의존 방향

```text
{service}-boot → {service}-rest → {service}-auth → {service}-aggregate → {service}-spec
```

### Gradle 의존 타입 설명

| 타입 | 설명 |
| --- | --- |
| `api` | 상위 모듈로 전이되는 공개 의존성 |
| `implementation` | 해당 모듈 내부에서만 사용하는 의존성 |

### 현재 프로젝트 기준 의존 규칙

현재 `slcnapp`는 아래 구조를 기본으로 사용합니다.

| 모듈 | 의존 대상 |
| --- | --- |
| `{service}-spec` | 없음 |
| `{service}-aggregate` | `{service}-spec` |
| `{service}-auth` | `{service}-spec`, `{service}-aggregate` |
| `{service}-rest` | `{service}-spec`, `{service}-aggregate`, `{service}-auth` |
| `{service}-boot` | `{service}-rest`, `{service}-auth` |

### 의존성 설계 원칙

- `aggregate`는 `spec`을 기본적으로 `implementation`으로 참조합니다.
- 따라서 `rest`, `auth`에서 `spec` 타입을 사용할 경우 `spec` 의존성을 직접 선언합니다.
- `api`는 상위 모듈로 노출해야 하는 명확한 이유가 있을 때만 사용합니다.
- `boot`는 모든 보안/비즈니스 구현을 소유하지 않고, 필요한 빈을 조립하는 최상위 모듈로 유지합니다.

---

## 3. {service}-spec — 도메인 계약 레이어

**역할**: 서비스 외부와 내부에서 공통으로 사용하는 공개 계약을 정의하는 레이어입니다.

**베이스 패키지**: `{org}.{service}.spec`

### 현재 기본 구조

```text
{org}/{service}/spec/
├── common/
│   ├── entity/          # 공통 도메인 엔티티 베이스 클래스
│   │   └── vo/          # 값 객체
│   ├── exception/       # 공통 예외 계약
│   ├── generator/       # aggregate가 구현하는 공통 인터페이스
│   └── response/        # 공통 응답 객체
├── {domain}/
│   ├── constant/        # 도메인 공통 상수
│   ├── entity/          # 순수 도메인 모델
│   ├── facade/          # REST 계약 인터페이스
│   │   └── sdo/         # 공개 DTO
│   └── mapper/          # Entity ↔ DTO 변환 매퍼
```

### 패키지 상세

#### `common/entity/`

공통 엔티티 베이스 클래스를 둡니다.
`spec/{domain}/entity`의 도메인 모델은 이 베이스 클래스를 상속할 수 있습니다.

#### `common/exception/`

서비스 전역에서 공유하는 예외 계약을 둡니다.

- `BusinessException.java`
- `ErrorCode.java`

HTTP 응답 직렬화용 `ErrorResponse`는 `common/response/`에 둡니다.

#### `spec/{domain}/entity/`

도메인 비즈니스 모델을 정의합니다.

- 이 계층의 엔티티는 **순수 도메인 모델**입니다.
- JPA 애너테이션(`@Entity`, `@Embeddable`)을 두지 않습니다.
- 영속성 매핑은 `aggregate/{domain}/store/jpo/`에서 담당합니다.

#### `spec/{domain}/facade/`

REST API의 공개 계약 인터페이스입니다.

- `{service}-rest`의 Resource가 이 인터페이스를 구현합니다.
- 추후 엔드포인트가 추가되더라도 API 계약은 `spec`에서 먼저 정의합니다.

호출 주체에 따라 Facade를 분리할 수 있습니다.(추후 서비스가 커질 경우 대비)

| 네이밍 | 대상 |
| --- | --- |
| `{Domain}Facade` | 일반 사용자 API |
| `{Domain}AdminFacade` | 관리자 API |

#### `spec/{domain}/facade/sdo/`

Facade의 파라미터/반환 타입으로 사용하는 공개 DTO를 둡니다.

#### `spec/{domain}/mapper/`

도메인 엔티티와 공개 DTO 간 변환을 담당합니다.

- DTO 생성 책임을 엔티티 내부에 분산하지 않고 매퍼로 모읍니다.
- 구현은 수동 매퍼 또는 MapStruct 중 하나를 선택할 수 있습니다.
- 현재 프로젝트는 수동 매퍼와 MapStruct를 혼용할 수 있는 구조를 허용합니다.

---

## 4. {service}-aggregate — 비즈니스 로직 레이어

**역할**: 핵심 비즈니스 로직과 영속성 구현을 담당하는 레이어입니다.

**베이스 패키지**: `{org}.{service}.aggregate`

### 현재 기본 구조

```text
{org}/{service}/aggregate/
├── aggregate/
│   ├── cache/              # 추후 Cache 필요시 사용
│   ├── util/               # 추후 Util이 필요시 사용
│   └── {domain}/
│       ├── exception/
│       ├── logic/
│       └── store/
│           ├── jpo/
│           ├── mapper/
│           ├── projection/
│           ├── repository/
│           ├── doc/         # MongoDB 등 저장소 확장 시 사용 가능
│           ├── query/                  # QueryDSL 동적 쿼리
│           └── clause/                 # QueryDSL 조건 빌더
├── common/
│   ├── entity/
│   ├── exception/
│   └── generator/
├── config/
└── flow/
    ├── {domain}/
    └── vo/
```

### 패키지 상세

#### `flow/`

여러 Logic과 외부 연동을 조합하는 오케스트레이터입니다.

- 복잡한 커맨드 흐름은 `Flow`를 우선 사용합니다.
- 단순 조회는 Resource가 Logic을 직접 호출할 수 있습니다.
- 현재 프로젝트도 필요한 도메인부터 단계적으로 `Flow`를 도입합니다.

> 인증/인가 중심 오케스트레이션처럼 `auth -> aggregate` 조합이 필요한 경우에는
> 의존 방향을 지키기 위해 `auth/flow`에 둘 수 있습니다.

Flow 네이밍 규칙:

| 네이밍 | 역할 |
| --- | --- |
| `{Domain}Flow` | 커맨드/복합 흐름 |
| `{Domain}QueryFlow` | 조회 조합 흐름 |
| `{Domain}AdminFlow` | 관리자 전용 흐름 |

#### `flow/vo/`

`aggregate -> rest` 구간에서만 사용하는 내부 DTO를 둡니다.

#### `aggregate/{domain}/logic/`

도메인 규칙과 상태 변경을 구현합니다.

- `*Logic.java` : 생성/수정/삭제 등 커맨드 로직
- `*QueryLogic.java` : 조회 로직
- `*AdminLogic.java` : 관리자 전용 로직

#### `aggregate/{domain}/store/`

데이터 접근의 단일 창구입니다.

- Logic은 Store를 통해서만 저장소에 접근합니다.
- Store는 Repository, QueryDSL, MongoDB, Redis 등을 캡슐화합니다.

| 클래스/패키지 | 역할 |
| --- | --- |
| `*Store.java` | Logic이 호출하는 데이터 접근 진입점 |
| `jpo/*Jpo.java` | JPA 영속성 모델 |
| `mapper/*Mapper.java` | JPO ↔ Domain 변환 |
| `repository/*Repository.java` | Spring Data Repository |
| `projection/` | 읽기 최적화 결과 객체 |
| `doc/` | 문서형 저장소 모델. 현재는 가이드 범위에서 상세 규칙 제외 |

#### `config/`

aggregate 모듈 전용 설정입니다.

- JPA Repository 스캔
- Entity 스캔
- QueryDSL 도입 시 `JPAQueryFactory` 설정

> QueryDSL은 현재 프로젝트에 적용되어 있지 않지만, 추후 필요한 경우 위 구조를 유지하여 확장합니다.

---

## 5. {service}-auth — 인증 인가 레이어

**역할**: 인증/인가, JWT 처리, Spring Security 보조 컴포넌트를 담당합니다.

**베이스 패키지**: `{org}.{service}.auth`

```text
{org}/{service}/auth/
├── config/                # 인증 관련 빈 등록
├── constant/              # 헤더/쿠키/인증 상수
├── filter/                # JWT 인증 필터
├── handler/               # AuthenticationEntryPoint, AccessDeniedHandler
├── logic/                 # 인증 로직, UserDetailsService
├── store/                 # 인증 전용 조회 저장소
│   └── projection/
└── util/                  # JWT 유틸리티
```

### 패키지 상세

#### `logic/`

- 로그인 인증
- 토큰 발급/재발급
- `UserDetailsService` 구현

#### `store/`

인증 로직에서 필요한 사용자/권한 조회를 담당합니다.
직접 JPA Repository를 참조하지 않고 `aggregate`의 Store를 통해 조회합니다.

#### `filter/`

JWT에서 인증 정보를 복원해 SecurityContext에 설정합니다.

#### `handler/`

보안 실패 응답을 HTTP 응답으로 변환합니다.

- `AuthenticationEntryPoint`
- `AccessDeniedHandler`

#### `config/`

인증 관련 빈을 등록합니다.

- JWT 인증 필터
- 인증에 필요한 보조 빈

### 경계 규칙

- `auth`는 보안 정책 그 자체보다 인증/인가 구현 세부사항을 담당합니다.
- 최종 `SecurityFilterChain` 조립은 `boot`에서 수행합니다.
- 하지만 필터, 엔트리포인트, 인가 실패 핸들러, 인증 상수는 `auth` 모듈에 응집합니다.

---

## 6. {service}-rest — 프레젠테이션 레이어

**역할**: HTTP 요청을 받고 응답을 반환하는 프레젠테이션 레이어입니다.

**베이스 패키지**: `{org}.{service}.rest`

```text
{org}/{service}/rest/
├── {domain}/             # Resource
└── common/handler/       # 공통 예외 응답 처리
```

### 패키지 상세

#### `{domain}/`

Facade 인터페이스를 구현하는 REST 컨트롤러입니다.

- 표준 흐름: `Resource -> Flow -> Logic`
- 단순 조회는 `Resource -> Logic` 직접 호출 가능
- 복잡한 인증/인가 조합은 `Flow`를 우선 사용

현재 프로젝트는 `UserResource`부터 Flow를 적용하고, 인증 중심 조합은 `auth/flow`에서 처리합니다.

#### `common/handler/`

공통 `@RestControllerAdvice`를 둡니다.

- 현재는 공통 핸들러 하나로 예외를 처리합니다.
- 도메인 복잡도가 증가하면 도메인별 핸들러로 분리할 수 있습니다.

---

## 7. {service}-boot — 애플리케이션 부트스트랩

**역할**: 애플리케이션 진입점과 최상위 통합 설정을 담당합니다.

**현재 프로젝트 베이스 패키지**: `com.seoulchonnom.boot`

```text
com/seoulchonnom/boot/
├── SlcnappApplication.java
└── common/config/
    ├── SecurityConfiguration.java
    ├── SwaggerConfig.java
    ├── RedisConfig.java
    └── WebConfig.java
```

### 책임 범위

- Spring Boot 진입점
- 최상위 인프라 설정
- SecurityFilterChain 조립
- 외부 설정 파일 로딩

### 설계 원칙

- `boot`는 인증 세부 구현을 직접 소유하지 않습니다.
- 보안 관련 세부 빈은 `auth`에서 제공하고, `boot`는 이를 조합합니다.
- Multipart converter, Swagger, Redis, WebMvc 설정 같은 인프라 코드는 `boot`에 둡니다.

---

## 8. 공통 아키텍처 패턴

### Facade 패턴

`spec`에서 Facade 인터페이스를 정의하고, `rest`의 Resource가 이를 구현합니다.

```text
[spec]  {Domain}Facade
            ↑ implements
[rest]  {Domain}Resource
            ├─→ {Domain}Flow
            └─→ {Domain}Logic
```

> 현재 일부 Facade는 아직 대응 Resource가 구현되지 않았습니다.
> 신규 엔드포인트를 추가할 때는 반드시 이 패턴을 따릅니다.

### Flow 패턴

여러 Logic/Auth 조합이 필요한 경우 Resource에서 직접 오케스트레이션하지 않고 `Flow`에 위임합니다.

예:

- 회원 로그인
- 토큰 재발급
- 여러 도메인을 동시에 조합하는 커맨드

### Store 패턴

데이터 접근은 `Logic -> Store -> Repository` 순으로 캡슐화합니다.

```text
Logic
  └── Store
        ├── Repository
        ├── Query
        ├── Projection
        └── Doc
```

### JPO vs Entity 분리

도메인 모델과 영속성 모델은 분리합니다.

| 클래스 | 위치 | 역할 |
| --- | --- | --- |
| `*.java` (entity) | `spec/{domain}/entity/` | 순수 도메인 모델 |
| `*Jpo.java` | `aggregate/{domain}/store/jpo/` | JPA 영속성 모델 |

둘 사이의 변환은 `aggregate/{domain}/store/mapper/`가 담당합니다.

### Entity ↔ DTO 변환 분리

공개 DTO 변환은 `spec/{domain}/mapper/`에 둡니다.

- Entity는 상태/행위 중심으로 유지
- DTO 생성 로직은 Mapper가 담당

---

## 9. 요청 흐름

### 표준 요청 처리

```text
HTTP Request
    ↓
[rest] Resource (implements Facade)
    ↓
[aggregate or auth] Flow      # 필요 시
    ↓
[aggregate] Logic
    ↓
[aggregate] Store
    ↓
[aggregate] Repository / Query / Doc
    ↓
DB / External Store
```

### 인증 요청 예시

```text
HTTP Request
    ↓
[rest] UserResource
    ↓
[auth] UserFlow
    ├─→ [auth] UserAuthLogic
    └─→ [aggregate] UserLogic
    ↓
[spec] UserMapper
    ↓
HTTP Response
```

---

## 10. DTO 네이밍 컨벤션

### DTO 접미사 규칙

| 접미사 | 이름 | 사용 목적 |
| --- | --- | --- |
| `Sdo` | Save Data Object | 저장 입력 |
| `Qdo` | Query Data Object | 조회 조건 |
| `Cdo` | Creation Data Object | 생성 입력 |
| `Rdo` | Response Data Object | 조회/응답 결과 |
| `Udo` | Update Data Object | 수정 입력 |
| `Pdo` | Projection Data Object | Projection 결과 |

### DTO 배치 기준

```text
Facade 파라미터/반환 타입
    → spec/{domain}/facade/sdo/

Flow -> Rest 내부 DTO
    → aggregate/flow/vo/ 또는 auth/flow/vo/

Store Projection 결과
    → aggregate/{domain}/store/projection/

문서형 저장소 전용 모델
    → aggregate/{domain}/store/doc/
```

### 추가 원칙

- 공개 계약 DTO는 반드시 `spec`에 둡니다.
- 내부 조합용 DTO는 `aggregate`에 둡니다.
- 공개 응답 생성은 Mapper를 통해 일관되게 수행합니다.
