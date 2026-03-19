# 멀티 모듈 DDD 아키텍처 정리 (spec · aggregate · auth · rest · boot)

본 문서는 현재까지 대화에서 정리된 **Spring Boot 기반 멀티 모듈 DDD 아키텍처**를 기준으로,
각 모듈의 **책임(Responsibility), 포함해야 할 구성요소, 금지/주의 사항, 의존성 방향**을 체계적으로 정리한다.

---

## 1. 전체 목표

- **도메인 중심 설계(DDD)**를 유지하면서도 Spring Boot의 현실적인 실행/조립(Assembly) 모델에 맞춘다.
- “**정의(계약)** / **도메인 로직** / **인증 보안** / **API 어댑터** / **부트스트랩 조립**” 책임을 분리한다.
- 의존성 방향을 **단방향**으로 고정하여 순환 의존과 레이어 침범을 예방한다.
- 보안(JWT)은 “**구현(auth)**”과 “**조립(boot)**”을 분리한다.

---

## 2. 모듈 의존성 방향 (확정)

```
spec (계약)
 ↑
aggregate (도메인 로직 + 영속성)
 ↑
auth (인증/인가)
 ↑
rest (API 어댑터)
 ↑
boot (실행/조립)
```

### 의존성 규칙

- **단방향 의존**: 아래 모듈은 위 모듈에만 의존 가능 (역방향 금지)
- **spec**: 최하위 계층, 아무에게도 의존하지 않음 (Spring context, validation만 허용)
- **aggregate**: spec에만 의존 (JPA, MySQL 추가)
- **auth**: spec, aggregate에 의존 (Spring Security, Redis, JWT 추가)
- **rest**: spec, aggregate, auth에 의존 (Spring Web 추가)
- **boot**: 모든 모듈에 의존 (조립 책임)

### 왜 이 순서인가?

1. **spec**: 모든 모듈이 참조하는 공통 계약
2. **aggregate**: 도메인 핵심 로직 (인증과 무관하게 동작 가능해야 함)
3. **auth**: aggregate의 사용자 정보를 조회하여 인증 처리
4. **rest**: auth의 인증 결과를 기반으로 API 제공
5. **boot**: 모든 것을 조립하여 실행

---

## 3. 모듈별 역할 상세

---

### 3.1 spec 모듈 (Contract / Shared Kernel)

#### 역할

- 시스템 전반에서 공유되는 **"계약(Contract)"**과 **최소 공통 타입**을 정의한다.
- 도메인/애플리케이션/어댑터 어디서든 참조 가능한 **공통 정의의 단일 진실(Single Source of Truth)**.
- **순수 도메인 엔티티**를 정의하여 비즈니스 규칙의 명확한 표현을 제공한다.

#### 포함해야 할 것들

- **도메인 엔티티 (순수 형태)**
    - `User`, `Authority`, `Schedule`, `Trip`, `Quiz` 등
    - 기본 엔티티 클래스: `Entity`, `DomainEntity` (UUID, 타임스탬프 포함)
    - JPA 구현 상세는 aggregate의 JPO에서 확장
    - 도메인 로직은 최소화, 주로 구조적 정의에 집중
- **Facade 인터페이스**
    - 모듈 간 통신 계약: `UserFacade`, `ScheduleFacade`, `TripFacade`
    - aggregate 모듈에서 구현, rest 모듈에서 호출
- **SDO (Service Data Object)**
    - `*Rdo` (Response Data Object): 응답 전달 객체
    - `*Cdo` (Create Data Object): 생성 요청 객체
    - `*Udo` (Update Data Object): 수정 요청 객체
    - 모듈 간 데이터 전달 규약으로 사용
- **도메인 공용 타입**
    - Value Object, Enum, Identifier(필요시)
- **공통 Exception / Error Code**
    - `DomainException`, `ErrorCode` 등 예외 계층 구조 정의
    - `ErrorResponse`: 표준 오류 응답 형식
- **Validation Annotation**
    - `jakarta.validation` 기반 어노테이션 및 제약 정의
- **API 스펙 관련 최소 정의**
    - OpenAPI annotations 정도(선택)

#### 지양 / 금지

- Spring Boot 실행/설정 코드 금지 (`@SpringBootApplication`, `SecurityConfig` 등)
- **JPA 구현 상세 금지** (`@Entity` 어노테이션은 가능하나 복잡한 매핑 로직은 JPO에서 처리)
- Infrastructure 의존 금지(특정 DB, Redis, Kafka, WebClient 등)
- 복잡한 도메인 로직 구현 지양 (aggregate에서 처리)

