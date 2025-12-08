# SLCN App 코드 분석 및 개선 방안

## 목차
1. [프로젝트 개요](#프로젝트-개요)
2. [아키텍처 분석](#아키텍처-분석)
3. [보안 개선점](#보안-개선점)
4. [아키텍처 및 설계 개선점](#아키텍처-및-설계-개선점)
5. [데이터베이스 및 엔티티 개선점](#데이터베이스-및-엔티티-개선점)
6. [코드 품질 개선점](#코드-품질-개선점)
7. [API 설계 개선점](#api-설계-개선점)
8. [테스트 및 품질 보증](#테스트-및-품질-보증)
9. [성능 최적화 방안](#성능-최적화-방안)
10. [우선순위별 개선 로드맵](#우선순위별-개선-로드맵)

---

## 프로젝트 개요

### 기술 스택
- **Framework**: Spring Boot 3.4.0
- **Language**: Java 17
- **Database**: MySQL
- **Cache**: Redis
- **Security**: Spring Security + JWT
- **Documentation**: Swagger/OpenAPI
- **Build Tool**: Gradle

### 주요 모듈
1. **User 모듈**: 사용자 인증/인가, JWT 토큰 관리
2. **Schedule 모듈**: 일정 관리 (CRUD, 날짜 기반 조회)
3. **Trip 모듈**: 여행 정보 및 퀴즈 관리
4. **Depot 모듈**: 파일 업로드 및 저장
5. **Common 모듈**: 보안 설정, 예외 처리, 공통 설정

---

## 아키텍처 분석

### 현재 구조
```
com.seoulchonnom.slcnapp/
├── common/          # 공통 설정 및 필터
├── user/            # 사용자 인증/인가
├── schedule/        # 일정 관리
├── trip/            # 여행 관리
└── depot/           # 파일 관리
```

### 계층 구조
- **Controller**: REST API 엔드포인트
- **Service**: 비즈니스 로직
- **Repository**: 데이터 액세스 (Spring Data JPA)
- **Domain**: 엔티티 클래스
- **DTO**: 데이터 전송 객체

### 장점
- 명확한 패키지 구조와 계층 분리
- Spring Data JPA를 활용한 간결한 Repository
- Swagger를 통한 API 문서화
- JWT 기반의 stateless 인증

### 개선 필요 영역
- 보안 설정 강화
- 트랜잭션 경계 명확화
- 예외 처리 일관성
- 테스트 커버리지 확대
- 로깅 전략 수립

---

## 보안 개선점

### 🔴 Critical (즉시 수정 필요)

#### 1. 민감 정보 노출
**문제**: `application.yml`에 DB 비밀번호, JWT 시크릿 키가 평문으로 노출
```yaml
# 현재 코드 (src/main/resources/application.yml)
spring:
  datasource:
    password: mysuni  # 평문 노출
  jwt:
    secretKey: Y2hsZGtkdWRyaGsgZGJkbGZybmpzZG1sIHRqZG5mY2hzc2hhIGR1Z29kZG1zIHJQdGhyZWhsc2VrLiBka3ZkbWZoZWggclB0aHI=
  data:
    redis:
      password: test  # 평문 노출
```

**개선 방안**:
```yaml
# 환경 변수 또는 외부 설정 사용
spring:
  datasource:
    password: ${DB_PASSWORD}
  jwt:
    secretKey: ${JWT_SECRET_KEY}
  data:
    redis:
      password: ${REDIS_PASSWORD}
```

**추가 조치**:
- Spring Cloud Config 또는 AWS Secrets Manager, HashiCorp Vault 등 사용 고려
- `.gitignore`에 `application-local.yml`, `application-prod.yml` 추가
- GitHub Secrets 사용 (CI/CD 환경)

**관련 파일**: `src/main/resources/application.yml`

---

#### 2. CORS 설정 과도한 허용
**문제**: 모든 origin에서의 요청을 허용
```java
// 현재 코드 (src/main/java/com/seoulchonnom/slcnapp/common/config/WebConfig.java:14)
.allowedOrigins("*")
.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
```

**개선 방안**:
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)  // 특정 도메인만 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)  // 쿠키 사용 시 필요
                .maxAge(3600);
    }
}
```

**application.yml 추가**:
```yaml
cors:
  allowed-origins:
    - https://your-frontend-domain.com
    - https://admin.your-domain.com
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/common/config/WebConfig.java:14`

---

#### 3. JWT 시크릿 키 보안 강화
**문제**: JWT 시크릿 키가 Base64 인코딩만 되어 있고, 충분히 복잡하지 않음

**개선 방안**:
```java
// JwtTokenProvider.java 개선
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${spring.jwt.secretKey}")
    private String key;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        // 최소 512비트 이상의 키 사용 검증
        byte[] keyBytes = Decoders.BASE64URL.decode(key);
        if (keyBytes.length < 64) {  // 512비트 = 64바이트
            throw new IllegalArgumentException("JWT secret key must be at least 512 bits");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // 토큰 유효시간을 설정에서 가져오도록 변경
    @Value("${jwt.access-token.expiration}")
    private long accessTokenValidTime;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenValidTime;
}
```

**시크릿 키 생성 가이드**:
```bash
# 안전한 512비트 키 생성
openssl rand -base64 64
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/user/JwtTokenProvider.java:25-38`

---

### 🟡 High Priority

#### 4. RefreshToken TTL 미설정
**문제**: Redis RefreshToken의 expiration이 설정되지 않아 자동 삭제 안됨
```java
// 현재 코드 (src/main/java/com/seoulchonnom/slcnapp/user/domain/RefreshToken.java:25)
@TimeToLive
private Long expiration;  // null로 저장됨
```

**개선 방안**:
```java
@RedisHash(value = "token", timeToLive = 1209600)  // 14일 (초 단위)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshToken {
    @Id
    private Integer id;

    @Indexed
    private String token;

    @TimeToLive
    private Long expiration;

    public static RefreshToken create(Integer userId, String token, long expirationSeconds) {
        return RefreshToken.builder()
                .id(userId)
                .token(token)
                .expiration(expirationSeconds)
                .build();
    }

    public void updateToken(String newToken) {
        this.token = newToken;
    }
}
```

**UserService 수정**:
```java
// src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:75
RefreshToken refreshToken = RefreshToken.create(
    user.getId(),
    token.getRefreshToken(),
    14 * 24 * 60 * 60L  // 14일
);
refreshTokenRepository.save(refreshToken);
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/user/domain/RefreshToken.java:25`
- `src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:75`

---

#### 5. 보안 헤더 추가
**문제**: SecurityConfiguration에 보안 헤더 설정이 없음

**개선 방안**:
```java
// SecurityConfiguration.java 수정
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'"))
            .frameOptions(frameOptions -> frameOptions.deny())
            .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .contentTypeOptions(contentType -> contentType.disable())
        )
        .sessionManagement(management ->
            management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests((authorizeRequests) -> authorizeRequests
            .requestMatchers("/swagger-ui/**", "/v3/**").permitAll()
            .requestMatchers("/user/login", "/user/token").permitAll()
            .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
            .requestMatchers("/user/register").hasAuthority("ADMIN")
            .anyRequest().hasAuthority("USER"))
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint(new CommonAuthenticationEntryPoint())
            .accessDeniedHandler(new CommonAccessDeniedHandler()))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtTokenProvider),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/common/config/SecurityConfiguration.java:28`

---

#### 6. SQL Injection 방지 확인
**현재 상태**: Spring Data JPA의 메서드 쿼리를 사용하여 안전함

**검증 필요**:
- 모든 쿼리가 PreparedStatement를 사용하는지 확인
- Native Query 사용 시 파라미터 바인딩 확인

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/schedule/repository/ScheduleRepository.java`
- `src/main/java/com/seoulchonnom/slcnapp/trip/repository/TripRepository.java`

---

#### 7. 파일 업로드 보안 강화
**문제**: 파일 확장자만 검증하고 실제 파일 타입은 검증하지 않음

**개선 방안**:
```java
// FileUtils.java 개선
@Component
public class FileUtils {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    @Value("${upload.path}")
    private String directory;

    @Value("${upload.max-file-size:5242880}")  // 5MB 기본값
    private long maxFileSize;

    private final String AVAILABLE_PATH = "logo|map";

    public String saveImages(MultipartFile multipartFile, String path) throws IOException {

        // 1. 경로 검증
        validatePath(path);

        // 2. 파일 크기 검증
        validateFileSize(multipartFile);

        // 3. 확장자 검증
        String originalFilename = multipartFile.getOriginalFilename();
        validateExtension(originalFilename);

        // 4. MIME 타입 검증 (실제 파일 타입)
        validateMimeType(multipartFile);

        // 5. 파일명 생성 및 저장
        String fileName = createSafeFileName(path, originalFilename);
        String saveFilePath = Paths.get(directory, fileName).toString();

        // 6. 디렉토리 생성
        Files.createDirectories(Paths.get(directory, path));

        // 7. 파일 저장
        multipartFile.transferTo(new File(saveFilePath));

        return fileName;
    }

    private void validatePath(String path) {
        if (path == null || path.isEmpty() || !path.matches(AVAILABLE_PATH)) {
            throw new FilePathInvalidException();
        }
        // Path Traversal 방지
        if (path.contains("..") || path.contains("/") || path.contains("\\")) {
            throw new FilePathInvalidException();
        }
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > maxFileSize) {
            throw new FileSizeException();
        }
    }

    private void validateExtension(String filename) {
        String ext = extractExt(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new FileExtException();
        }
    }

    private void validateMimeType(MultipartFile file) {
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new UnsupportedMediaTypeException();
        }

        // 추가: Magic Number 검증 (선택사항)
        try {
            byte[] header = new byte[12];
            file.getInputStream().read(header);
            if (!isValidImageHeader(header)) {
                throw new UnsupportedMediaTypeException();
            }
        } catch (IOException e) {
            throw new FileUploadException();
        }
    }

    private boolean isValidImageHeader(byte[] header) {
        // JPEG: FF D8 FF
        if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return true;
        }
        // PNG: 89 50 4E 47
        if (header[0] == (byte) 0x89 && header[1] == 0x50 &&
            header[2] == 0x4E && header[3] == 0x47) {
            return true;
        }
        // GIF: 47 49 46 38
        if (header[0] == 0x47 && header[1] == 0x49 &&
            header[2] == 0x46 && header[3] == 0x38) {
            return true;
        }
        return false;
    }

    private String createSafeFileName(String path, String originalFilename) {
        String ext = extractExt(originalFilename);
        String uuid = UUID.randomUUID().toString();
        // 안전한 파일명 생성 (특수문자 제거)
        return path + "/" + uuid + "." + ext.toLowerCase();
    }

    private String extractExt(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new FileExtException();
        }
        int pos = originalFilename.lastIndexOf(".");
        return originalFilename.substring(pos + 1);
    }
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/depot/util/FileUtils.java`

---

#### 8. 비밀번호 정책 강화
**문제**: 비밀번호 복잡성 검증이 없음

**개선 방안**:
```java
// UserService.java에 비밀번호 검증 추가
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );

    @Transactional
    public void registerUser(UserRegisterRequest userRegisterRequest) {
        // 비밀번호 검증
        validatePassword(userRegisterRequest.getPassword());

        // 사용자명 중복 검증
        if (userRepository.findByUsername(userRegisterRequest.getUserName()).isPresent()) {
            throw new DuplicateUsernameException();
        }

        User user = User.builder()
                .name(userRegisterRequest.getName())
                .username(userRegisterRequest.getUserName())
                .password(passwordEncoder.encode(userRegisterRequest.getPassword()))
                .loginFailCount(0)
                .build();

        userRepository.save(user);

        Authority authority = Authority.builder()
                .role(Role.USER)
                .user(user)
                .build();
        authorityRepository.save(authority);
    }

    private void validatePassword(String password) {
        if (password == null || !PASSWORD_PATTERN.matcher(password).matches()) {
            throw new InvalidPasswordException(
                "비밀번호는 최소 8자 이상이며, 대소문자, 숫자, 특수문자를 포함해야 합니다."
            );
        }
    }
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:47`

---

## 아키텍처 및 설계 개선점

### 🟡 High Priority

#### 1. 트랜잭션 경계 명확화
**문제**: 로그인 실패 카운트 업데이트가 트랜잭션 경계가 애매함

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:62`):
```java
@Transactional
public Token issueToken(UserLoginRequest userLoginRequest) {
    User user = userRepository.findByUsername(userLoginRequest.getUsername())
            .orElseThrow(InvalidUserException::new);

    // 실패 카운트 검증
    if (user.getLoginFailCount() >= LOGIN_FAIL_LIMIT_COUNT
            && Duration.between(user.getLastLoginFailTime(), LocalDateTime.now()).getSeconds()
            > LOGIN_LIMIT_CLEAR_TIME) {
        throw new UserLoginFailCountOverException();
    }

    if (passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword())) {
        // 성공 시 카운트 리셋
        user.resetLoginFailCount();
        // ... 토큰 발급
    } else {
        // 실패 시 카운트 증가
        user.updateLoginFailCount();
        return Token.builder().userId(LOGIN_ERROR_CODE).build();
    }
}
```

**문제점**:
- 비밀번호 불일치 시 예외를 던지지 않고 특수 값(-1)을 반환
- 실패 카운트 업데이트가 트랜잭션 내에서 처리되지만 명확하지 않음
- 실패 시에도 트랜잭션이 커밋됨

**개선 방안**:
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    @Transactional
    public Token issueToken(UserLoginRequest userLoginRequest) {
        User user = userRepository.findByUsername(userLoginRequest.getUsername())
                .orElseThrow(InvalidUserException::new);

        // 계정 잠김 검증
        validateAccountLock(user);

        // 비밀번호 검증
        if (!passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword())) {
            handleLoginFailure(user);
            throw new InvalidPasswordException();
        }

        // 로그인 성공 처리
        return handleLoginSuccess(user);
    }

    private void validateAccountLock(User user) {
        if (user.getLoginFailCount() >= LOGIN_FAIL_LIMIT_COUNT) {
            LocalDateTime lockTime = user.getLastLoginFailTime();
            long elapsedHours = Duration.between(lockTime, LocalDateTime.now()).toHours();

            if (elapsedHours < LOGIN_LIMIT_CLEAR_TIME) {
                throw new AccountLockedException(
                    String.format("계정이 잠겼습니다. %d시간 후에 다시 시도하세요.",
                        LOGIN_LIMIT_CLEAR_TIME - elapsedHours)
                );
            } else {
                // 잠금 시간이 지난 경우 카운트 초기화
                user.resetLoginFailCount();
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void handleLoginFailure(User user) {
        user.updateLoginFailCount();
        userRepository.save(user);

        if (user.getLoginFailCount() >= LOGIN_FAIL_LIMIT_COUNT) {
            // 로그 기록 또는 알림
            log.warn("User account locked: username={}, failCount={}",
                user.getUsername(), user.getLoginFailCount());
        }
    }

    private Token handleLoginSuccess(User user) {
        user.resetLoginFailCount();

        Token token = jwtTokenProvider.createToken(
            new UserDetail(user),
            user.getId()
        );

        RefreshToken refreshToken = RefreshToken.create(
            user.getId(),
            token.getRefreshToken(),
            REFRESH_TOKEN_EXPIRATION
        );
        refreshTokenRepository.save(refreshToken);

        return token;
    }
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:62`

---

#### 2. Entity에서 비즈니스 로직 분리
**문제**: Entity에 변환 로직과 비즈니스 로직이 혼재됨

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/schedule/domain/Schedule.java:71`):
```java
public static Schedule from(ScheduleRegisterRequest request) {
    String id = UUID.randomUUID().toString();
    return Schedule.builder()
            .id(id)
            .calendarId(request.getCalendarId())
            // ... 많은 필드 매핑
            .build();
}

public void modifyValues(ScheduleModifyRequest request) {
    BeanUtils.copyProperties(request, this, "start", "end");
    this.start = LocalDateTime.parse(request.getStart(), DATE_TIME_FORMATTER);
    this.end = LocalDateTime.parse(request.getEnd(), DATE_TIME_FORMATTER);
}
```

**개선 방안**: Mapper 클래스 도입
```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/schedule/mapper/ScheduleMapper.java
@Component
public class ScheduleMapper {

    public Schedule toEntity(ScheduleRegisterRequest request) {
        return Schedule.builder()
                .id(UUID.randomUUID().toString())
                .calendarId(request.getCalendarId())
                .title(request.getTitle())
                .body(request.getBody())
                .isAllDay(request.isAllday())
                .start(parseDateTime(request.getStart()))
                .end(parseDateTime(request.getEnd()))
                .goingDuration(request.getGoingDuration())
                .comingDuration(request.getComingDuration())
                .location(request.getLocation())
                .category(request.getCategory())
                .dueDateClass(request.getDueDateClass())
                .recurrenceRule(request.getRecurrenceRule())
                .state(request.getState())
                .isVisible(request.isVisible())
                .isPending(request.isPending())
                .isFocused(request.isFocused())
                .isReadOnly(request.isReadOnly())
                .isPrivate(request.isPrivate())
                .color(request.getColor())
                .backgroundColor(request.getBackgroundColor())
                .dragBackgroundColor(request.getDragBackgroundColor())
                .borderColor(request.getBorderColor())
                .customStyle(request.getCustomStyle())
                .build();
    }

    public void updateEntity(Schedule schedule, ScheduleModifyRequest request) {
        schedule.setCalendarId(request.getCalendarId());
        schedule.setTitle(request.getTitle());
        schedule.setBody(request.getBody());
        schedule.setIsAllDay(request.isAllday());
        schedule.setStart(parseDateTime(request.getStart()));
        schedule.setEnd(parseDateTime(request.getEnd()));
        // ... 나머지 필드 업데이트
    }

    public ScheduleResponse toResponse(Schedule schedule) {
        return ScheduleResponse.from(schedule);
    }

    private LocalDateTime parseDateTime(String dateTimeString) {
        return LocalDateTime.parse(dateTimeString, DATE_TIME_FORMATTER);
    }
}
```

**Schedule Entity 수정**:
```java
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Schedule {
    @Id
    private String id;

    @Column(nullable = false)
    private String calendarId;

    // ... 필드들

    // 비즈니스 로직만 남김
    public void hide() {
        this.isVisible = false;
    }

    public boolean isModifiable() {
        return !this.isReadOnly;
    }

    // 정적 팩토리 메서드 및 변환 로직 제거
}
```

**ScheduleService 수정**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final ScheduleMapper scheduleMapper;

    public String registerSchedule(ScheduleRegisterRequest request) {
        validateScheduleRequest(request);

        Schedule schedule = scheduleMapper.toEntity(request);
        scheduleRepository.save(schedule);

        return schedule.getId();
    }

    public void modifySchedule(ScheduleModifyRequest request) {
        validateScheduleRequest(request);

        Schedule schedule = scheduleRepository.findById(request.getId())
                .orElseThrow(ScheduleNotFoundException::new);

        if (!schedule.isModifiable()) {
            throw new ReadOnlyScheduleException();
        }

        scheduleMapper.updateEntity(schedule, request);
        scheduleRepository.save(schedule);
    }

    private void validateScheduleRequest(ScheduleRegisterRequest request) {
        LocalDateTime start = LocalDateTime.parse(request.getStart(), DATE_TIME_FORMATTER);
        LocalDateTime end = LocalDateTime.parse(request.getEnd(), DATE_TIME_FORMATTER);

        if (start.isAfter(end)) {
            throw new InvalidScheduleDateException("시작 시간이 종료 시간보다 늦습니다.");
        }
    }
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/schedule/domain/Schedule.java:71`
- `src/main/java/com/seoulchonnom/slcnapp/trip/dto/TripRegisterRequest.java:34`

---

#### 3. DTO 변환 로직 통합
**문제**: DTO에서 Entity로의 변환이 DTO 클래스에 위치함

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/trip/dto/TripRegisterRequest.java:34`):
```java
public Trip of() {
    return Trip.builder()
            .date(date)
            .type(type)
            // ... 필드 매핑
            .build();
}
```

**개선 방안**: Mapper 패턴 적용
```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/trip/mapper/TripMapper.java
@Component
public class TripMapper {

    public Trip toEntity(TripRegisterRequest request) {
        Trip trip = Trip.builder()
                .date(request.getDate())
                .type(request.getType())
                .info1(request.getInfo1())
                .info2(request.getInfo2())
                .logo(request.getLogo())
                .map1(request.getMap1())
                .map2(request.getMap2())
                .button1(request.getButton1())
                .button2(request.getButton2())
                .drive(request.getDrive())
                .quizTitle(request.getQuizTitle())
                .quizAnswer(request.getQuizAnswer())
                .quizAnswerTitle(request.getQuizAnswerTitle())
                .quizAnswerText(request.getQuizAnswerText())
                .quizErrorTitle(request.getQuizErrorTitle())
                .quizErrorText(request.getQuizErrorText())
                .build();

        // Quiz 리스트 설정
        List<Quiz> quizList = request.getQuizRegisterRequestList().stream()
                .map(quizRequest -> toQuizEntity(quizRequest, trip))
                .toList();
        trip.setQuizList(quizList);

        return trip;
    }

    private Quiz toQuizEntity(QuizRegisterRequest request, Trip trip) {
        return Quiz.builder()
                .trip(trip)
                .quizIndex(request.getQuizIndex())
                .answer(request.getAnswer())
                .build();
    }

    public TripInfoResponse toInfoResponse(Trip trip) {
        return TripInfoResponse.from(trip);
    }

    public TripListResponse toListResponse(Trip trip) {
        return TripListResponse.from(trip);
    }
}
```

**TripService 수정**:
```java
@Service
@RequiredArgsConstructor
@Transactional
public class TripService {
    private final TripRepository tripRepository;
    private final TripMapper tripMapper;
    private final DepotService depotService;

    public boolean registerTrip(TripRegisterRequest request) {
        // 유효성 검증
        validateTripRequest(request);

        // Entity 변환 및 저장
        Trip trip = tripMapper.toEntity(request);
        tripRepository.save(trip);

        return true;
    }

    public List<TripListResponse> getAllTripList() {
        return tripRepository.findAllByOrderByDateDesc().stream()
                .map(tripMapper::toListResponse)
                .collect(Collectors.toList());
    }

    public TripInfoResponse getTripByDate(String date) {
        Trip trip = tripRepository.findByDate(date)
                .orElseThrow(TripNotFoundException::new);
        return tripMapper.toInfoResponse(trip);
    }

    private void validateTripRequest(TripRegisterRequest request) {
        // 날짜 형식 검증
        if (!isValidDateFormat(request.getDate())) {
            throw new InvalidTripDateException();
        }

        // 중복 검증
        if (tripRepository.findByDate(request.getDate()).isPresent()) {
            throw new DuplicateTripDateException();
        }
    }

    private boolean isValidDateFormat(String date) {
        return date != null && date.matches("\\d{8}");  // YYYYMMDD
    }
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/trip/dto/TripRegisterRequest.java:34`

---

### 🟢 Medium Priority

#### 4. CascadeType 사용 최소화
**문제**: Authority와 User의 양방향 관계에서 CascadeType.ALL 사용

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/user/domain/Authority.java:20`):
```java
@ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
@JoinColumn(name = "user_id")
private User user;
```

**문제점**:
- Authority 저장 시 User도 함께 저장됨 (의도하지 않은 동작)
- Authority 삭제 시 User도 삭제될 수 있음 (위험)

**개선 방안**:
```java
@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Authority {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)  // cascade 제거
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Setter 제거 (불변 객체)
}
```

**User Entity 수정**:
```java
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // ... 기존 필드들

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<Authority> authorityList = new ArrayList<>();

    // 비즈니스 메서드
    public void addAuthority(Authority authority) {
        this.authorityList.add(authority);
    }

    public boolean hasRole(Role role) {
        return authorityList.stream()
                .anyMatch(auth -> auth.getRole() == role);
    }
}
```

**UserService 수정**:
```java
@Transactional
public void registerUser(UserRegisterRequest userRegisterRequest) {
    validatePassword(userRegisterRequest.getPassword());

    User user = User.builder()
            .name(userRegisterRequest.getName())
            .username(userRegisterRequest.getUserName())
            .password(passwordEncoder.encode(userRegisterRequest.getPassword()))
            .loginFailCount(0)
            .build();

    Authority authority = Authority.builder()
            .role(Role.USER)
            .user(user)
            .build();

    user.addAuthority(authority);

    // User 저장 시 Authority도 함께 저장됨 (cascade = PERSIST)
    userRepository.save(user);
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/user/domain/Authority.java:20`
- `src/main/java/com/seoulchonnom/slcnapp/user/domain/User.java:34`

---

#### 5. 서비스 계층 책임 분리
**문제**: UserService가 토큰 관리, 쿠키 관리, 사용자 관리를 모두 담당

**개선 방안**: 별도의 서비스로 분리

```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/user/service/TokenService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-token.expiration}")
    private long refreshTokenExpiration;

    @Transactional
    public Token issueToken(User user) {
        Token token = jwtTokenProvider.createToken(
            new UserDetail(user),
            user.getId()
        );

        saveRefreshToken(user.getId(), token.getRefreshToken());

        return token;
    }

    @Transactional
    public Token reissueToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = userRepository.findById(storedToken.getId())
                .orElseThrow(InvalidRefreshTokenException::new);

        Token newToken = jwtTokenProvider.createToken(
            new UserDetail(user),
            user.getId()
        );

        updateRefreshToken(storedToken, newToken.getRefreshToken());

        return newToken;
    }

    private void saveRefreshToken(Integer userId, String token) {
        RefreshToken refreshToken = RefreshToken.create(
            userId,
            token,
            refreshTokenExpiration
        );
        refreshTokenRepository.save(refreshToken);
    }

    private void updateRefreshToken(RefreshToken refreshToken, String newToken) {
        refreshToken.updateToken(newToken);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeToken(Integer userId) {
        refreshTokenRepository.deleteById(userId);
    }
}
```

```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/user/service/AuthenticationService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Value("${login.fail.limit.count}")
    private int loginFailLimitCount;

    @Value("${login.limit.clear.time}")
    private int loginLimitClearTime;

    @Transactional
    public Token authenticate(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(InvalidUserException::new);

        validateAccountLock(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleLoginFailure(user);
            throw new InvalidPasswordException();
        }

        return handleLoginSuccess(user);
    }

    private void validateAccountLock(User user) {
        if (user.getLoginFailCount() >= loginFailLimitCount) {
            LocalDateTime lockTime = user.getLastLoginFailTime();
            long elapsedHours = Duration.between(lockTime, LocalDateTime.now()).toHours();

            if (elapsedHours < loginLimitClearTime) {
                throw new AccountLockedException();
            } else {
                user.resetLoginFailCount();
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void handleLoginFailure(User user) {
        user.updateLoginFailCount();
        userRepository.save(user);
    }

    private Token handleLoginSuccess(User user) {
        user.resetLoginFailCount();
        return tokenService.issueToken(user);
    }
}
```

```java
// UserService 간소화
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registerUser(UserRegisterRequest request) {
        validatePassword(request.getPassword());
        validateDuplicateUsername(request.getUserName());

        User user = User.builder()
                .name(request.getName())
                .username(request.getUserName())
                .password(passwordEncoder.encode(request.getPassword()))
                .loginFailCount(0)
                .build();

        Authority authority = Authority.builder()
                .role(Role.USER)
                .user(user)
                .build();

        user.addAuthority(authority);
        userRepository.save(user);
    }

    public UserInfoResponse getUserInfo(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidUserException::new);
        return UserInfoResponse.of(user);
    }

    private void validatePassword(String password) {
        // 비밀번호 검증 로직
    }

    private void validateDuplicateUsername(String username) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new DuplicateUsernameException();
        }
    }
}
```

```java
// UserController 수정
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;
    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final CookieService cookieService;

    @PostMapping("/login")
    public ResponseEntity<BaseResponse> loginUser(
            HttpServletResponse response,
            @RequestBody UserLoginRequest request) {

        Token token = authenticationService.authenticate(request);
        cookieService.addRefreshTokenCookie(response, token.getRefreshToken());

        UserInfoResponse userInfo = userService.getUserInfo(token.getUserId());

        return ResponseEntity.ok(
            BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userInfo)
        );
    }

    @GetMapping("/token")
    public ResponseEntity<BaseResponse> reissueToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        Token token = tokenService.reissueToken(refreshToken);
        cookieService.addRefreshTokenCookie(response, token.getRefreshToken());

        UserInfoResponse userInfo = userService.getUserInfo(token.getUserId());

        return ResponseEntity.ok(
            BaseResponse.from(true, USER_LOGIN_SUCCESS_MESSAGE, userInfo)
        );
    }
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java`

---

## 데이터베이스 및 엔티티 개선점

### 🟡 High Priority

#### 1. 감사(Audit) 필드 추가
**문제**: 생성일시, 수정일시, 생성자, 수정자 정보가 없음

**개선 방안**:
```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/common/domain/BaseEntity.java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @LastModifiedBy
    private String modifiedBy;
}
```

```java
// JPA Auditing 설정
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.of("SYSTEM");
            }

            return Optional.of(authentication.getName());
        };
    }
}
```

**Entity 수정 예시**:
```java
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Schedule extends BaseEntity {  // BaseEntity 상속
    @Id
    private String id;

    // ... 기존 필드들
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/schedule/domain/Schedule.java`
- `src/main/java/com/seoulchonnom/slcnapp/trip/domain/Trip.java`
- `src/main/java/com/seoulchonnom/slcnapp/user/domain/User.java`

---

#### 2. ID 전략 통일
**문제**: Schedule은 UUID String, 나머지는 Integer Auto Increment 사용

**현재 상태**:
- Schedule: String (UUID)
- User, Trip, Quiz, Authority: Integer (Auto Increment)

**개선 방안**: UUID 전략 통일 고려
```java
// Schedule.java 수정
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Schedule extends BaseEntity {
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(columnDefinition = "VARCHAR(36)")
    private String id;

    // ... 나머지 필드
}
```

**또는 전체를 Long으로 통일**:
```java
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Schedule extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ... 나머지 필드
}
```

**권장사항**:
- 성능이 중요한 경우: Long (Auto Increment)
- 분산 시스템이나 보안이 중요한 경우: UUID
- 현재 프로젝트: Long 권장 (단순하고 성능 우수)

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/schedule/domain/Schedule.java:22`

---

#### 3. FetchType 최적화
**문제**: User의 authorityList가 EAGER 로딩으로 설정됨

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/user/domain/User.java:34`):
```java
@OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
private List<Authority> authorityList = new ArrayList<>();
```

**문제점**:
- N+1 문제 발생 가능
- 불필요한 데이터 로딩
- 성능 저하

**개선 방안**:
```java
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class User extends BaseEntity {
    // ... 기존 필드들

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private List<Authority> authorityList = new ArrayList<>();

    // ... 나머지 코드
}
```

**Repository에서 필요 시 Join Fetch 사용**:
```java
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM User u JOIN FETCH u.authorityList WHERE u.username = :username")
    Optional<User> findByUsernameWithAuthorities(@Param("username") String username);
}
```

**UserDetailService 수정**:
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(InvalidUserException::new);
        return new UserDetail(user);
    }
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/user/domain/User.java:34`

---

#### 4. Trip 엔티티 필드명 개선
**문제**: info1, info2, map1, map2 등 의미가 불명확한 필드명

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/trip/domain/Trip.java`):
```java
@Column(length = 10, nullable = false)
private String info1;

@Column(length = 30, nullable = false)
private String info2;

private String map1;
private String map2;
```

