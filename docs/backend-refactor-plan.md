# Backend Refactor Progress

## Goal
Java 17 + Spring Boot + JPA backend refactor, test, and API E2E verification.

## Current Status — 완료 (2026-07-05)
- [x] Baseline 확보
- [x] Explore 완료
- [x] Plan 확정
- [x] Implement (Batch 1~3)
- [x] Compile / Unit Test / Integration (132 tests 0 failures)
- [x] Context Load (실 인프라 bootRun)
- [x] API E2E (전 구간 500 없음)
- [x] Build/Package (check + bootJar)
- [x] Fresh Review — APPROVE, blocking 0건
- [x] Final Report (아래 및 대화 보고)

## Fresh Review 결과 (Gate 7)
- 판정: APPROVE. 변경 1~9 전부 승인, GET /api/trips 400은 pre-existing 확정 (이번 diff 미접촉)
- 비차단 MEDIUM 2건 → 후속 과제:
  - M-1: TripLogic.requireQuiz의 TripNotFoundException 의미 부정확 (QuizNotFound 계열 신설 고려)
  - M-2: JPA+Mongo 혼합 영속성의 단일 트랜잭션 미보장 (pre-existing 아키텍처 이슈)

## 확정 계획 (Scope)
- Batch 1 (정리성, 동작 중립): G3 FileLogic @Component 제거 / G1 JwtAuthenticationFilter PUBLIC_AUTH_PATHS 복수형 수정 + JwtAuthenticationFilterTest 신규 / N2 SecurityConfiguration exceptionHandling 단일 호출 병합 / N3 JwtTokenProvider WARN 로그 username 마스킹
- Batch 2 (JPA/방어): R1 UserStore 쓰기 메서드 @Transactional 오버라이드(호출 경로 검증 포함) / G6 RefreshSessionStore parseLong 방어 + 테스트 / G10 TripLogic quiz null 방어 + 테스트 / G2 IdGeneratorLogic 오버플로우·파싱 방어 + 테스트 (전파 변경 REQUIRES_NEW 불채택 — 롤백 의미 보존)
- Batch 3: G7-a UserLogin 시간 주입 순수화 + UserLoginTest 신규 (UserAuthLogic 호출부 동시 수정)
- Batch 4: G11 @SpringBootTest context load 테스트 + API E2E 검증 전략 수립·실행

## Out of Scope → 후속 과제
- 1.1/1.2 Facade HTTP 타입·Swagger 분리 (전 Facade 표준이라 ROI 없음, module.md 패턴 유지)
- G7-b Authority 시간 순수화 (registeredTime 저장값 회귀 확신 부족)
- G8 Resource→Logic 직접 호출 통일 (module.md 허용 패턴)
- G9 FileAssetRdo.from → FileMapper 분리
- H1 cookie.secure 기본값 true (운영/개발 환경 결정 필요 — 제품 의사결정)
- N4 Swagger permitAll 프로파일 제한, N5 잠금 중 실패 카운터, N6 SameSite Strict (인증 정책 인접)
- R2 TripLogic getAllTripList N+1 (Mongo 배치 조회 설계 필요)
- R5/R6 OSIV(open-in-view) 명시 설정 (운영 프로파일 변경)
- [ ] Implement
- [ ] Compile
- [ ] Unit Test
- [ ] Integration Test
- [ ] API E2E
- [ ] Build/Package
- [ ] Fresh Review
- [ ] Final Report

## Baseline (변경 전 기준점)
- Branch: `refactor`, HEAD: `d7c3016620cb8bfb147ce5fb1ad19fe46c2d63bef`, working tree clean
- Build tool: Gradle wrapper (Gradle 멀티모듈)
- Modules: slcn-spec ← slcn-aggregate ← slcn-auth ← slcn-rest ← slcn-boot
- Java toolchain: 17 (로컬 JDK는 Zulu 21, toolchain으로 17 사용)
- Spring Boot: 3.5.12, jjwt 0.12.7, springdoc 2.8.15, Lombok + MapStruct
- Baseline test: `./gradlew test` → **BUILD SUCCESSFUL** (2026-07-04)
  - slcn-aggregate/auth/rest/spec 테스트 통과, slcn-boot 테스트 없음(NO-SOURCE)