#### 기대 효과

- 도메인 핵심 모델이 프레임워크로부터 최소한의 의존성만 유지
- 명확한 모듈 간 계약으로 의존성 방향 준수
- 재사용 가능한 도메인 정의

---

### 3.2 aggregate 모듈 (Domain Logic / Application Core)

#### 역할

- "업무 규칙"과 "도메인 정책"이 존재하는 핵심 모듈.
- 유스케이스(서비스), 도메인 모델 구현, 저장소(Repository) 구현을 담당.
- spec의 도메인 엔티티를 JPO로 확장하여 실제 JPA 영속성 구현.

#### 포함해야 할 것들

- **Logic 레이어 (유스케이스)**
    - 트랜잭션 경계(`@Transactional`)
    - 도메인 규칙/검증/상태 전이 로직
    - 예: `UserLogic`, `ScheduleLogic`, `TripLogic`
- **Store 레이어**
    - Repository 패턴 구현
    - JPO와 Repository 관리
    - 예: `UserStore`, `ScheduleStore`
- **JPO (JPA Objects)**
    - `EntityJpo`, `DomainEntityJpo`: spec의 엔티티를 확장
    - 실제 JPA 매핑 정의 (`@Entity`, `@Table`, `@Column` 등)
    - 복잡한 연관관계, 인덱스, 제약조건 등 구현
    - 예: `UserJpo extends User`, `AuthorityJpo extends Authority`
- **Repository 인터페이스 및 구현**
    - `JpaRepository` 기반 구현
    - 커스텀 쿼리 메서드
    - 예: `UserRepository`, `ScheduleRepository`
- **도메인별 예외 (Domain-specific Exceptions)**
    - User 도메인: `InvalidUserException`, `UserLoginFailCountOverException`, `InvalidAccessTokenException`,
      `InvalidRefreshTokenException`
    - Schedule 도메인: `ScheduleNotFoundException`, `InvalidScheduleDateException`,
      `InvalidScheduleRegisterRequestException`
    - Trip 도메인: `TripNotFoundException`
    - Depot (파일): `FileExtException`, `FileSizeException`, `FilePathInvalidException`, `FileUploadException`
    - 공통: `BadRequestException`, `InternalServerErrorException`, `PayloadTooLargeException`,
      `UnsupportedMediaTypeException`
- **Facade 구현**
    - spec에 정의된 Facade 인터페이스의 실제 구현
    - 모듈 간 통신 지점
- **Mapper (선택적)**
    - JPO ↔ Domain/DTO 변환은 aggregate에서 관리
    - 도메인 순수성을 위해 엔티티 내부보다는 별도 Mapper 사용 권장

#### 지양 / 금지

- SecurityContext에 직접 의존하는 도메인 로직 지양
    - 인증 정보는 "값"으로 전달받는 것을 권장
- Controller/Web 관련 코드 금지
- HTTP 관련 의존성 금지

#### 기대 효과

- 도메인 규칙이 UI/인프라에 흔들리지 않음
- 테스트 가능한 순수 로직 계층 확보
- spec의 계약을 구현하여 명확한 책임 분리

---

### 3.3 auth 모듈 (Authentication / Authorization Domain + Security Implementation)

#### 역할

- 인증/인가를 "하나의 독립된 보안 도메인"으로 보고, JWT 기반 인증 체계를 구현한다.
- Refresh Token 관리, 로그인 실패 추적 등 인증 관련 모든 기능을 담당.
- **SecurityFilterChain에 '어디에' 끼울지는 boot가 담당(조립)**한다.

#### 포함해야 할 것들

- **JWT 토큰 처리**
    - `JwtTokenProvider`: 토큰 생성/검증/클레임 파싱
    - Access Token: 단기 토큰 (응답 본문으로 전달)
    - Refresh Token: 장기 토큰 (Redis 저장, HTTP-only Cookie로 전달)
- **JWT Filter 구현**
    - `JwtAuthenticationFilter` (GenericFilterBean/OncePerRequestFilter)
    - 요청에서 토큰 추출 → 검증 → `SecurityContextHolder` 세팅
- **Spring Security 연동 구현**
    - `UserAuthDetailLogic`: `UserDetailsService` 구현
    - `UserAuthLogic`: 인증 비즈니스 로직 (로그인, 토큰 갱신)
    - `UserDetail` projection: 인증에 필요한 사용자 정보
