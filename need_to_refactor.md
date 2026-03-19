# 리팩토링 필요 사항 (Architecture Guide 위반)

본 문서는 `architect.md`에 정의된 DDD 아키텍처 가이드라인과 비교하여, 현재 프로젝트에서 개선이 필요한 부분을 정리합니다.

**분석 기준**: architect.md (멀티 모듈 DDD 아키텍처 가이드)
**분석 일자**: 2026-01-04
**우선순위**: 🔴 High (심각), 🟠 Medium (중요), 🟡 Low (개선 권장)

---

## 목차

1. [spec 모듈 위반 사항](#1-spec-모듈-위반-사항)
2. [aggregate 모듈 위반 사항](#2-aggregate-모듈-위반-사항)
3. [rest 모듈 위반 사항](#3-rest-모듈-위반-사항)
4. [boot 모듈 위반 사항](#4-boot-모듈-위반-사항)
5. [의존성 위반 사항](#5-의존성-위반-사항)
6. [리팩토링 우선순위](#6-리팩토링-우선순위)

---

## 1. spec 모듈 위반 사항

### 🔴 1.1 HTTP 관련 타입 사용 (UserFacade.java)

**파일**: `slcn-spec/src/main/java/com/seoulchonnom/spec/user/facade/UserFacade.java`

**문제**:

```java
// Line 3, 16
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletResponse;

public interface UserFacade {
    ResponseEntity<Void> registerUser(UserCdo userCdo);  // Line 23
    ResponseEntity<UserRdo> loginUser(HttpServletResponse response, UserLoginCdo userLoginCdo);  // Line 27
}
```

**위반 내용**:

- spec 모듈은 "최소 공통 타입"만 정의해야 하며, HTTP 관련 타입 사용 금지
- `ResponseEntity`, `HttpServletResponse`는 REST API 어댑터(rest 모듈)에서 사용해야 함

**가이드**:
> spec 모듈 지양/금지: Infrastructure 의존 금지(특정 DB, Redis, Kafka, WebClient 등)

**개선 방안**:

```java
// spec/UserFacade.java
public interface UserFacade {
    void registerUser(UserCdo userCdo);
    UserRdo loginUser(UserLoginCdo userLoginCdo);
    UserRdo reissueToken(String refreshToken);
}
```

- ResponseEntity는 Controller(rest)에서 감싸서 반환
- HttpServletResponse는 Controller에서 처리

---

### 🔴 1.2 Swagger 어노테이션 위치 (UserFacade.java)

**파일**: `slcn-spec/src/main/java/com/seoulchonnom/spec/user/facade/UserFacade.java`

**문제**:

```java
// Line 9-15
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
// ...
@Tag(name = "회원 관리 API", description = "회원 가입 및 로그인")
public interface UserFacade {
    @Parameters({@Parameter(name = "X-AUTH-TOKEN", ...)})
    @Operation(summary = "회원 가입", description = "회원 가입 API")
    ResponseEntity<Void> registerUser(UserCdo userCdo);
}
```

**위반 내용**:

- Swagger/API 문서 어노테이션은 spec이 아닌 rest 모듈의 ControllerDocs에 있어야 함
- spec은 순수한 계약(Contract) 정의만 포함

**가이드**:
> architect.md 4.2: "ControllerDocs 인터페이스: Swagger/OpenAPI 문서화를 위한 인터페이스"

**개선 방안**:

```java
// spec/UserFacade.java - Swagger 제거
public interface UserFacade {
    void registerUser(UserCdo userCdo);
    UserRdo loginUser(UserLoginCdo userLoginCdo);
}

// rest/UserControllerDocs.java - 새로 생성
@Tag(name = "회원 관리 API", description = "회원 가입 및 로그인")
public interface UserControllerDocs {
    @Operation(summary = "회원 가입")
    ResponseEntity<Void> registerUser(@RequestBody UserCdo userCdo);

    @Operation(summary = "로그인")
    ResponseEntity<UserRdo> loginUser(@RequestBody UserLoginCdo userLoginCdo);
}

// rest/UserController.java - Docs 구현
@RestController
public class UserController implements UserControllerDocs {
    private final UserFacade userFacade;

    @Override
    public ResponseEntity<Void> registerUser(UserCdo userCdo) {
        userFacade.registerUser(userCdo);
        return ResponseEntity.ok().build();
    }
}
```

---

### 🟠 1.3 비즈니스 로직 포함 (Schedule.java, User.java)

**파일**:

- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/user/entity/User.java`

**문제**:

```java
// Schedule.java
import org.springframework.beans.BeanUtils;  // Line 5 - Spring Framework 의존

public Schedule(ScheduleCdo scheduleCdo) {  // Line 60
    super();
    BeanUtils.copyProperties(scheduleCdo, this);  // 변환 로직
}

public ScheduleRdo toRdo() {  // Line 65 - 변환 로직
    ScheduleRdo scheduleRdo = new ScheduleRdo();
    BeanUtils.copyProperties(this, scheduleRdo);
    return scheduleRdo;
}

public void updateSchedule(ScheduleUdo scheduleUdo) {  // Line 71 - 업데이트 로직
    BeanUtils.copyProperties(scheduleUdo, this);
}

public void hideSchedule() {  // Line 75 - 비즈니스 로직
    this.isVisible = false;
}

// User.java
public User(UserCdo userCdo, String id, String password) {  // Line 25
    super(id);
    User.builder()  // Line 27 - 빌더로 생성하지만 할당하지 않음 (버그!)
        .name(userCdo.getName())
        .password(password)
        .build();
    this.authorityList.add(new Authority());  // Line 33
}

public UserRdo toRdo(String token) {  // Line 36 - 변환 로직
    return UserRdo.builder()
        .accessToken(token)
        .username(this.username)
        .build();
}
```

**위반 내용**:

- spec 엔티티에 변환 로직(`toRdo`, `toDomain`) 포함 - aggregate에서 처리해야 함
- spec 엔티티에 비즈니스 로직 포함 - aggregate의 Logic 계층에서 처리해야 함
- `BeanUtils` (Spring Framework) 의존성 - spec은 Spring context/validation만 허용
- User 생성자에 버그 존재 (Builder로 생성한 객체를 사용하지 않음)

**가이드**:
> spec 모듈: 도메인 로직은 최소화, 주로 구조적 정의에 집중
> aggregate 모듈: Mapper(권장 위치) - JPO ↔ Domain/DTO 변환은 aggregate에서 관리

**개선 방안**:

```java
// spec/Schedule.java - 순수 데이터 구조만
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule extends DomainEntity {
    private String calendarId;
    private String title;
    private boolean isVisible;
    // ... 필드만 정의
}

// aggregate/mapper/ScheduleMapper.java - 변환 로직 분리
@Mapper(componentModel = "spring")
public interface ScheduleMapper {
    Schedule fromCdo(ScheduleCdo cdo);
    ScheduleRdo toRdo(Schedule schedule);
    void updateFromUdo(@MappingTarget Schedule schedule, ScheduleUdo udo);
}

// aggregate/ScheduleLogic.java - 비즈니스 로직
@Service
@Transactional
public class ScheduleLogic {
    private final ScheduleMapper mapper;

    public void hideSchedule(String scheduleId) {
        Schedule schedule = scheduleStore.findById(scheduleId);
        schedule.setVisible(false);  // 또는 도메인 메서드 사용
        scheduleStore.save(schedule);
    }
}
```

---

### 🟡 1.4 인프라 의존성 (Authority.java)

**파일**: `slcn-spec/src/main/java/com/seoulchonnom/spec/user/entity/Authority.java`

**문제**:

```java
public Authority() {  // Line 18
    this.role = Role.USER;
    this.registeredTime = System.currentTimeMillis();  // Line 20 - 시스템 의존성
}

public Authority(Role role) {  // Line 13
    this.role = role;
    this.registeredTime = System.currentTimeMillis();  // Line 15
}
```

**위반 내용**:

- `System.currentTimeMillis()`는 시스템 레벨 의존성
- 시간 생성은 aggregate 계층에서 처리하거나 외부에서 주입받아야 함
- 기본값 설정도 비즈니스 로직으로 간주될 수 있음

**개선 방안**:

```java
// spec/Authority.java - 순수 데이터
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Authority {
    private String userId;
    private Role role;
    private Long registeredTime;
}

// aggregate/UserLogic.java - 생성 로직
public void createAuthority(String userId, Role role) {
    Authority authority = new Authority();
    authority.setUserId(userId);
    authority.setRole(role);
    authority.setRegisteredTime(System.currentTimeMillis());
    // 또는 LocalDateTime.now()
}
```

---

## 2. aggregate 모듈 위반 사항

### 🔴 2.1 JPO가 spec 엔티티를 확장하지 않음

**파일**:

- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/user/store/jpo/UserJpo.java`
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/user/store/jpo/AuthorityJpo.java`

**문제**:

```java
// UserJpo.java
@Entity
public class UserJpo extends DomainEntityJpo {  // Line 21 - User를 extends하지 않음!
    private String username;
    private String name;
    private String password;
    @OneToMany(mappedBy = "userId", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<AuthorityJpo> authorityList;
}

// AuthorityJpo.java
@Entity
public class AuthorityJpo {  // Line 19 - Authority를 extends하지 않음!
    @Id
    private String userId;
    private Role role;
    private Long registeredTime;
}
```

**위반 내용**:

- architect.md와 CLAUDE.md에서 명시한 대로 JPO는 spec 엔티티를 확장해야 함
- 현재는 필드를 중복 정의하고 있음
- Mapper로 변환하고 있지만, 상속 구조가 더 명확함

**가이드**:
> architect.md 3.2: "JPO (JPA Objects): EntityJpo, DomainEntityJpo: spec의 엔티티를 확장"
> architect.md 예시: "UserJpo.java (extends User)"

**개선 방안**:

```java
// aggregate/UserJpo.java - User 확장
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username")
})
public class UserJpo extends User {
    // JPA 구현 상세만 추가
    @OneToMany(mappedBy = "userId", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @Override
    public List<Authority> getAuthorityList() {
        return super.getAuthorityList();
    }
}

// aggregate/AuthorityJpo.java - Authority 확장
@Entity
@Table(name = "authorities")
public class AuthorityJpo extends Authority {
    @Id
    @Override
    public String getUserId() {
        return super.getUserId();
    }

    // 필요한 JPA 어노테이션만 추가
}
```

**장점**:

- 필드 중복 제거
- spec과 aggregate의 관계가 명확해짐
- Mapper 불필요 (형변환만으로 처리 가능)

---

### 🟠 2.2 EAGER Loading 사용 (UserJpo.java)

**파일**: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/user/store/jpo/UserJpo.java`

**문제**:

```java
@OneToMany(mappedBy = "userId", fetch = FetchType.EAGER, cascade = CascadeType.ALL)  // Line 26
private List<AuthorityJpo> authorityList;
```

**위반 내용**:

- EAGER Loading은 N+1 쿼리 문제 발생
- CLAUDE.md에서도 성능 문제로 지적됨

**가이드**:
> CLAUDE.md: "User ↔ Authority: One-to-Many (currently EAGER, should be LAZY)"

**개선 방안**:

```java
@OneToMany(mappedBy = "userId", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
private List<AuthorityJpo> authorityList;
```

필요한 경우 `@EntityGraph` 또는 `JOIN FETCH` 쿼리 사용

---

### 🟡 2.3 Facade 구현 누락

**문제**:

- spec에 정의된 `UserFacade`, `ScheduleFacade`, `TripFacade`의 구현이 aggregate에 없음
- 현재 rest의 Controller가 직접 Facade를 구현하고 있음 (잘못된 구조)

**가이드**:
> architect.md 3.2: "Facade 구현: spec에 정의된 Facade 인터페이스의 실제 구현, 모듈 간 통신 지점"
> architect.md 4.2: "aggregate/UserFacadeImpl.java"

**개선 방안**:

```java
// aggregate/user/UserFacadeImpl.java - 새로 생성
@Component
public class UserFacadeImpl implements UserFacade {
    private final UserLogic userLogic;
    private final UserAuthLogic userAuthLogic;  // auth 모듈 의존

    @Override
    public void registerUser(UserCdo userCdo) {
        userLogic.registerUser(userCdo);
    }

    @Override
    public UserRdo loginUser(UserLoginCdo userLoginCdo) {
        return userAuthLogic.login(userLoginCdo);
    }

    @Override
    public UserRdo reissueToken(String refreshToken) {
        return userAuthLogic.reissueToken(refreshToken);
    }
}
```

---

## 3. rest 모듈 위반 사항

### 🔴 3.1 Controller가 Facade를 구현 (UserResource.java)

**파일**: `slcn-rest/src/main/java/com/seoulchonnom/rest/user/UserResource.java`

**문제**:

```java
@RestController
public class UserResource implements UserFacade {  // Line 14 - Facade 구현!
    @Override
    public ResponseEntity<Void> registerUser(UserCdo userCdo) {
        return new ResponseEntity<>(null);  // Line 17 - 빈 구현
    }
}
```

**위반 내용**:

- **Controller는 Facade를 "호출"해야 하며, "구현"하면 안 됨**
- Facade 구현은 aggregate 모듈의 책임
- 현재 모든 메서드가 null을 반환 (미구현 상태)
- 계층 역전 발생 (rest → aggregate가 아닌, rest에서 구현)

**가이드**:
> architect.md 3.4: "Controller: spec의 Facade 인터페이스 호출"
> architect.md 4.3: "❌ 잘못된 예: Controller에서 직접 Repository 호출"

**개선 방안**:

```java
// rest/UserController.java - Facade 호출
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {
    private final UserFacade userFacade;  // aggregate의 구현체 주입

    @PostMapping("/register")
    @Override
    public ResponseEntity<Void> registerUser(@Valid @RequestBody UserCdo userCdo) {
        userFacade.registerUser(userCdo);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    @Override
    public ResponseEntity<UserRdo> loginUser(
            HttpServletResponse response,
            @Valid @RequestBody UserLoginCdo userLoginCdo) {
        UserRdo userRdo = userFacade.loginUser(userLoginCdo);
        // RefreshToken을 Cookie에 설정하는 로직 추가
        return ResponseEntity.ok(userRdo);
    }
}
```

---

### 🟠 3.2 ControllerDocs 인터페이스 미분리

**문제**:

- 현재 Controller에 Swagger 어노테이션이 없거나 Facade에 혼재
- ControllerDocs 인터페이스 패턴을 사용하지 않음

**가이드**:
> architect.md 3.4: "ControllerDocs 인터페이스: Swagger/OpenAPI 문서화를 위한 인터페이스, Controller가 이 인터페이스를 구현하여 문서와 구현 분리"

**개선 방안**:
1.2 섹션 참조 - UserControllerDocs 생성 예시

---

## 4. boot 모듈 위반 사항

### 🔴 4.1 정의되지 않은 메서드 호출 (SecurityConfiguration.java)

**파일**: `slcn-boot/src/main/java/com/seoulchonnom/boot/common/config/SecurityConfiguration.java`

**문제**:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // ...
        .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        // Line 38 - jwtAuthenticationFilter() 메서드가 정의되어 있지 않음!

        return http.build();
    }
}
```

**위반 내용**:

- `jwtAuthenticationFilter()` 메서드가 클래스에 정의되어 있지 않음 (컴파일 에러)
- Filter를 Bean으로 등록하지 않고 직접 생성하려는 의도로 보임

**개선 방안**:

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {
    private final JwtTokenProvider jwtTokenProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(management ->
                management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests((authorizeRequests) -> authorizeRequests
                .requestMatchers("/swagger-ui/**", "/v3/**").permitAll()
                .requestMatchers("/user/login", "/user/token").permitAll()
                .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                .requestMatchers("/user/register").hasAuthority("ADMIN")
                .anyRequest().hasAuthority("USER"))
            .exceptionHandling(handling ->
                handling.authenticationEntryPoint(authenticationEntryPoint()))
            .exceptionHandling(handling ->
                handling.accessDeniedHandler(accessDeniedHandler()))
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new CommonAuthenticationEntryPoint();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new CommonAccessDeniedHandler();
    }
}
```

---

### 🟠 4.2 Handler와 EntryPoint를 직접 new로 생성

**파일**: `slcn-boot/src/main/java/com/seoulchonnom/boot/common/config/SecurityConfiguration.java`

**문제**:

```java
.exceptionHandling(handling ->
    handling.authenticationEntryPoint(new CommonAuthenticationEntryPoint()))  // Line 36
.exceptionHandling(handling ->
    handling.accessDeniedHandler(new CommonAccessDeniedHandler()))  // Line 37
```

**위반 내용**:

- Handler들을 매번 new로 생성하면 Bean 주입이 불가능
- 만약 Handler가 다른 Bean을 의존한다면 문제 발생

**개선 방안**:
4.1 섹션의 개선 방안 참조 - @Bean으로 등록 후 주입

---

## 5. 의존성 위반 사항

### 🟠 5.1 순환 의존성 위험 (spec ↔ SDO)

**파일**:

- `slcn-spec/src/main/java/com/seoulchonnom/spec/user/entity/User.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java`

**문제**:

```java
// User.java
import com.seoulchonnom.spec.user.facade.sdo.UserCdo;  // Line 7
import com.seoulchonnom.spec.user.facade.sdo.UserRdo;  // Line 8

public User(UserCdo userCdo, String id, String password) { ... }
public UserRdo toRdo(String token) { ... }

// Schedule.java
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleCdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleRdo;
import com.seoulchonnom.spec.schedule.facade.sdo.ScheduleUdo;
```

**위반 내용**:

- 엔티티가 SDO를 import하는 것은 같은 모듈 내이지만 순환 참조 위험
- SDO는 엔티티를 참조할 수 있지만, 역방향은 지양
- 변환 로직은 Mapper에서 처리하는 것이 더 명확

**개선 방안**:

- 엔티티는 순수한 데이터 구조만 정의
- 변환 로직은 aggregate의 Mapper로 분리

---

## 6. 리팩토링 우선순위

### Phase 1: 긴급 (컴파일 에러 / 구조적 문제)

1. ✅ **[boot] SecurityConfiguration 컴파일 에러 수정** (4.1)
    - `jwtAuthenticationFilter()` 메서드 정의 또는 Filter Bean 주입

2. ✅ **[rest] Controller의 Facade 구현 제거** (3.1)
    - aggregate에 FacadeImpl 생성
    - Controller는 Facade 호출로 변경

3. ✅ **[spec] HTTP 타입 제거** (1.1)
    - Facade 인터페이스에서 ResponseEntity, HttpServletResponse 제거
    - Controller에서 HTTP 처리

### Phase 2: 중요 (아키텍처 정합성)

4. ✅ **[spec] Swagger 어노테이션 분리** (1.2)
    - spec에서 Swagger 제거
    - rest에 ControllerDocs 인터페이스 생성

5. ✅ **[aggregate] JPO 상속 구조 수정** (2.1)
    - UserJpo extends User
    - AuthorityJpo extends Authority
    - Mapper 제거 또는 단순화

6. ✅ **[spec] 변환 로직 제거** (1.3)
    - toRdo(), toDomain() 메서드 제거
    - aggregate에 Mapper 생성

### Phase 3: 개선 (성능 및 품질)

7. ✅ **[aggregate] EAGER → LAZY 변경** (2.2)
    - UserJpo의 authorityList fetch 타입 변경

8. ✅ **[spec] 비즈니스 로직 제거** (1.3, 1.4)
    - 엔티티를 순수 데이터 구조로 변경
    - 생성자, 업데이트 로직을 Logic 계층으로 이동

9. ✅ **[boot] Handler Bean 등록** (4.2)
    - EntryPoint, AccessDeniedHandler를 Bean으로 등록

### Phase 4: 최적화

10. ✅ **테스트 코드 작성**
    - 각 계층별 단위 테스트 추가
    - 통합 테스트 작성

11. ✅ **성능 개선**
    - 인덱스 추가 (CLAUDE.md 참조)
    - 쿼리 최적화

---

## 7. 리팩토링 체크리스트

각 Phase 완료 시 체크:

### Phase 1 체크리스트

- [ ] SecurityConfiguration 빌드 성공
- [ ] UserFacadeImpl 생성 완료
- [ ] UserController Facade 호출 구현
- [ ] 애플리케이션 정상 실행

### Phase 2 체크리스트

- [ ] UserControllerDocs 인터페이스 생성
- [ ] spec에서 Swagger 어노테이션 제거
- [ ] UserJpo, AuthorityJpo 상속 구조 변경
- [ ] Mapper 수정 또는 제거
- [ ] UserFacade에서 HTTP 타입 제거

### Phase 3 체크리스트

- [ ] EAGER → LAZY 변경 및 테스트
- [ ] Schedule, User 엔티티 순수화
- [ ] ScheduleMapper, UserMapper 생성
- [ ] ScheduleLogic, UserLogic 비즈니스 로직 이동

### Phase 4 체크리스트

- [ ] 단위 테스트 커버리지 50% 이상
- [ ] 통합 테스트 주요 시나리오 커버
- [ ] 성능 테스트 및 최적화

---

## 8. 참고 문서

- **architect.md**: DDD 아키텍처 가이드라인
- **CLAUDE.md**: 프로젝트 개요 및 기술 부채 정리
- **docs/database-entity-analysis.md**: 데이터베이스 개선 사항
- **docs/improvement-v2.md**: 전체 개선 계획 (있다면)

---

## 9. 리팩토링 시 주의사항

1. **한 번에 모든 것을 바꾸지 말 것**
    - Phase별로 점진적 리팩토링
    - 각 단계마다 테스트하여 기능 정상 동작 확인

2. **브랜치 전략**
    - 각 Phase별로 별도 브랜치 생성 권장
    - `refactor/phase-1-critical-fixes`
    - `refactor/phase-2-architecture`
    - `refactor/phase-3-improvements`

3. **테스트 우선**
    - 리팩토링 전 기존 기능의 테스트 코드 작성
    - 리팩토링 후 동일한 테스트가 통과하는지 확인

4. **문서화**
    - 변경사항을 CLAUDE.md에 기록
    - 주요 결정사항은 별도 ADR(Architecture Decision Record) 작성 고려

5. **의존성 방향 엄수**
    - spec ← aggregate ← auth ← rest ← boot
    - 역방향 의존 발생 시 즉시 수정

---

## 10. 버전 정보

- **문서 버전**: 1.0
- **분석 일자**: 2026-01-04
- **대상 브랜치**: ddd
- **분석자**: Claude Code
- **다음 리뷰 예정**: Phase 1 완료 후

---

## 부록: 참고 코드 예시

### A. 올바른 Facade 패턴 구현

```java
// spec/UserFacade.java
public interface UserFacade {
    void registerUser(UserCdo userCdo);
    UserRdo loginUser(UserLoginCdo userLoginCdo);
}

// aggregate/UserFacadeImpl.java
@Component
public class UserFacadeImpl implements UserFacade {
    private final UserLogic userLogic;

    @Override
    public void registerUser(UserCdo userCdo) {
        userLogic.registerUser(userCdo);
    }

    @Override
    public UserRdo loginUser(UserLoginCdo userLoginCdo) {
        return userLogic.loginUser(userLoginCdo);
    }
}

// rest/UserController.java
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserFacade userFacade;

    @PostMapping("/user/register")
    public ResponseEntity<Void> registerUser(@RequestBody UserCdo userCdo) {
        userFacade.registerUser(userCdo);
        return ResponseEntity.ok().build();
    }
}
```

### B. 올바른 JPO 상속 구조

```java
// spec/User.java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User extends DomainEntity {
    private String username;
    private String name;
    private String password;
    private List<Authority> authorityList;
}

// aggregate/UserJpo.java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserJpo extends User {
    @Column(nullable = false, unique = true)
    @Override
    public String getUsername() {
        return super.getUsername();
    }

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "user")
    @Override
    public List<Authority> getAuthorityList() {
        return super.getAuthorityList();
    }
}
```

### C. 올바른 Mapper 패턴

```java
// aggregate/mapper/UserMapper.java
@Mapper(componentModel = "spring")
public interface UserMapper {
    User fromCdo(UserCdo cdo);
    UserRdo toRdo(User user);

    @Mapping(target = "id", ignore = true)
    void updateFromUdo(@MappingTarget User user, UserUdo udo);
}

// aggregate/UserLogic.java
@Service
@RequiredArgsConstructor
public class UserLogic {
    private final UserMapper userMapper;
    private final UserStore userStore;

    public UserRdo getUserInfo(String userId) {
        User user = userStore.findById(userId);
        return userMapper.toRdo(user);
    }
}
```

---

**이 문서는 프로젝트 리팩토링의 로드맵이자 체크리스트입니다.**
**Phase별로 순차적으로 진행하며, 각 단계 완료 시 체크리스트를 업데이트하세요.**