## Key Inputs
- `need_to_refactor.md` (2026-01-04, 구 `ddd` 브랜치 기준) — 항목별 현재 유효성 검증 필요 (stale 가능성 높음)
- `docs/architecture.md`, `docs/module.md` — 프로젝트 자체 레이어 규칙 (충돌 시 우선)
- `docs/learning/` — 과거 학습 문서

## Explore 결과 요약 (backend-explorer)
- 모듈 의존성 방향 정상: spec ← aggregate ← auth ← rest ← boot
- 엔드포인트 29개 (/users, /calendars, /schedule, /trips, /travels, /assets)
- JPA: @OneToMany/@ManyToOne 없음. UserJpo만 @ElementCollection(LAZY). JSON 컨버터로 중첩 구조 처리
- 테스트: 전부 순수 단위/Mockito 테스트. @SpringBootTest/@DataJpaTest 없음 (G11)
- need_to_refactor.md 검증: 유효 항목 = 1.1(Facade HTTP 타입), 1.2(spec Swagger), 1.4/G7(spec 엔티티 System.currentTimeMillis), 3.2(ControllerDocs 미분리)
  - 2.1/2.3/3.1은 현 docs/module.md 아키텍처 결정으로 대체됨 (JPO 상속 X, Resource가 Facade 구현이 정식 패턴)
  - 2.2(EAGER), 4.1(컴파일 에러), 4.2(new 핸들러)는 이미 수정됨
- 추가 후보: G1(JwtAuthenticationFilter stale 경로), G2(IdGeneratorLogic 동시성, High), G3(FileLogic 중복 어노테이션), G4(파일 전체 메모리 로드), G5(FileUtils 견고성), G6(RefreshSessionStore parseLong), G8(Resource→Logic 직접 호출 비일관), G9(FileAssetRdo.from), G10(Trip quiz null), G11(context load 테스트 부재)
- Do-not-touch: JPO 컬럼 매핑, SecurityConfiguration 인가 규칙, JwtTokenProvider 클레임, Redis 키 포맷, BusinessException/ErrorCode, DomainEntity 계열 베이스 클래스

## Decisions
- (2026-07-04) need_to_refactor.md는 stale로 간주하고 backend-explorer가 항목별 재검증 후 범위 결정
- (2026-07-04) JPO 상속 구조 변경(구 2.1)과 aggregate FacadeImpl(구 2.3)은 채택하지 않음 — docs/module.md가 정식 패턴