- **Redis 기반 Refresh Token 관리**
    - `UserAuthStore`: Redis 연동 저장소
    - Refresh Token 저장/조회/삭제
    - TTL 관리 (기본 14일)
- **로그인 실패 추적**
    - 실패 횟수 카운팅 (기본 5회 제한)
    - 계정 잠금 및 해제 (기본 300초)
    - `Propagation.REQUIRES_NEW`를 사용한 독립 트랜잭션 처리
- **보안 예외 정의**
    - `InvalidJwtTokenException`, `ExpiredJwtTokenException` 등
- **보안 예외 처리 컴포넌트**
    - `AuthenticationEntryPoint` (인증 실패: 401)
    - `AccessDeniedHandler` (인가 실패: 403)

#### 의존성

- Spring Security, Redis, JWT (io.jsonwebtoken:jjwt)
- aggregate 모듈 (사용자 정보 조회)

#### 지양 / 금지

- `@SpringBootApplication` / 실행 진입점 금지
- 필터를 체인에 등록하는 조립 로직은 `boot`에서 수행
- 도메인 비즈니스 로직 포함 금지 (인증/인가 외)

---

### 3.4 rest 모듈 (Web Adapter / Controller Layer)

#### 역할

- 외부 요청(HTTP)을 받아 유스케이스(aggregate의 Logic/Facade)를 호출하고 응답을 반환하는 "어댑터".
- 인증 여부는 Spring Security의 결과를 기반으로 접근 제어(인가)를 받는다.
- API 문서화 및 표준 응답 형식 제공.

#### 포함해야 할 것들

- **Controller**
    - REST API 엔드포인트 정의
    - spec의 Facade 인터페이스 호출
    - Request/Response DTO 사용
    - 입력 검증 (`@Valid`, `@Validated`)
    - 예: `UserController`, `ScheduleController`, `TripController`
- **ControllerDocs 인터페이스**
    - Swagger/OpenAPI 문서화를 위한 인터페이스
    - `@Operation`, `@ApiResponse` 등 어노테이션 정의
    - Controller가 이 인터페이스를 구현하여 문서와 구현 분리
- **표준 응답 형식**
    - `{ success: boolean, message: string, data: object }`
    - spec의 SDO (Rdo)를 data 필드에 담아 반환
- **Global Exception Handler**
    - `@RestControllerAdvice` 기반
    - `CommonExceptionHandler`: 도메인 예외를 HTTP 상태 코드로 매핑
    - `CommonAccessDeniedHandler`: 403 Forbidden 처리
    - 단, Filter 레벨 예외는 auth 모듈의 EntryPoint/Handler에서 처리
- **API 문서/Swagger 설정**
    - Swagger UI 경로: `/api-test` (개발 환경)
    - Base path 설정 (application.yml)

#### 의존성

- Spring Web, Spring Security
- auth 모듈 (인증 확인용, 직접 사용은 최소화)
- aggregate 모듈 (Facade 인터페이스 호출)
- spec 모듈 (SDO, Facade 인터페이스)

#### 지양 / 금지

- 도메인 규칙 로직 포함 금지 (컨트롤러는 얇게, 위임만)
- JWT 파싱/검증 로직 포함 금지 (auth에서 처리)
- 트랜잭션 처리 금지 (aggregate의 Logic에서 처리)
- 데이터베이스 직접 접근 금지

---

### 3.5 boot 모듈 (Bootstrap / Assembly / Runtime Configuration)

#### 역할

- 애플리케이션 실행 진입점 (`SlcnappApplication.java`).
- 모든 모듈을 **조립**하여 실제 런타임을 구성한다.
- "어떤 필터를 어떤 순서로 체인에 넣을지" 같은 **조립 결정권**이 존재.
- Spring Boot Gradle Plugin이 적용되어 실행 가능한 JAR를 생성.

#### 포함해야 할 것들

- **`@SpringBootApplication`**
    - Component Scan 기준 (`com.seoulchonnom.boot` 패키지)
    - 모든 하위 모듈의 컴포넌트 스캔
- **SecurityConfiguration**
    - `SecurityFilterChain` Bean 정의
    - `JwtAuthenticationFilter`를 필터 체인에 등록
    - `AuthenticationEntryPoint`, `AccessDeniedHandler` 연결
    - Stateless 정책 (`SessionCreationPolicy.STATELESS`)
    - CORS 설정 (현재: allowedOrigins("*") - 개선 필요)
    - 권한별 엔드포인트 접근 제어