**개선 방안**:
```java
@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Getter
public class Trip extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 8, nullable = false)
    private String tripDate;  // date -> tripDate

    @Column(length = 1, nullable = false)
    @Enumerated(EnumType.STRING)
    private TripType tripType;  // type -> tripType, String -> Enum

    @Column(length = 10, nullable = false)
    private String locationCode;  // info1 -> locationCode

    @Column(length = 30, nullable = false)
    private String locationName;  // info2 -> locationName

    @Column(nullable = false)
    private String logoImagePath;  // logo -> logoImagePath

    @Column(nullable = false)
    private String mainMapImagePath;  // map1 -> mainMapImagePath

    private String detailMapImagePath;  // map2 -> detailMapImagePath

    @Column(length = 30)
    private String primaryButtonText;  // button1 -> primaryButtonText

    @Column(length = 30)
    private String secondaryButtonText;  // button2 -> secondaryButtonText

    @Column(nullable = false)
    private String driveUrl;  // drive -> driveUrl (더 명확)

    // Quiz 관련 필드들 (Embedded 객체로 분리 고려)
    @Embedded
    private TripQuizInfo quizInfo;

    @Builder.Default
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quiz> quizList = new ArrayList<>();
}
```

```java
// 새 파일: TripType Enum
public enum TripType {
    A("A 타입"),
    B("B 타입"),
    C("C 타입");

    private final String description;

    TripType(String description) {
        this.description = description;
    }
}
```

