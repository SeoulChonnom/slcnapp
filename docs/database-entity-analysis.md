# 데이터베이스 엔티티 분석 및 개선 방안

## 목차

1. [엔티티 개요](#엔티티-개요)
2. [엔티티 상세 분석](#엔티티-상세-분석)
3. [주요 개선 사항](#주요-개선-사항)
4. [우선순위별 개선 로드맵](#우선순위별-개선-로드맵)

---

## 엔티티 개요

### JPA 엔티티 (MySQL)

1. **User** - 사용자 정보
2. **Authority** - 사용자 권한
3. **Schedule** - 일정 관리
4. **Trip** - 여행 정보
5. **Quiz** - 퀴즈 정보

### Redis 엔티티

1. **RefreshToken** - JWT 리프레시 토큰

### Enum 타입

1. **Role** - 사용자 역할 (USER, ADMIN)
2. **ScheduleState** - 일정 상태 (Busy, Free)
3. **ScheduleCategory** - 일정 카테고리 (milestone, task, allday, time)

---

## 엔티티 상세 분석

### 1. User 엔티티

**파일**: `src/main/java/com/seoulchonnom/slcnapp/user/domain/User.java`

#### 현재 구조

```java
@Entity
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 30, nullable = false, unique = true)
    private String username;

    @Column(length = 30, nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    private List<Authority> authorityList = new ArrayList<>();

    private LocalDateTime lastLoginTime;
    @ColumnDefault("0")
    private Integer loginFailCount;
    private LocalDateTime lastLoginFailTime;
}
```

#### 문제점

1. **EAGER 로딩으로 인한 성능 문제**
    - `authorityList`가 EAGER 로딩되어 불필요한 쿼리 발생
    - N+1 문제 가능성

2. **password 컬럼 길이 제한 없음**
    - BCrypt 등의 암호화 알고리즘은 고정 길이(60자) 필요
    - 현재 TEXT 타입으로 저장되어 성능 저하 가능

3. **loginFailCount 초기화 문제**
    - `@ColumnDefault("0")` 사용하지만 JPA에서 항상 동작하지 않음
    - `@Builder.Default` 또는 생성자에서 초기화 필요

4. **Audit 필드 부재**
    - 생성일시, 수정일시, 생성자, 수정자 정보 없음
    - 데이터 추적 불가능

5. **ID 타입**
    - Integer 사용 시 최대 2,147,483,647 제한
    - Long 타입 권장

#### 개선 방안

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Integer → Long

    @Column(length = 30, nullable = false, unique = true)
    @Index(name = "idx_username")  // 인덱스 추가
    private String username;

    @Column(length = 30, nullable = false)
    private String name;

    @Column(length = 60, nullable = false)  // BCrypt 길이
    private String password;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)  // EAGER → LAZY
    private List<Authority> authorityList = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginTime;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "int default 0")
    private Integer loginFailCount = 0;  // 기본값 설정

    private LocalDateTime lastLoginFailTime;
}
```

---

### 2. Authority 엔티티

**파일**: `src/main/java/com/seoulchonnom/slcnapp/user/domain/Authority.java`

#### 현재 구조

```java
@Entity
public class Authority {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User user;
}
```

#### 문제점

1. **양방향 CascadeType.ALL 문제**
    - User와 Authority 양쪽에 CascadeType.ALL 설정
    - 순환 참조 및 의도하지 않은 삭제 가능성

2. **유니크 제약조건 부재**
    - 동일 사용자에게 동일 Role 중복 부여 가능
    - `(user_id, role)` 복합 유니크 제약조건 필요

3. **ID 타입**
    - Integer → Long 권장

#### 개선 방안

```java
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role"})  // 복합 유니크
})
public class Authority {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Integer → Long

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)  // CascadeType 제거
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
```

---

### 3. RefreshToken 엔티티

**파일**: `src/main/java/com/seoulchonnom/slcnapp/user/domain/RefreshToken.java`

#### 현재 구조

```java
@RedisHash("token")
public class RefreshToken {
    @Id
    private Integer id;  // User ID

    @Indexed
    private String token;

    @TimeToLive
    private Long expiration;
}
```

#### 문제점

1. **ID 타입 불일치**
    - User의 ID가 Integer이지만, 확장성을 위해 Long 권장

2. **보안 고려사항**
    - 토큰 값 자체만 저장하고 있음
    - 발급 시각, IP 주소 등 추가 정보 고려 가능

#### 개선 방안

```java
@RedisHash("token")
public class RefreshToken {
    @Id
    private Long id;  // Integer → Long