- **환경/프로필 설정**
    - `application.properties` (개발): 하드코딩된 설정값
    - `application.yml` (프로덕션): 환경변수 주입 방식
    - 주요 설정값:
        - `cookie.expire.time`: Refresh Token TTL
        - `login.fail.limit.count`: 로그인 실패 제한 횟수
        - `login.limit.clear.time`: 계정 잠금 시간
        - `upload.path`: 파일 업로드 경로
- **통합 구성**
    - 모듈 간 Bean wiring
    - 데이터베이스 설정 (MySQL, Redis)
    - JPA/Hibernate 설정

#### 의존성

- 모든 모듈에 대한 의존성 (rest, auth, aggregate, spec)
- Spring Boot Starter (Web, Security, Data JPA, Data Redis)

#### 지양 / 금지

- 도메인/비즈니스 로직 금지
- JWT 검증 로직 구현 금지 (구현은 auth, 조립만 boot)
- Controller/Service/Repository 구현 금지 (다른 모듈 역할)

---

## 4. 실전 가이드

### 4.1 새로운 기능 추가 시 체크리스트

예시: "사용자 프로필 이미지 업로드" 기능 추가

1. **spec 모듈**
    - [ ] 필요한 경우 엔티티에 필드 추가 (`User.profileImagePath`)
    - [ ] SDO 정의 (`UserProfileImageRdo`, `UserProfileImageUdo`)
    - [ ] Facade 인터페이스에 메서드 추가 (`UserFacade.updateProfileImage()`)

2. **aggregate 모듈**
    - [ ] JPO에 JPA 매핑 추가 (`UserJpo.profileImagePath` with `@Column`)
    - [ ] Logic에 비즈니스 로직 구현 (`UserLogic.updateProfileImage()`)
    - [ ] Store/Repository에 필요한 쿼리 메서드 추가
    - [ ] 필요한 경우 도메인 예외 정의
    - [ ] Facade 구현

3. **rest 모듈**
    - [ ] Controller에 엔드포인트 추가 (`POST /user/profile/image`)
    - [ ] ControllerDocs에 Swagger 문서 추가
    - [ ] Request DTO 검증 규칙 추가

4. **auth 모듈**
    - [ ] 인증이 필요한 경우 권한 확인 (이미 구현되어 있으면 생략)

5. **boot 모듈**
    - [ ] SecurityConfiguration에서 엔드포인트 권한 설정 추가
    - [ ] 필요한 경우 application.yml에 설정 추가

### 4.2 모듈 간 통신 패턴

#### Controller → Facade → Logic 흐름

```java
// rest/UserController.java
@PostMapping("/profile")
public ResponseEntity<?> updateProfile(@RequestBody UserProfileUdo udo) {
    UserProfileRdo rdo = userFacade.updateProfile(udo);
    return ResponseEntity.ok(new SuccessResponse(rdo));
}

// spec/UserFacade.java
public interface UserFacade {
    UserProfileRdo updateProfile(UserProfileUdo udo);
}

// aggregate/UserFacadeImpl.java
@Component
public class UserFacadeImpl implements UserFacade {
    private final UserLogic userLogic;

    @Override
    public UserProfileRdo updateProfile(UserProfileUdo udo) {
        return userLogic.updateProfile(udo);
    }
}

// aggregate/UserLogic.java
@Service
@Transactional
public class UserLogic {
    private final UserStore userStore;

    public UserProfileRdo updateProfile(UserProfileUdo udo) {
        // 비즈니스 로직
        UserJpo user = userStore.findById(udo.getUserId());
        user.updateProfile(udo);
        userStore.save(user);
        return UserProfileRdo.from(user);
    }
}
```

#### 인증 정보 전달 패턴

```java
// rest/UserController.java
@GetMapping("/me")
public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
    String username = userDetails.getUsername();
    UserRdo rdo = userFacade.getUserByUsername(username);
    return ResponseEntity.ok(new SuccessResponse(rdo));
}
```

### 4.3 일반적인 실수와 해결책

#### ❌ 잘못된 예: Controller에서 직접 Repository 호출

```java
// rest/UserController.java
@Autowired
private UserRepository userRepository; // 잘못됨!

@GetMapping("/users")
public List<User> getUsers() {
    return userRepository.findAll(); // 계층 침범!
}
```