```java
// 새 파일: Quiz 정보를 Embedded 객체로 분리
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripQuizInfo {
    @Column(length = 50, nullable = false)
    private String quizTitle;

    @Column(length = 2, nullable = false)
    private String correctAnswer;

    @Column(length = 50, nullable = false)
    private String correctAnswerTitle;

    @Column(length = 50, nullable = false)
    private String correctAnswerText;

    @Column(length = 50, nullable = false)
    private String wrongAnswerTitle;  // quizErrorTitle -> wrongAnswerTitle

    @Column(length = 50, nullable = false)
    private String wrongAnswerText;  // quizErrorText -> wrongAnswerText
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/trip/domain/Trip.java`

---

#### 5. 인덱스 추가
**문제**: 자주 조회되는 컬럼에 인덱스가 없음

**개선 방안**:
```java
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_last_login_time", columnList = "lastLoginTime")
})
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class User extends BaseEntity {
    // ... 필드들
}

@Entity
@Table(name = "schedule", indexes = {
    @Index(name = "idx_start_visible", columnList = "start, isVisible"),
    @Index(name = "idx_calendar_id", columnList = "calendarId")
})
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Schedule extends BaseEntity {
    // ... 필드들
}

@Entity
@Table(name = "trip", indexes = {
    @Index(name = "idx_trip_date", columnList = "tripDate")
})
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Trip extends BaseEntity {
    // ... 필드들
}
```