    @Indexed
    private String token;

    @TimeToLive
    private Long expiration;

    // 선택적 추가 필드
    private String issuedIp;  // 발급 IP
    private LocalDateTime issuedAt;  // 발급 시각
}
```

---

### 4. Schedule 엔티티

**파일**: `src/main/java/com/seoulchonnom/slcnapp/schedule/domain/Schedule.java`

#### 현재 구조

```java
@Entity
public class Schedule {
    @Id
    private String id;  // UUID

    @Column(nullable = false)
    private String calendarId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private boolean isAllDay;

    @Column(nullable = false)
    private LocalDateTime start;

    @Column(nullable = false)
    private LocalDateTime end;

    // ... 많은 필드들
}
```

#### 문제점

1. **UUID String 사용**
    - String 타입 UUID는 인덱스 성능 저하
    - Binary(16) 또는 CHAR(36) 명시적 지정 권장
    - 또는 IDENTITY 전략으로 변경 고려

2. **과도한 nullable = false**
    - 모든 컬럼이 nullable = false로 설정
    - 선택적 필드(goingDuration, comingDuration 등)는 nullable 허용 필요

3. **Audit 필드 부재**
    - 생성/수정 일시 추적 불가

4. **Soft Delete 불완전**
    - isVisible 필드로 soft delete 구현
    - deletedAt 필드 추가 권장

5. **인덱스 부재**
    - start, end 날짜 범위 검색 시 인덱스 필요
    - calendarId로 필터링 시 인덱스 필요

6. **연관관계 부재**
    - calendarId가 String으로만 존재
    - Calendar 엔티티 생성 및 ManyToOne 관계 고려

#### 개선 방안

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(indexes = {
    @Index(name = "idx_calendar_id", columnList = "calendarId"),
    @Index(name = "idx_start_end", columnList = "start, end"),
    @Index(name = "idx_visible_deleted", columnList = "isVisible, deletedAt")
})
public class Schedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // UUID String → Long

    @Column(nullable = false, length = 50)
    private String calendarId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean isAllDay;

    @Column(nullable = false)
    private LocalDateTime start;

    @Column(nullable = false)
    private LocalDateTime end;

    // nullable 허용
    private Long goingDuration;
    private Long comingDuration;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScheduleState state;

    // Soft delete
    @Column(nullable = false)
    private boolean isVisible = true;

    private LocalDateTime deletedAt;

    // Audit
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 기타 필드들...
}
```

---

### 5. Trip 엔티티

**파일**: `src/main/java/com/seoulchonnom/slcnapp/trip/domain/Trip.java`

#### 현재 구조

```java
@Entity
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 8, nullable = false)
    private String date;  // YYYYMMDD 형식

    @Column(length = 1, nullable = false)
    private String type;

    @Column(length = 10, nullable = false)
    private String info1;

    @Column(length = 30, nullable = false)
    private String info2;

    @Column(nullable = false)
    private String logo;

    @Column(nullable = false)
    private String map1;

    @Setter
    private String map2;

    // Quiz 관련 필드들...
    @Column(length = 50, nullable = false)
    private String quizTitle;

    @Column(length = 2, nullable = false)
    private String quizAnswer;

    // ... 더 많은 quiz 필드들

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL)
    private List<Quiz> quizList = new ArrayList<>();
}
```

#### 문제점

1. **date를 String으로 저장**
    - String(8) 대신 LocalDate 타입 사용 권장
    - 날짜 범위 검색 시 성능 및 정확성 향상

2. **type을 String(1)로 저장**
    - Enum 타입 사용 권장 (TripType enum 생성)

3. **불명확한 컬럼명**
    - info1, info2, button1, button2 등 의미 파악 어려움
    - 명확한 네이밍 필요 (예: region, description, linkUrl1, linkText1)

4. **Quiz 관련 필드 중복**
    - quizTitle, quizAnswer 등이 Trip에 직접 저장
    - Quiz 엔티티와 역할 중복
    - Quiz 관련 필드는 Quiz 엔티티로 이동하거나 제거 고려