#### ✅ 올바른 예: Facade를 통한 호출

```java
// rest/UserController.java
@Autowired
private UserFacade userFacade; // 올바름

@GetMapping("/users")
public ResponseEntity<?> getUsers() {
    List<UserRdo> users = userFacade.getAllUsers();
    return ResponseEntity.ok(new SuccessResponse(users));
}
```

#### ❌ 잘못된 예: spec에 JPA 구현 상세 포함

```java
// spec/User.java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username")
}) // 너무 구체적!
public class User {
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL) // 구현 상세!
    private List<Authority> authorities;
}
```

#### ✅ 올바른 예: spec은 간결하게, JPO에서 상세 구현

```java
// spec/User.java
@Entity
public class User {
    private List<Authority> authorities; // 관계만 명시
}

// aggregate/UserJpo.java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username")
})
public class UserJpo extends User {
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "user")
    @Override
    public List<Authority> getAuthorities() {
        return super.getAuthorities();
    }
}
```

#### ❌ 잘못된 예: Logic에서 SecurityContext 직접 접근

```java
// aggregate/UserLogic.java
public UserRdo getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication(); // 나쁨!
    String username = auth.getName();
    // ...
}
```

#### ✅ 올바른 예: 파라미터로 전달받기

```java
// rest/UserController.java
@GetMapping("/me")
public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
    UserRdo rdo = userFacade.getUserByUsername(userDetails.getUsername());
    return ResponseEntity.ok(new SuccessResponse(rdo));
}

// aggregate/UserLogic.java
public UserRdo getUserByUsername(String username) { // 값으로 전달받음
    // ...
}
```

### 4.4 순환 의존성 방지

#### 발생 가능한 시나리오

- auth 모듈이 aggregate의 UserLogic을 사용
- aggregate의 UserLogic이 auth의 JwtTokenProvider를 사용하려고 시도
- → 순환 의존성 발생!

#### 해결 방법

1. **이벤트 기반 통신**: Spring ApplicationEvent 사용
2. **Facade 분리**: 공통 인터페이스를 spec에 정의
3. **의존성 방향 재검토**: auth가 aggregate를 호출하는 것은 OK, 역방향은 NO

### 4.5 테스트 전략

#### spec 모듈

- 단위 테스트: Value Object, Enum 로직
- DTO 직렬화/역직렬화 테스트

#### aggregate 모듈

- **Logic 계층**: `@SpringBootTest` 또는 `@DataJpaTest`
- 트랜잭션 동작 확인
- 도메인 규칙 검증 테스트
- **Store/Repository**: 쿼리 메서드 테스트

#### auth 모듈

- JWT 토큰 생성/검증 단위 테스트
- Redis 연동 통합 테스트 (`@DataRedisTest`)
- 로그인 실패 추적 시나리오 테스트

#### rest 모듈

- `@WebMvcTest`: Controller 단위 테스트
- MockMvc로 HTTP 요청/응답 검증
- 입력 검증 테스트

#### boot 모듈

- `@SpringBootTest`: 전체 통합 테스트
- SecurityFilterChain 동작 확인
- 엔드투엔드 시나리오 테스트

---

## 5. 현재 프로젝트 구조 예시