**관련 파일**: 모든 Entity 클래스

---

## 코드 품질 개선점

### 🟢 Medium Priority

#### 1. 로깅 전략 수립
**문제**: 로깅이 거의 없음 (RedisConfig에만 존재)

**개선 방안**:
```java
// 각 Service 클래스에 로깅 추가
@Service
@RequiredArgsConstructor
@Slf4j  // Lombok의 @Slf4j 사용
@Transactional(readOnly = true)
public class UserService {

    @Transactional
    public void registerUser(UserRegisterRequest request) {
        log.info("User registration started: username={}", request.getUserName());

        try {
            validatePassword(request.getPassword());
            validateDuplicateUsername(request.getUserName());

            // ... 사용자 등록 로직

            log.info("User registration completed: username={}", request.getUserName());
        } catch (Exception e) {
            log.error("User registration failed: username={}, error={}",
                request.getUserName(), e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public Token authenticate(UserLoginRequest request) {
        log.debug("Authentication attempt: username={}", request.getUsername());

        try {
            // ... 인증 로직

            log.info("Authentication successful: username={}", request.getUsername());
            return token;
        } catch (InvalidPasswordException e) {
            log.warn("Authentication failed - invalid password: username={}",
                request.getUsername());
            throw e;
        } catch (AccountLockedException e) {
            log.warn("Authentication failed - account locked: username={}",
                request.getUsername());
            throw e;
        }
    }
}
```