## Modified Files
### Batch 1 (완료, 전체 테스트 green)
- slcn-aggregate/.../file/logic/FileLogic.java — G3: @Component 제거, @Service만 유지
- slcn-auth/.../filter/JwtAuthenticationFilter.java — G1: PUBLIC_AUTH_PATHS 복수형(/users/*) 수정
- slcn-auth/src/test/.../filter/JwtAuthenticationFilterTest.java — 신규 (shouldNotFilter 공개/보호 경로, 토큰 없음 시 체인 통과 + SecurityContext 비어있음)
- slcn-boot/.../config/SecurityConfiguration.java — N2: exceptionHandling 단일 호출 병합 (인가 규칙 불변)
- slcn-auth/.../util/JwtTokenProvider.java — N3: describeClaims username 마스킹 (첫 글자+***)

### Batch 2 (완료, 전체 테스트 green)
- slcn-aggregate/.../user/store/UserStore.java — R1: save/initializeUserLogin/saveUserLogin/saveUserLoginHistory에 메서드 레벨 @Transactional (UserAuthStore 경로가 readOnly tx로 Mongo 쓰기하던 문제 해소)
- slcn-aggregate/.../calendar/store/CalendarStore.java — R1: save/delete @Transactional (동일 패턴; Schedule/Trip/Travel/FileBox/FileAsset Store는 해당 패턴 없음 확인)
- slcn-auth/.../store/RefreshSessionStore.java — G6: parseEpochMillis 방어 헬퍼, 손상 값 → InvalidRefreshTokenException(기존 ErrorCode), @Slf4j
- slcn-auth/src/test/.../store/RefreshSessionStoreTest.java — 신규 4개 테스트
- slcn-aggregate/.../trip/logic/TripLogic.java — G10: requireQuiz null 방어 → TripNotFoundException(기존), getTripQuiz/checkTripQuizAnswer 적용
- slcn-aggregate/.../trip/logic/TripLogicTest.java — quiz null 테스트 2개 추가
- slcn-aggregate/.../common/generator/IdGeneratorLogic.java — G2: 0xFFFF 오버플로우 가드(BadRequestException "ID CAPACITY EXCEEDED"), %04x 패딩 통일(≤0xFFFF 출력 동일 검증). 락/전파 불변
- slcn-aggregate/.../common/generator/IdGeneratorLogicTest.java — ffff 초과·패딩 회귀 테스트 추가

### Batch 3 (완료, 전체 테스트 green)
- slcn-spec/.../user/entity/UserLogin.java — G7-a: markLoginFailure(long now)/markLoginSuccess(long now)/isLoginBlockExpired(..., long now)로 시간 주입 (직접 시그니처 변경, 호출부 각 1곳)
- slcn-auth/.../logic/UserAuthLogic.java — 호출부 3곳에서 System.currentTimeMillis() 전달 (기존 시각 샘플링 시점 비트 단위 보존)
- slcn-spec/src/test/.../entity/UserLoginTest.java — 신규 10개 테스트 (고정 now, 만료 경계 3종, 실패 카운트/리셋)

## Verification Evidence
- Baseline: `./gradlew test` BUILD SUCCESSFUL in 9s (18 actionable tasks)
- Batch 1 후: `./gradlew :slcn-aggregate:test :slcn-auth:test :slcn-boot:compileJava` PASS, `./gradlew test` PASS (JwtAuthenticationFilterTest 실행 확인)
- Batch 2 후: `:slcn-aggregate:test` PASS, `:slcn-auth:test` PASS, `./gradlew test` 전체 PASS
- Batch 3 후: `:slcn-spec:test :slcn-auth:test` PASS, `./gradlew test` 전체 PASS (UserLoginTest 10/10)
- Gate 1~2: `./gradlew clean compileJava` PASS, `./gradlew test` PASS — 132 tests 0 failures (spec 30 / aggregate 52 / auth 18 / rest 32)
- Gate 3 (Persistence): 단위 테스트 + E2E 실 DB 쓰기/읽기(회원가입·로그인·파일업로드) PASS
- Gate 4 (Context Load): dev 프로파일 + 실 PG/Mongo/Redis로 bootRun 정상 기동 PASS
- Gate 5 (API E2E, 로컬 docker dev 인프라): 무인증 401 / 로그인 성공 / 로그인 실패 3회(500 없음) / 토큰 재발급 / 로그아웃 후 재발급 4xx / calendars 200 / schedule/now 200 / trips/quiz 200 / 없는 trip 400 / travels 200 / validation 400 / 파일 업로드 200×2 — 전 구간 500 없음
  - 특이사항: GET /api/trips 400 "FileBox를 찾을 수 없습니다" — 리팩토링 무관, legacy trip의 FileBox mongo 문서 미이관(사전 존재 데이터 문제). 후속 과제
  - 테스트 데이터 정리 완료: PG user/authority 2행, Mongo user_login_history 4 + file_asset 2, Redis 세션 1키, 업로드 파일 2개 삭제. 앱 프로세스 종료, docker 무접촉
- Gate 6 (Build/Package): `./gradlew check` PASS (exit 0), bootJar 생성 확인 (slcn-boot.jar 76MB)

## Reviewer 검토 필요 항목 (기록)
- G1은 의도된 미세 동작 변화: /users/login|token|logout에서 JWT 필터가 더 이상 실행되지 않음 (기존에는 stale 경로 탓에 실행됨). 세 엔드포인트 모두 permitAll이고 principal 의존 없음 확인됨 — fresh reviewer가 재확인할 것

## Remaining Risks
- ddl-auto=update 활성 → JPA 매핑 변경은 스키마에 영향 가능, 신중히
- slcn-boot 테스트 부재 → SecurityConfiguration 등 context load 검증 게이트 필요