```
slcnapp/
├── slcn-spec/
│   ├── src/main/java/com/seoulchonnom/spec/
│   │   ├── common/
│   │   │   ├── entity/
│   │   │   │   ├── Entity.java
│   │   │   │   └── DomainEntity.java
│   │   │   └── response/
│   │   │       └── ErrorResponse.java
│   │   ├── user/
│   │   │   ├── entity/
│   │   │   │   ├── User.java
│   │   │   │   └── Authority.java
│   │   │   ├── facade/
│   │   │   │   └── UserFacade.java
│   │   │   └── sdo/
│   │   │       ├── UserRdo.java
│   │   │       ├── UserCdo.java
│   │   │       └── UserUdo.java
│   │   ├── schedule/
│   │   │   └── entity/Schedule.java
│   │   └── trip/
│   │       ├── entity/
│   │       │   ├── Trip.java
│   │       │   └── Quiz.java
│   │       └── facade/TripFacade.java
│
├── slcn-aggregate/
│   ├── src/main/java/com/seoulchonnom/aggregate/
│   │   ├── common/
│   │   │   ├── exception/
│   │   │   │   ├── BadRequestException.java
│   │   │   │   └── InternalServerErrorException.java
│   │   │   └── generator/
│   │   │       └── PasswordGenerator.java
│   │   ├── user/
│   │   │   ├── logic/
│   │   │   │   └── UserLogic.java
│   │   │   ├── store/
│   │   │   │   ├── UserStore.java
│   │   │   │   ├── jpo/
│   │   │   │   │   ├── UserJpo.java (extends User)
│   │   │   │   │   └── AuthorityJpo.java (extends Authority)
│   │   │   │   └── repository/
│   │   │   │       └── UserRepository.java
│   │   │   └── exception/
│   │   │       ├── InvalidUserException.java
│   │   │       └── UserLoginFailCountOverException.java
│   │   ├── schedule/
│   │   │   ├── logic/ScheduleLogic.java
│   │   │   ├── store/ScheduleStore.java
│   │   │   └── exception/ScheduleNotFoundException.java
│   │   └── trip/
│   │       ├── logic/TripLogic.java
│   │       └── store/TripStore.java
│
├── slcn-auth/
│   ├── src/main/java/com/seoulchonnom/auth/
│   │   ├── util/
│   │   │   └── JwtTokenProvider.java
│   │   ├── filter/
│   │   │   └── JwtAuthenticationFilter.java
│   │   ├── logic/
│   │   │   ├── UserAuthLogic.java
│   │   │   └── UserAuthDetailLogic.java (implements UserDetailsService)
│   │   └── store/
│   │       ├── UserAuthStore.java (Redis)
│   │       └── projection/UserDetail.java
│
├── slcn-rest/
│   ├── src/main/java/com/seoulchonnom/rest/
│   │   ├── common/
│   │   │   └── handler/
│   │   │       ├── CommonExceptionHandler.java (@RestControllerAdvice)
│   │   │       └── CommonAccessDeniedHandler.java
│   │   ├── user/
│   │   │   ├── UserController.java
│   │   │   └── UserControllerDocs.java
│   │   ├── schedule/
│   │   │   └── ScheduleController.java
│   │   └── trip/
│   │       └── TripController.java
│
└── slcn-boot/
    ├── src/main/java/com/seoulchonnom/boot/
    │   ├── SlcnappApplication.java (@SpringBootApplication)
    │   └── common/
    │       └── config/
    │           └── SecurityConfiguration.java
    └── src/main/resources/
        ├── application.properties (dev)
        └── application.yml (prod)
```

---

## 6. 마이그레이션 가이드 (기존 코드 → DDD 구조)

### 6.1 단계별 접근

#### Phase 1: spec 모듈 정리

1. 엔티티를 spec으로 이동
2. Facade 인터페이스 정의
3. SDO 정의

#### Phase 2: aggregate 모듈 구성

1. JPO 생성 (spec 엔티티 확장)
2. Logic/Store 계층 분리
3. Repository를 Store로 감싸기
4. 도메인 예외 정리

#### Phase 3: auth 모듈 분리

1. JWT 관련 코드 이동
2. Redis 저장소 구현
3. Filter 구현 (조립은 boot에서)

#### Phase 4: rest 모듈 정리

1. Controller를 rest로 이동
2. ControllerDocs 인터페이스 분리
3. Exception Handler 이동
4. Facade 호출로 변경

#### Phase 5: boot 모듈 최소화

1. SecurityConfiguration만 남기기
2. 설정 파일 정리
3. 불필요한 코드 제거

### 6.2 주의사항

- **한 번에 모든 것을 바꾸지 말 것**: 도메인별로 점진적 마이그레이션
- **테스트 유지**: 각 단계마다 기존 기능이 동작하는지 확인
- **의존성 방향 엄수**: 역방향 의존이 생기면 즉시 수정
- **문서화**: 변경사항을 CLAUDE.md에 기록

---

## 7. 참고 자료

- DDD (Domain-Driven Design): Eric Evans
- Hexagonal Architecture (Ports and Adapters): Alistair Cockburn
- Clean Architecture: Robert C. Martin
- Spring Boot 공식 문서: https://spring.io/projects/spring-boot
- Spring Security 공식 문서: https://spring.io/projects/spring-security

---

## 8. 문서 버전 관리

- **Version**: 1.0
- **Last Updated**: 2026-01-03
- **작성자**: DDD 아키텍처 정리
- **변경 이력**:
    - 2026-01-03: 초기 작성, 현재 프로젝트 구조 반영
    - 실제 구현 예시 추가 (JPO, Facade, SDO)
    - 실전 가이드 섹션 추가