**logback-spring.xml 추가**:
```xml
<!-- src/main/resources/logback-spring.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- File Appender -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/slcnapp.log</file>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/slcnapp.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
    </appender>

    <!-- Error File Appender -->
    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/error.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/error.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
    </appender>

    <!-- Logger 설정 -->
    <logger name="com.seoulchonnom.slcnapp" level="DEBUG"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.hibernate" level="INFO"/>
    <logger name="org.hibernate.SQL" level="DEBUG"/>
    <logger name="org.hibernate.type.descriptor.sql.BasicBinder" level="TRACE"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </root>
</configuration>
```

**관련 파일**: 모든 Service 클래스

---

#### 2. 예외 처리 일관성 개선
**문제**: 예외 클래스가 모듈별로 분산되어 있고, 메시지가 하드코딩됨

**개선 방안**:
```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/common/exception/ErrorCode.java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Common
    INVALID_INPUT_VALUE("CMN001", "입력 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("CMN002", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    // User
    USER_NOT_FOUND("USR001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_USERNAME("USR002", "이미 사용 중인 사용자명입니다.", HttpStatus.CONFLICT),
    INVALID_PASSWORD("USR003", "비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("USR004", "계정이 잠겼습니다.", HttpStatus.FORBIDDEN),
    INVALID_PASSWORD_FORMAT("USR005", "비밀번호 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),

    // Token
    INVALID_ACCESS_TOKEN("TKN001", "유효하지 않은 액세스 토큰입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("TKN002", "유효하지 않은 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN("TKN003", "만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),

    // Schedule
    SCHEDULE_NOT_FOUND("SCH001", "일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_SCHEDULE_DATE("SCH002", "일정 날짜가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    READONLY_SCHEDULE("SCH003", "읽기 전용 일정은 수정할 수 없습니다.", HttpStatus.FORBIDDEN),

    // Trip
    TRIP_NOT_FOUND("TRP001", "여행 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    DUPLICATE_TRIP_DATE("TRP002", "해당 날짜의 여행 정보가 이미 존재합니다.", HttpStatus.CONFLICT),

    // File
    FILE_UPLOAD_FAILED("FIL001", "파일 업로드에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_FILE_EXTENSION("FIL002", "허용되지 않는 파일 확장자입니다.", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("FIL003", "파일 크기가 제한을 초과했습니다.", HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_FILE_PATH("FIL004", "파일 경로가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MEDIA_TYPE("FIL005", "지원하지 않는 미디어 타입입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
```