5. **파일 경로 저장**
    - logo, map1, map2가 파일 경로를 String으로 저장
    - 별도 File/Attachment 엔티티 생성 고려

6. **ID 타입**
    - Integer → Long 권장

7. **Audit 필드 부재**

8. **인덱스 부재**
    - date 필드에 인덱스 필요

#### 개선 방안

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(indexes = {
    @Index(name = "idx_trip_date", columnList = "tripDate")
})
public class Trip {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Integer → Long

    @Column(nullable = false)
    private LocalDate tripDate;  // String → LocalDate

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripType type;  // Enum 사용

    // 명확한 네이밍
    @Column(length = 50, nullable = false)
    private String region;  // info1

    @Column(length = 200, nullable = false)
    private String description;  // info2

    // 파일 관리
    @Column(nullable = false)
    private String logoPath;

    @Column(nullable = false)
    private String mapPath1;

    private String mapPath2;

    // 링크 정보
    @Column(length = 200)
    private String linkUrl1;

    @Column(length = 50)
    private String linkText1;  // button1

    @Column(length = 200)
    private String linkUrl2;

    @Column(length = 50)
    private String linkText2;  // button2

    @Column(nullable = false, length = 500)
    private String driveInfo;

    // Quiz 관련 필드는 제거하고 Quiz 엔티티만 사용
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quiz> quizList = new ArrayList<>();

    // Audit
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}

// 새로운 Enum
public enum TripType {
    A, B, C, D  // 실제 타입에 맞게 수정
}
```

---

### 6. Quiz 엔티티

**파일**: `src/main/java/com/seoulchonnom/slcnapp/trip/domain/Quiz.java`

#### 현재 구조

```java
@Entity
public class Quiz {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(length = 2, nullable = false)
    private String quizIndex;

    @Column(length = 10, nullable = false)
    private String answer;
}
```

#### 문제점

1. **quizIndex를 String으로 저장**
    - Integer 타입이 더 적합
    - 정렬 및 비교 성능 향상

2. **제약조건 부재**
    - (trip_id, quizIndex) 복합 유니크 제약조건 필요

3. **퀴즈 정보 부족**
    - 퀴즈 제목, 설명, 정답/오답 메시지 등이 Trip에 있음
    - Quiz 엔티티로 통합 필요

4. **ID 타입**
    - Integer → Long 권장

5. **Audit 필드 부재**

#### 개선 방안

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "quizIndex"})
})
public class Quiz {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Integer → Long

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private Integer quizIndex;  // String → Integer

    @Column(length = 100, nullable = false)
    private String title;  // Trip의 quizTitle 이동

    @Column(length = 50, nullable = false)
    private String correctAnswer;

    @Column(length = 500, nullable = false)
    private String question;

    // Trip에서 이동한 필드들
    @Column(length = 100)
    private String correctAnswerTitle;

    @Column(length = 200)
    private String correctAnswerText;

    @Column(length = 100)
    private String wrongAnswerTitle;

    @Column(length = 200)
    private String wrongAnswerText;

    // Audit
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

---

## 주요 개선 사항

### 1. 성능 개선

#### 1.1 인덱스 추가

```java
// User 엔티티
@Table(indexes = {
    @Index(name = "idx_username", columnList = "username")
})

// Schedule 엔티티
@Table(indexes = {
    @Index(name = "idx_calendar_id", columnList = "calendarId"),
    @Index(name = "idx_start_end", columnList = "start, end"),
    @Index(name = "idx_visible_deleted", columnList = "isVisible, deletedAt")
})

// Trip 엔티티
@Table(indexes = {
    @Index(name = "idx_trip_date", columnList = "tripDate")
})
```

#### 1.2 EAGER → LAZY 로딩 변경

```java
// User.java - Before
@OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
private List<Authority> authorityList;

// User.java - After
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
private List<Authority> authorityList;

// 필요한 경우 @EntityGraph 또는 fetch join 사용
@EntityGraph(attributePaths = {"authorityList"})
User findByUsername(String username);
```

#### 1.3 UUID 성능 개선

```java
// Schedule.java - Before
@Id
private String id;  // UUID.randomUUID().toString()

// Schedule.java - After (Option 1: Long ID 사용)
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

// Schedule.java - After (Option 2: UUID 유지 시)
@Id
@GeneratedValue(generator = "uuid2")
@GenericGenerator(name = "uuid2", strategy = "uuid2")
@Column(columnDefinition = "BINARY(16)")
private UUID id;
```

---

### 2. 데이터 무결성 개선

#### 2.1 유니크 제약조건 추가

```java
// Authority.java
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role"})
})

// Quiz.java
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"trip_id", "quizIndex"})
})
```

#### 2.2 외래키 제약조건 강화

```java
// Authority.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_authority_user"))
private User user;

// Quiz.java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "fk_quiz_trip"))
private Trip trip;
```

#### 2.3 컬럼 길이 및 타입 명시

```java
// User.java
@Column(length = 60, nullable = false)  // BCrypt 암호화 고려
private String password;

// Schedule.java
@Column(length = 200, nullable = false)
private String title;

@Column(columnDefinition = "TEXT", nullable = false)
private String body;
```

---

### 3. Audit 기능 추가

#### 3.1 BaseEntity 생성

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

#### 3.2 각 엔티티에 적용

```java
@Entity
public class User extends BaseEntity {
    // ...
}

@Entity
public class Schedule extends BaseEntity {
    // ...
}

@Entity
public class Trip extends BaseEntity {
    // ...
}

@Entity
public class Quiz extends BaseEntity {
    // ...
}
```

#### 3.3 Spring Boot 설정

```java
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
```

---

### 4. Soft Delete 패턴 통일

#### 4.1 SoftDeletableEntity 생성

```java
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {
    @Column(nullable = false)
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    public void delete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
    }
}
```

#### 4.2 적용

```java
// Schedule.java - Before
private boolean isVisible;

public void hideSchedule() {
    this.isVisible = false;
}

// Schedule.java - After
@Entity
public class Schedule extends SoftDeletableEntity {
    // isVisible 제거, deleted/deletedAt 상속
}
```

---

### 5. 타입 안정성 개선

#### 5.1 String → Enum 변환

```java
// Trip.java - Before
@Column(length = 1, nullable = false)
private String type;

// Trip.java - After
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private TripType type;

public enum TripType {
    WALKING("도보"),
    BUS("버스"),
    SUBWAY("지하철"),
    CAR("자동차");

    private final String description;

    TripType(String description) {
        this.description = description;
    }
}
```

#### 5.2 String 날짜 → LocalDate 변환

```java
// Trip.java - Before
@Column(length = 8, nullable = false)
private String date;  // "20240101"

// Trip.java - After
@Column(nullable = false)
private LocalDate tripDate;
```

#### 5.3 ID 타입 통일 (Integer → Long)

```java
// 모든 엔티티
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;  // Integer → Long
```

---

### 6. CascadeType 재검토

#### 6.1 양방향 관계 Cascade 조정

```java
// User.java
@OneToMany(mappedBy = "user", fetch = FetchType.LAZY,
           cascade = {CascadeType.PERSIST, CascadeType.MERGE})
private List<Authority> authorityList;

// Authority.java
@ManyToOne(fetch = FetchType.LAZY)  // CascadeType 제거
@JoinColumn(name = "user_id", nullable = false)
private User user;
```

#### 6.2 orphanRemoval 추가

```java
// Trip.java
@OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Quiz> quizList;
```

---

### 7. 네이밍 개선

#### 7.1 불명확한 컬럼명 개선

```java
// Trip.java - Before
private String info1;
private String info2;
private String button1;
private String button2;