```java
// 새 파일: src/main/java/com/seoulchonnom/slcnapp/common/exception/BusinessException.java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
```

```java
// ErrorResponse 개선
@Getter
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private boolean success;
    private String code;
    private String message;
    private LocalDateTime timestamp;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
```

```java
// CommonExceptionHandler 개선
@RestControllerAdvice
@Slf4j
public class CommonExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.error("BusinessException: code={}, message={}",
            e.getErrorCode().getCode(), e.getMessage());

        ErrorResponse response = ErrorResponse.of(e.getErrorCode());
        return new ResponseEntity<>(response, e.getErrorCode().getHttpStatus());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException e) {
        log.error("BadRequestException: {}", e.getMessage());
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.error("ValidationException: {}", e.getMessage());

        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));

        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, message);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unexpected exception occurred", e);
        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

**기존 예외 클래스 수정**:
```java
// InvalidUserException 수정
public class InvalidUserException extends BusinessException {
    public InvalidUserException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}

// ScheduleNotFoundException 수정
public class ScheduleNotFoundException extends BusinessException {
    public ScheduleNotFoundException() {
        super(ErrorCode.SCHEDULE_NOT_FOUND);
    }
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/common/handler/CommonExceptionHandler.java`
- 모든 예외 클래스

---

#### 3. 유효성 검증 강화
**문제**: DTO 필드에 유효성 검증 어노테이션이 없음

**개선 방안**:
```java
// build.gradle에 의존성 추가
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    // ... 기존 의존성
}
```

```java
// UserRegisterRequest 개선
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {

    @NotBlank(message = "사용자명은 필수입니다.")
    @Size(min = 4, max = 30, message = "사용자명은 4-30자여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "사용자명은 영문, 숫자, 언더스코어만 사용 가능합니다.")
    private String userName;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 2, max = 30, message = "이름은 2-30자여야 합니다.")
    private String name;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "비밀번호는 8자 이상이며, 대소문자, 숫자, 특수문자를 포함해야 합니다."
    )
    private String password;
}
```

```java
// ScheduleRegisterRequest 개선
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleRegisterRequest {

    @NotBlank(message = "캘린더 ID는 필수입니다.")
    private String calendarId;

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    @Size(max = 500, message = "내용은 500자 이하여야 합니다.")
    private String body;

    @NotNull(message = "시작 일시는 필수입니다.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
        message = "날짜 형식이 올바르지 않습니다. (yyyy-MM-dd HH:mm:ss)")
    private String start;

    @NotNull(message = "종료 일시는 필수입니다.")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
        message = "날짜 형식이 올바르지 않습니다. (yyyy-MM-dd HH:mm:ss)")
    private String end;

    @NotBlank(message = "장소는 필수입니다.")
    private String location;

    @NotNull(message = "카테고리는 필수입니다.")
    private ScheduleCategory category;

    @NotNull(message = "상태는 필수입니다.")
    private ScheduleState state;

    // ... 나머지 필드
}
```

```java
// Controller에서 @Valid 사용
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse> registerUser(
            @Valid @RequestBody UserRegisterRequest request) {  // @Valid 추가
        userService.registerUser(request);
        return ResponseEntity.ok(
            BaseResponse.from(true, USER_REGISTER_SUCCESS_MESSAGE)
        );
    }
}
```

**관련 파일**: 모든 DTO 클래스

---

#### 4. 상수 관리 개선
**문제**: 매직 넘버와 문자열이 코드에 산재됨

**개선 방안**:
```java
// 각 모듈의 Constant 클래스 개선
public class UserConstant {
    // Messages
    public static final String USER_REGISTER_SUCCESS_MESSAGE = "사용자 등록이 완료되었습니다.";
    public static final String USER_LOGIN_SUCCESS_MESSAGE = "로그인이 완료되었습니다.";

    // Validation
    public static final int USERNAME_MIN_LENGTH = 4;
    public static final int USERNAME_MAX_LENGTH = 30;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int NAME_MIN_LENGTH = 2;
    public static final int NAME_MAX_LENGTH = 30;

    // Token
    public static final long ACCESS_TOKEN_VALID_TIME = 30 * 60 * 1000L;  // 30분
    public static final long REFRESH_TOKEN_VALID_TIME = 14 * 24 * 60 * 60 * 1000L;  // 14일

    // Login Failure
    public static final int DEFAULT_LOGIN_FAIL_LIMIT_COUNT = 5;
    public static final int DEFAULT_LOGIN_LIMIT_CLEAR_TIME_HOURS = 24;