// Trip.java - After
private String region;
private String description;
private String linkText1;
private String linkUrl1;
private String linkText2;
private String linkUrl2;
```

---

## 우선순위별 개선 로드맵

### 우선순위 1: 즉시 개선 (Critical)

#### 1.1 성능 개선

- [ ] User.authorityList EAGER → LAZY 변경
- [ ] 주요 검색 컬럼에 인덱스 추가
    - User.username
    - Schedule.calendarId, start, end
    - Trip.tripDate

#### 1.2 데이터 무결성

- [ ] Authority (user_id, role) 복합 유니크 제약조건
- [ ] Quiz (trip_id, quizIndex) 복합 유니크 제약조건
- [ ] User.password 컬럼 길이 60으로 제한

#### 1.3 버그 수정

- [ ] User.loginFailCount 기본값 처리
    - @ColumnDefault 제거
    - @Builder.Default 또는 생성자 초기화

### 우선순위 2: 단기 개선 (High)

#### 2.1 Audit 기능 추가

- [ ] BaseEntity 생성 (createdAt, updatedAt)
- [ ] @EnableJpaAuditing 설정
- [ ] 모든 엔티티에 BaseEntity 상속

#### 2.2 ID 타입 통일

- [ ] 모든 엔티티 ID를 Integer → Long 변경
    - User, Authority, Trip, Quiz, RefreshToken

#### 2.3 타입 안정성

- [ ] Trip.date String → LocalDate 변환
- [ ] Trip.type String → TripType enum 변환
- [ ] Quiz.quizIndex String → Integer 변환

### 우선순위 3: 중기 개선 (Medium)

#### 3.1 Soft Delete 패턴 통일

- [ ] SoftDeletableEntity 생성
- [ ] Schedule의 isVisible → deleted 패턴 변경
- [ ] 다른 엔티티에도 soft delete 적용 고려

#### 3.2 엔티티 구조 개선

- [ ] Trip의 Quiz 관련 필드를 Quiz 엔티티로 이동
- [ ] 불명확한 컬럼명 개선 (info1, info2, button1, button2)
- [ ] CascadeType 재검토 및 조정

#### 3.3 연관관계 개선

- [ ] Schedule.calendarId를 Calendar 엔티티 연관관계로 변경 고려
- [ ] File/Attachment 엔티티 분리 고려 (Trip.logo, map1, map2)

### 우선순위 4: 장기 개선 (Low)

#### 4.1 보안 강화

- [ ] RefreshToken에 발급 IP, 발급 시각 등 추가 정보 저장
- [ ] 사용자별 동시 로그인 세션 관리

#### 4.2 확장성

- [ ] Schedule UUID 사용 시 Binary(16) 타입으로 최적화
- [ ] 다국어 지원을 위한 구조 검토

#### 4.3 모니터링

- [ ] 생성자/수정자 정보 추가 (@CreatedBy, @LastModifiedBy)
- [ ] 엔티티 변경 이력 추적 (Audit Table)

---

## 마이그레이션 전략

### 1. 개발 환경 먼저 적용

1. 로컬/개발 DB에서 테스트
2. 기존 데이터 마이그레이션 스크립트 작성
3. 롤백 계획 수립

### 2. 단계적 배포

```sql
-- Phase 1: 인덱스 추가 (다운타임 없음)
CREATE INDEX idx_username ON user(username);
CREATE INDEX idx_trip_date ON trip(trip_date);

-- Phase 2: 제약조건 추가
ALTER TABLE authority
ADD CONSTRAINT uk_authority_user_role UNIQUE (user_id, role);

-- Phase 3: 컬럼 추가
ALTER TABLE user ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE user ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- Phase 4: 데이터 타입 변경 (주의 필요)
ALTER TABLE user MODIFY COLUMN id BIGINT AUTO_INCREMENT;
ALTER TABLE trip MODIFY COLUMN trip_date DATE;
```

### 3. 백업 및 검증

- 마이그레이션 전 DB 백업
- 마이그레이션 후 데이터 검증 쿼리 실행
- 애플리케이션 통합 테스트

---

## 참고 사항

### JPA 설정 권장사항

```properties
# application.properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.show_sql=false
spring.jpa.properties.hibernate.use_sql_comments=true
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

### 성능 모니터링

- 쿼리 로그 분석
- N+1 문제 감지
- 슬로우 쿼리 모니터링
- Connection Pool 설정 최적화

### 테스트 전략

- 엔티티 매핑 테스트
- 연관관계 테스트
- 제약조건 위반 테스트
- 성능 테스트 (JMH, JUnit Benchmark)

---

## 결론

현재 데이터베이스 구조는 기본적인 기능은 잘 구현되어 있으나, 성능, 데이터 무결성, 유지보수성 측면에서 개선이 필요합니다.

**핵심 개선 사항**:

1. EAGER 로딩을 LAZY로 변경하여 N+1 문제 방지
2. 적절한 인덱스 추가로 조회 성능 향상
3. 유니크 제약조건 추가로 데이터 무결성 보장
4. Audit 기능으로 데이터 추적성 확보
5. ID 타입 통일 및 타입 안정성 개선

우선순위에 따라 단계적으로 개선을 진행하면, 안정적이고 확장 가능한 데이터베이스 구조를 구축할 수 있습니다.