    private UserConstant() {
        throw new IllegalStateException("Constant class");
    }
}
```

```java
// FileConstant 개선
public class FileConstant {
    // File Size
    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;  // 5MB
    public static final long MAX_FILE_SIZE_MB = 5;

    // Allowed Extensions
    public static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "webp"
    );

    // Allowed MIME Types
    public static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    // Paths
    public static final String LOGO_PATH = "logo";
    public static final String MAP_PATH = "map";
    public static final Pattern VALID_PATH_PATTERN = Pattern.compile("logo|map");

    // Messages
    public static final String FILE_UPLOAD_SUCCESS_MESSAGE = "파일 업로드가 완료되었습니다.";

    private FileConstant() {
        throw new IllegalStateException("Constant class");
    }
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/user/UserConstant.java`
- `src/main/java/com/seoulchonnom/slcnapp/depot/DepotConstant.java`
- 기타 Constant 클래스

---

#### 5. 주석 처리된 코드 제거
**문제**: UserService에 주석 처리된 코드 존재

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:87-90`):
```java
//	@Transactional(propagation = Propagation.REQUIRES_NEW)
//    protected void updateLoginFailCount(User user) {
//		user.updateLoginFailCount();
//	}
```

**개선 방안**: 제거 또는 필요시 활성화

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/user/service/UserService.java:87-90`

---

## API 설계 개선점

### 🟢 Medium Priority

#### 1. HTTP 메서드 사용 개선
**문제**: PUT을 DELETE 용도로 사용

**현재 코드** (`src/main/java/com/seoulchonnom/slcnapp/schedule/controller/ScheduleController.java:59`):
```java
@PutMapping("/remove/{scheduleId}")
public ResponseEntity<BaseResponse> hideSchedule(@PathVariable String scheduleId)
```

**개선 방안**:
```java
// Soft Delete는 PATCH 사용
@PatchMapping("/{scheduleId}/visibility")
public ResponseEntity<BaseResponse> updateScheduleVisibility(
        @PathVariable String scheduleId,
        @RequestParam boolean visible) {
    if (visible) {
        scheduleService.showSchedule(scheduleId);
    } else {
        scheduleService.hideSchedule(scheduleId);
    }
    return ResponseEntity.ok(
        BaseResponse.from(true, "일정 노출 상태가 변경되었습니다.")
    );
}

// Hard Delete는 DELETE 사용
@DeleteMapping("/{scheduleId}")
public ResponseEntity<BaseResponse> deleteSchedule(@PathVariable String scheduleId) {
    scheduleService.deleteSchedule(scheduleId);
    return ResponseEntity.ok(
        BaseResponse.from(true, HARD_DELETE_SCHEDULE_COMPLETE_MESSAGE)
    );
}
```

**관련 파일**: `src/main/java/com/seoulchonnom/slcnapp/schedule/controller/ScheduleController.java:59`

---

#### 2. API 버전 관리
**개선 방안**:
```java
// application.yml에 API 버전 추가
server:
  servlet:
    context-path: /api/v1  # /api -> /api/v1
```

또는 Controller에서 버전 관리:
```java
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {
    // ... 컨트롤러 코드
}
```

---

#### 3. 페이지네이션 추가
**문제**: Trip 목록 조회 시 페이지네이션이 없음

**개선 방안**:
```java
// TripService 수정
@Transactional(readOnly = true)
public Page<TripListResponse> getAllTripList(Pageable pageable) {
    return tripRepository.findAllByOrderByDateDesc(pageable)
            .map(tripMapper::toListResponse);
}
```

```java
// TripRepository 수정
public interface TripRepository extends JpaRepository<Trip, Integer> {
    Page<Trip> findAllByOrderByDateDesc(Pageable pageable);
    Optional<Trip> findByDate(@NonNull String date);
}
```

```java
// TripController 수정
@GetMapping("/")
public ResponseEntity<BaseResponse> getTrips(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "date,desc") String[] sort) {

    Pageable pageable = PageRequest.of(page, size, Sort.by(
        sort[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
        sort[0]
    ));

    Page<TripListResponse> trips = tripService.getAllTripList(pageable);

    return ResponseEntity.ok(
        BaseResponse.from(true, RETRIEVE_TRIP_LIST_SUCCESS_MESSAGE, trips)
    );
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/trip/service/TripService.java:26`
- `src/main/java/com/seoulchonnom/slcnapp/trip/controller/TripController.java:19`

---

#### 4. 파일 업로드와 Trip 등록 통합
**문제**: 파일 업로드와 Trip 등록이 분리되어 트랜잭션 관리 어려움

**개선 방안**:
```java
// TripController에 멀티파트 요청 처리 추가
@PostMapping(value = "/", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<BaseResponse> createTrip(
        @RequestPart("trip") TripRegisterRequest tripRequest,
        @RequestPart(value = "logo", required = false) MultipartFile logoFile,
        @RequestPart(value = "map1", required = false) MultipartFile map1File,
        @RequestPart(value = "map2", required = false) MultipartFile map2File) {

    boolean result = tripService.registerTripWithFiles(
        tripRequest, logoFile, map1File, map2File
    );

    return ResponseEntity.ok(
        BaseResponse.from(true, REGISTER_TRIP_SUCCESS_MESSAGE, result)
    );
}
```

```java
// TripService 수정
@Transactional
public boolean registerTripWithFiles(
        TripRegisterRequest request,
        MultipartFile logoFile,
        MultipartFile map1File,
        MultipartFile map2File) {

    try {
        // 파일 업로드
        if (logoFile != null && !logoFile.isEmpty()) {
            String logoPath = depotService.uploadFile(logoFile, "logo");
            request.setLogo(logoPath);
        }

        if (map1File != null && !map1File.isEmpty()) {
            String map1Path = depotService.uploadFile(map1File, "map");
            request.setMap1(map1Path);
        }

        if (map2File != null && !map2File.isEmpty()) {
            String map2Path = depotService.uploadFile(map2File, "map");
            request.setMap2(map2Path);
        }

        // Trip 등록
        Trip trip = tripMapper.toEntity(request);
        tripRepository.save(trip);

        return true;
    } catch (Exception e) {
        log.error("Failed to register trip with files", e);
        throw new TripRegistrationException();
    }
}
```

**관련 파일**:
- `src/main/java/com/seoulchonnom/slcnapp/trip/controller/TripController.java`
- `src/main/java/com/seoulchonnom/slcnapp/trip/service/TripService.java`

---

## 테스트 및 품질 보증

### 🔴 Critical

#### 1. 단위 테스트 작성
**문제**: 테스트 코드가 거의 없음

**개선 방안**:
```java
// UserService 테스트 예시
@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("사용자 등록 성공")
    void registerUser_Success() {
        // given
        UserRegisterRequest request = new UserRegisterRequest(
            "testuser", "Test User", "Test1234!"
        );

        // when
        userService.registerUser(request);

        // then
        User savedUser = userRepository.findByUsername("testuser").orElseThrow();
        assertThat(savedUser.getUsername()).isEqualTo("testuser");
        assertThat(savedUser.getName()).isEqualTo("Test User");
        assertThat(passwordEncoder.matches("Test1234!", savedUser.getPassword())).isTrue();
        assertThat(savedUser.getAuthorityList()).hasSize(1);
        assertThat(savedUser.getAuthorityList().get(0).getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("중복된 사용자명으로 등록 시 예외 발생")
    void registerUser_DuplicateUsername_ThrowsException() {
        // given
        userService.registerUser(new UserRegisterRequest(
            "testuser", "Test User 1", "Test1234!"
        ));

        // when & then
        assertThatThrownBy(() ->
            userService.registerUser(new UserRegisterRequest(
                "testuser", "Test User 2", "Test1234!"
            ))
        ).isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    @DisplayName("비밀번호 형식이 올바르지 않으면 예외 발생")
    void registerUser_InvalidPassword_ThrowsException() {
        // given
        UserRegisterRequest request = new UserRegisterRequest(
            "testuser", "Test User", "weak"
        );

        // when & then
        assertThatThrownBy(() -> userService.registerUser(request))
            .isInstanceOf(InvalidPasswordException.class);
    }
}
```

```java
// ScheduleService 테스트 예시
@SpringBootTest
@Transactional
class ScheduleServiceTest {

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("일정 등록 성공")
    void registerSchedule_Success() {
        // given
        ScheduleRegisterRequest request = createScheduleRequest();

        // when
        String scheduleId = scheduleService.registerSchedule(request);

        // then
        Schedule savedSchedule = scheduleRepository.findById(scheduleId).orElseThrow();
        assertThat(savedSchedule.getTitle()).isEqualTo("Test Schedule");
        assertThat(savedSchedule.isVisible()).isTrue();
    }

    @Test
    @DisplayName("시작 시간이 종료 시간보다 늦으면 예외 발생")
    void registerSchedule_InvalidDate_ThrowsException() {
        // given
        ScheduleRegisterRequest request = createScheduleRequest();
        request.setStart("2025-03-01 10:00:00");
        request.setEnd("2025-03-01 09:00:00");  // 시작보다 이른 시간

        // when & then
        assertThatThrownBy(() -> scheduleService.registerSchedule(request))
            .isInstanceOf(InvalidScheduleDateException.class);
    }

    private ScheduleRegisterRequest createScheduleRequest() {
        ScheduleRegisterRequest request = new ScheduleRegisterRequest();
        request.setCalendarId("cal1");
        request.setTitle("Test Schedule");
        request.setBody("Test Body");
        request.setIsAllday(false);
        request.setStart("2025-03-01 09:00:00");
        request.setEnd("2025-03-01 10:00:00");
        request.setLocation("Test Location");
        request.setCategory(ScheduleCategory.time);
        request.setState(ScheduleState.Busy);
        request.setIsVisible(true);
        return request;
    }
}
```

---

#### 2. 통합 테스트 작성
**개선 방안**:
```java
// UserController 통합 테스트
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("사용자 등록 API 테스트")
    void registerUser() throws Exception {
        // given
        UserRegisterRequest request = new UserRegisterRequest(
            "testuser", "Test User", "Test1234!"
        );

        // when & then
        mockMvc.perform(post("/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(USER_REGISTER_SUCCESS_MESSAGE));
    }

    @Test
    @DisplayName("로그인 API 테스트")
    void loginUser() throws Exception {
        // given
        // 먼저 사용자 등록
        UserRegisterRequest registerRequest = new UserRegisterRequest(
            "testuser", "Test User", "Test1234!"
        );
        mockMvc.perform(post("/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // 로그인 요청
        UserLoginRequest loginRequest = new UserLoginRequest("testuser", "Test1234!");

        // when & then
        mockMvc.perform(post("/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"));
    }
}
```

---

#### 3. 테스트 커버리지 설정
**개선 방안**:
```gradle
// build.gradle에 JaCoCo 플러그인 추가
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

test {
    useJUnitPlatform()
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}

jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.70  // 70% 커버리지 목표
            }
        }
    }
}
```

---

## 성능 최적화 방안

### 🟢 Medium Priority

#### 1. 쿼리 최적화
**개선 방안**:
```java
// ScheduleRepository에 Join Fetch 추가
public interface ScheduleRepository extends JpaRepository<Schedule, String> {

    @Query("SELECT s FROM Schedule s WHERE s.start BETWEEN :startDateAfter AND :startDateBefore AND s.isVisible = :isVisible")
    List<Schedule> findAllByStartBetweenAndIsVisible(
        @Param("startDateAfter") LocalDateTime startDateAfter,
        @Param("startDateBefore") LocalDateTime startDateBefore,
        @Param("isVisible") boolean isVisible
    );
}
```

---

#### 2. 캐싱 전략
**개선 방안**:
```java
// Redis 캐싱 설정
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .serializeKeysWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(
                    RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

```java
// Service에 캐싱 적용
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripService {

    @Cacheable(value = "trips", key = "#date")
    public TripInfoResponse getTripByDate(String date) {
        Trip trip = tripRepository.findByDate(date)
                .orElseThrow(TripNotFoundException::new);
        return tripMapper.toInfoResponse(trip);
    }

    @CacheEvict(value = "trips", allEntries = true)
    @Transactional
    public boolean registerTrip(TripRegisterRequest request) {
        // ... 등록 로직
    }
}
```

---

#### 3. Connection Pool 설정
**개선 방안**:
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  data:
    redis:
      lettuce:
        pool:
          max-active: 10
          max-idle: 10
          min-idle: 2
```

---

## 우선순위별 개선 로드맵

### Phase 1: 즉시 수정 (1-2주)
1. 민감 정보 환경 변수 처리
2. CORS 설정 개선
3. JWT 시크릿 키 강화
4. RefreshToken TTL 설정
5. 파일 업로드 보안 강화

### Phase 2: 단기 개선 (1개월)
1. Mapper 패턴 도입 (Entity <-> DTO 분리)
2. 트랜잭션 경계 명확화
3. 예외 처리 일관성 개선 (ErrorCode 도입)
4. 로깅 전략 수립
5. 유효성 검증 강화 (@Valid)
6. 감사 필드 추가 (BaseEntity)

### Phase 3: 중기 개선 (2-3개월)
1. 단위 테스트 작성 (커버리지 70% 목표)
2. 통합 테스트 작성
3. FetchType 최적화
4. 인덱스 추가
5. 페이지네이션 구현
6. API 버전 관리

### Phase 4: 장기 개선 (3-6개월)
1. 캐싱 전략 구현
2. 성능 모니터링 도구 도입
3. CI/CD 파이프라인 구축
4. 문서화 강화
5. 보안 감사 및 penetration testing

---

## 결론

이 문서에서 도출된 개선점들을 단계적으로 적용하면 다음과 같은 효과를 기대할 수 있습니다:

1. **보안 강화**: 민감 정보 보호, 인증/인가 개선, 파일 업로드 보안
2. **코드 품질 향상**: 유지보수성, 가독성, 테스트 가능성 개선
3. **성능 최적화**: 쿼리 최적화, 캐싱, Connection Pool 튜닝
4. **안정성 증가**: 예외 처리, 로깅, 모니터링 강화
5. **확장성 확보**: 계층 분리, 모듈화, API 버전 관리

우선순위를 고려하여 단계적으로 개선 작업을 진행하는 것을 권장합니다.
