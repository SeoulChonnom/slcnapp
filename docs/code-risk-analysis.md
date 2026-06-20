# 코드 위험 분석 문서

> 이 문서는 코드 검토 시점의 위험 항목과 수정 시 API 스펙 변경 가능성을 정리한 분석 문서이다.
> 일부 항목은 이후 코드 수정으로 개선될 수 있으므로, 실제 운영 반영 여부는 최신 구현과 테스트를 함께 확인한다.

## 분석 범위

- 기준 코드: 현재 워크스페이스의 `slcnapp` 소스
- 참고 문서: `docs/architecture.md`, `docs/module.md`, `docs/learning/domain-entity-must-not-depend-on-api-dto.md`
- 관점:
	- 잠재적 오류 발생 가능성이 있는 함수
	- 운영환경에서 장애, 보안, 데이터 무결성 문제로 이어질 수 있는 함수

## 요약

| 우선순위 | 함수 | 주요 위험 | 운영 영향 |
| --- | --- | --- | --- |
| P1 | `IdGeneratorLogic.nextDomainId()` / `nextId()` | 동시 ID 발급 경쟁 조건 | 중복 PK, 등록 실패, 데이터 정합성 훼손 |
| P1 | `FileUtils.saveImages()` | 저장 디렉터리 미보장, 확장자 기반 검증 | 업로드 실패, 악성/비이미지 파일 저장 |
| P1 | `FileLogic.getImageFile()` | 파일 전체 메모리 적재 | 대용량/동시 요청 시 메모리 압박 |
| P1 | `RefreshSessionStore.findBySessionId()` | Redis 값 파싱 예외 미처리 | 토큰 재발급 요청이 500으로 실패 |
| P1 | `JwtTokenProvider.init()` | JWT 환경값 오류 시 부팅 실패 | 운영 배포 즉시 애플리케이션 기동 실패 |
| P2 | `UserLogic.registerUser()` | username 중복 검증/제약 부재 | 중복 계정, 로그인 대상 불명확 |
| P2 | `UserLogin.isLoginBlocked()` / `UserAuthLogic.issueLoginToken()` | 잠금 해제 정책 미적용 | 계정 영구 잠금 가능 |
| P2 | `SecurityConfiguration.filterChain()` | 보안 정책 TODO 및 공개 경로 하드코딩 | 운영 API 접근 정책 오작동 가능 |
| P2 | `JwtAuthenticationFilter.doFilterInternal()` | 인증 객체 생성 예외 전파 | 정상 401 대신 500 가능 |
| P2 | `CalendarLogic.deleteCalendar()` | 참조 일정 존재 시 캘린더 하드 삭제 | 일정 고아 데이터 발생 가능 |
| P2 | `TripLogic.getTripQuiz()` / `checkTripQuizAnswer()` | quiz null 방어 없음 | 기존/오염 데이터 조회 시 500 가능 |

## 잠재적 오류 발생 가능성이 있는 함수

### 1. `IdGeneratorLogic.nextDomainId()` / `nextId()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/common/generator/IdGeneratorLogic.java:20`, `:26`
- 문제:
	- `id_sequence` 레코드를 조회한 뒤 `lastId`를 메모리에서 증가시키지만, 비관적 잠금/낙관적 잠금/DB sequence가 없다.
	- `lastId`가 null이거나 16진수 형식이 아니면 `NumberFormatException`이 발생한다.
	- `int` 기반 파싱이라 값이 커지면 오버플로 가능성이 있다.
- 발생 조건:
	- 캘린더/여행/사용자 등록 요청이 동시에 들어오는 경우
	- 운영 DB의 `id_sequence.lastId`가 수동 변경되거나 초기 데이터가 누락된 경우
- 영향:
	- 동일 ID 발급으로 저장 실패 또는 데이터 덮어쓰기 위험
	- 예외가 `BusinessException`으로 변환되지 않아 500 응답 가능
- 권장 조치:
	- DB sequence, `@Version` 낙관적 잠금, 또는 `PESSIMISTIC_WRITE` 조회로 변경한다.
	- `lastId` 형식 검증과 초기 데이터 검증을 애플리케이션 기동/마이그레이션 단계에 둔다.

### 2. `FileUtils.saveImages()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/util/FileUtils.java:24`
- 문제:
	- 저장 경로를 `directory + type + '/' + filename` 문자열로 결합한다.
	- `logo`, `map` 하위 디렉터리가 없으면 `transferTo()`가 실패한다.
	- `multipartFile.getOriginalFilename()`이 null이면 `extractExt()`에서 `NullPointerException`이 발생한다.
	- 파일 확장자만 검사하고 실제 MIME type, 파일 signature 검증이 없다.
	- 확장자 정규식이 소문자만 허용하므로 `PNG`, `JPG`는 실패한다.
- 발생 조건:
	- 배포 직후 업로드 디렉터리 미생성
	- 클라이언트가 원본 파일명 없이 multipart 전송
	- 확장자를 위장한 비이미지 파일 업로드
- 영향:
	- 정상 업로드가 400/500으로 실패
	- 정적 파일 저장소에 비정상 파일이 저장될 수 있음
- 권장 조치:
	- `Path base = Paths.get(directory).toAbsolutePath().normalize()` 기준으로 `resolve()`를 사용한다.
	- 저장 전 `Files.createDirectories(base.resolve(type))`를 호출한다.
	- 원본 파일명 null/빈값을 명시적으로 검증한다.
	- 확장자, content type, image decoder 기반 검증을 함께 적용한다.

### 3. `FileLogic.getImageFile()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileLogic.java:38`
- 문제:
	- `Files.readAllBytes(filePath)`로 파일 전체를 byte 배열에 적재한다.
	- 파일 존재 여부, 일반 파일 여부, 실제 base directory 내부 경로 여부를 별도로 확인하지 않는다.
	- `Files.probeContentType()` 결과가 잘못되거나 null일 수 있다.
- 발생 조건:
	- 큰 파일이 저장소에 존재하거나 동시 다운로드가 증가하는 경우
	- 운영 저장소 파일이 수동 교체되어 크기 제한을 우회한 경우
- 영향:
	- JVM heap 사용량 급증, GC 지연, OOM 가능
	- 파일 조회 API 지연
- 권장 조치:
	- `Resource`, `InputStreamResource`, `StreamingResponseBody` 기반 streaming 응답으로 변경한다.
	- 파일 크기 상한과 `Files.isRegularFile()` 검증을 조회 시에도 적용한다.
	- `normalize()` 후 base path 하위인지 확인한다.

### 4. `RefreshSessionStore.findBySessionId()`

- 위치: `slcn-auth/src/main/java/com/seoulchonnom/auth/store/RefreshSessionStore.java:36`
- 문제:
	- Redis hash 값의 `issuedAt`, `expiresAt`을 `Long.parseLong()`으로 바로 변환한다.
	- Redis 데이터가 손상되거나 다른 타입으로 저장되면 `NumberFormatException`이 발생한다.
	- `sessionId`가 빈 문자열이어도 `auth:refresh:` 키를 조회할 수 있다.
- 발생 조건:
	- Redis 데이터 수동 수정, 직렬화 정책 변경, 장애 복구 중 부분 데이터 발생
	- 쿠키가 비정상 값으로 들어오는 경우
- 영향:
	- 토큰 재발급 실패가 `InvalidRefreshTokenException`이 아니라 500으로 처리될 수 있음
- 권장 조치:
	- 숫자 파싱 예외를 잡아 `Optional.empty()` 또는 명시적 인증 예외로 변환한다.
	- `sessionId` blank 검증을 store 계층에도 추가한다.

### 5. `JwtTokenProvider.init()`

- 위치: `slcn-auth/src/main/java/com/seoulchonnom/auth/util/JwtTokenProvider.java:71`
- 문제:
	- `SLCN_JWT_SECRETKEY`를 Base64URL로 decode하고 HMAC key로 생성한다.
	- 값이 Base64URL 형식이 아니거나 알고리즘별 최소 key 길이보다 짧으면 부팅 중 예외가 발생한다.
	- `spring.jwt.algorithm` 값에 공백이 포함되면 `resolveMacAlgorithm()`에서 실패한다.
- 발생 조건:
	- 운영 환경변수 오입력
	- secret rotation 중 잘못된 값 배포
- 영향:
	- 애플리케이션 기동 실패
- 권장 조치:
	- 환경변수 검증 가이드를 배포 문서에 명시한다.
	- `configuredAlgorithm.trim().toUpperCase()` 처리 및 명확한 에러 로그를 추가한다.
	- 운영 배포 전 설정 검증 테스트를 추가한다.

### 6. `JwtAuthenticationFilter.doFilterInternal()`

- 위치: `slcn-auth/src/main/java/com/seoulchonnom/auth/filter/JwtAuthenticationFilter.java:32`
- 문제:
	- access token 자체가 유효하면 `getAuthentication()`에서 사용자 조회를 수행한다.
	- 토큰 subject/username에 해당하는 사용자가 삭제되었거나 DB 장애가 발생하면 예외가 필터 밖으로 전파될 수 있다.
- 발생 조건:
	- 사용자 삭제 후 기존 access token 사용
	- DB 일시 장애
- 영향:
	- 인증 실패가 401이 아니라 500으로 보일 수 있음
- 권장 조치:
	- 필터에서 인증 객체 생성 실패를 잡아 `SecurityContextHolder.clearContext()` 후 인증 실패 경로로 전달한다.
	- 삭제/비활성 사용자 정책을 명확히 둔다.

### 7. `TripLogic.getTripQuiz()` / `checkTripQuizAnswer()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/trip/logic/TripLogic.java:54`, `:58`
- 문제:
	- `tripStore.findById(tripId).getQuiz()`가 null인 경우를 방어하지 않는다.
	- 신규 등록은 quiz 필수 검증이 있지만 기존 데이터, 직접 DB 수정, 마이그레이션 데이터에는 적용되지 않는다.
- 발생 조건:
	- 과거 데이터에 quiz가 null
	- JSON converter 실패/부분 데이터
- 영향:
	- 퀴즈 조회/정답 확인 API가 500으로 실패 가능
- 권장 조치:
	- 조회 시 quiz null을 도메인 예외로 변환한다.
	- DB 데이터 보정 스크립트 또는 NOT NULL 정책을 검토한다.

### 8. `QuizConverter.convertToEntityAttribute()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/trip/store/jpo/converter/QuizConverter.java:18`
- 문제:
	- DB의 quiz JSON이 깨져 있으면 `JsonUtil.fromJson()`이 `IllegalArgumentException`을 던진다.
	- JPA attribute converter 예외는 조회 전체 실패로 이어진다.
- 발생 조건:
	- 운영 DB JSON 수동 수정
	- 이전 버전과 호환되지 않는 quiz JSON 저장
- 영향:
	- 여행 목록/상세 조회 중 특정 row 때문에 전체 요청 실패 가능
- 권장 조치:
	- DB 제약 또는 마이그레이션으로 JSON 형식을 관리한다.
	- converter 예외 로그와 데이터 보정 절차를 마련한다.

### 9. `FileReference.fromPath()`

- 위치: `slcn-spec/src/main/java/com/seoulchonnom/spec/file/entity/vo/FileReference.java:14`
- 문제:
	- `path.split("/", 2)` 결과 길이를 확인하지 않고 `parts[1]`에 접근한다.
	- `FileType.from(parts[0])`의 `IllegalArgumentException`이 그대로 전파된다.
- 발생 조건:
	- DB에 `logo/filename` 형식이 아닌 파일 경로 저장
- 영향:
	- Trip 매핑/조회 시 500 가능
- 권장 조치:
	- split 결과 길이와 filename blank 여부를 검증하고 명시적 도메인 예외로 변환한다.

### 10. `CommonExceptionHandler.exception()`

- 위치: `slcn-rest/src/main/java/com/seoulchonnom/rest/common/handler/CommonExceptionHandler.java:66`
- 문제:
	- 모든 미처리 예외를 동일한 500 응답으로 변환한다.
	- 클라이언트 입력 오류에서 발생한 `IllegalArgumentException`, `NumberFormatException`도 500으로 처리될 수 있다.
- 발생 조건:
	- 위 함수들의 null, parse, converter 예외
- 영향:
	- 실제 원인이 400/401이어도 운영 모니터링에는 서버 장애로 집계됨
- 권장 조치:
	- 대표적인 입력 예외를 별도 handler로 분리한다.
	- 내부 오류와 사용자 입력 오류를 구분해 로그 레벨/응답 코드를 조정한다.

## 운영환경에서 문제가 발생할 수 있는 함수

### 1. `SecurityConfiguration.filterChain()`

- 위치: `slcn-boot/src/main/java/com/seoulchonnom/boot/common/config/SecurityConfiguration.java:29`
- 문제:
	- 코드에 `TODO: 보안 로직 수정 필요`가 남아 있다.
	- `/user/login`, `/user/token`, `/user/logout`은 permitAll이고, 그 외는 단순 authority 기반이다.
	- `/user/register`는 `ADMIN` 권한만 허용하지만 초기 관리자 생성 경로가 코드상 명확하지 않다.
- 운영 영향:
	- 최초 운영 세팅에서 사용자 생성이 막힐 수 있다.
	- API별 세부 권한 정책이 늘어날 때 실수로 과도한 접근을 허용/차단할 수 있다.
- 권장 조치:
	- 운영 권한 매트릭스를 문서화하고 테스트로 고정한다.
	- 초기 관리자 생성 절차를 별도로 정의한다.

### 2. `UserResource.buildCookie()`

- 위치: `slcn-rest/src/main/java/com/seoulchonnom/rest/user/UserResource.java:99`
- 문제:
	- 기본 설정에서 `cookie.secure=false`, `sameSite=Lax`이다.
	- refresh token과 session id가 모두 쿠키에 저장된다.
- 운영 영향:
	- HTTPS 운영 환경에서 Secure 미설정 시 보안 수준 저하
	- 프론트엔드 도메인/배포 구조에 따라 쿠키 전송이 예상과 다르게 동작 가능
- 권장 조치:
	- 운영 profile에서는 `cookie.secure=true`를 강제한다.
	- SameSite 정책을 프론트 배포 도메인과 함께 검증한다.

### 3. `UserLogic.registerUser()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/user/logic/UserLogic.java:27`
- 문제:
	- username 중복 검증이 없다.
	- `UserJpo.username`에 unique constraint가 보이지 않는다.
	- `userStore.save(user)` 후 `initializeUserLogin(userId)`를 수행한다. Mongo 저장 실패 시 JPA 저장과 문서 저장 간 정합성이 깨질 수 있다.
- 운영 영향:
	- 중복 username이 생성되면 로그인 조회가 모호해진다.
	- 사용자 row는 있으나 로그인 상태 문서가 없어 로그인 시 내부 오류 가능
- 권장 조치:
	- username unique constraint와 사전 중복 검증을 추가한다.
	- PostgreSQL/Mongo 간 원자성이 없음을 고려해 보상 처리나 단일 저장소 정책을 정한다.

### 4. `UserAuthLogic.issueLoginToken()` / `UserLogin.isLoginBlocked()`

- 위치: `slcn-auth/src/main/java/com/seoulchonnom/auth/logic/UserAuthLogic.java:43`, `slcn-spec/src/main/java/com/seoulchonnom/spec/user/entity/UserLogin.java:23`
- 문제:
	- `login.limit.clear.time` 설정이 `application.yml`에 있지만 실제 로직에서 사용되지 않는다.
	- `loginFailCount >= limit`이면 성공 가능성을 평가하지 않고 계속 실패를 기록한다.
- 운영 영향:
	- 한 번 잠긴 계정은 관리자가 DB를 수정하거나 별도 기능이 없으면 해제되지 않을 수 있다.
- 권장 조치:
	- `lastLoginFailTime` 기반 자동 해제 정책을 구현한다.
	- 잠금 상태 응답과 관리자 해제 기능을 정의한다.

### 5. `UserAuthLogic.reissueToken()`

- 위치: `slcn-auth/src/main/java/com/seoulchonnom/auth/logic/UserAuthLogic.java:64`
- 문제:
	- Redis refresh session 저장이 필수 경로다.
	- Redis 장애 시 재발급이 모두 실패한다.
	- refresh token rotation은 수행하지만 이전 토큰 재사용 감지 시 전체 세션 폐기 같은 정책은 없다.
- 운영 영향:
	- Redis 장애가 사용자 전체 재로그인/세션 장애로 직결된다.
	- 탈취된 refresh token 재사용 탐지가 제한적이다.
- 권장 조치:
	- Redis 장애 시 사용자 경험/재로그인 정책을 명확히 한다.
	- 재사용 감지 시 세션 삭제 및 보안 로그를 남긴다.

### 6. `CalendarLogic.deleteCalendar()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/calendar/logic/CalendarLogic.java:70`
- 문제:
	- 캘린더를 하드 삭제한다.
	- `ScheduleJpo.calendarId`에는 JPA 관계나 FK가 없고, 일정 조회는 schedule의 `calendarId` 유효성을 다시 확인하지 않는다.
- 운영 영향:
	- 삭제된 캘린더 ID를 가진 일정이 남을 수 있다.
	- 화면에서 캘린더 메타데이터가 없는 일정이 노출될 가능성이 있다.
- 권장 조치:
	- 운영 API는 `hideCalendar()` 중심으로 사용하고, 하드 삭제는 관리자/정리 작업으로 제한한다.
	- 일정 존재 시 삭제 차단 또는 cascade/hide 정책을 정한다.

### 7. `ScheduleLogic.getSchedulesForRange()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogic.java:70`
- 문제:
	- 날짜 범위 길이에 제한이 없다.
	- `findAllByStartBeforeAndEndAfterAndHiddenFalse()` 결과가 많으면 전체를 메모리로 가져온다.
- 운영 영향:
	- 매우 넓은 기간 조회 시 DB/애플리케이션 부하 증가
- 권장 조치:
	- 최대 조회 기간을 제한한다.
	- 필요한 경우 페이징 또는 월 단위 조회만 허용한다.

### 8. `ScheduleLogic.registerSchedule()` / `modifySchedule()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/schedule/logic/ScheduleLogic.java:85`, `:96`
- 문제:
	- 검증에서 날짜 파싱을 한 뒤 `new Schedule()` 또는 `updateSchedule()`에서 같은 파싱을 반복한다.
	- 현재는 같은 parser라 실질 문제는 작지만, 추후 parser 변경 시 검증과 적용 결과가 달라질 수 있다.
- 운영 영향:
	- 유지보수 중 날짜 변환 버그가 생길 가능성
- 권장 조치:
	- 검증 함수가 파싱된 `LocalDateTime`을 반환하도록 바꾸고, 엔티티 생성/수정에 전달한다.

### 9. `TripLogic.registerTrip()`

- 위치: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/trip/logic/TripLogic.java:43`
- 문제:
	- ID를 먼저 발급하고 그 뒤 요청 검증을 한다.
	- 잘못된 등록 요청도 sequence를 증가시킨다.
- 운영 영향:
	- ID 공백이 생긴다. 기능 오류는 아니지만 감사/운영 추적에서 혼란을 줄 수 있다.
- 권장 조치:
	- 요청 검증을 먼저 수행한 뒤 ID를 발급한다.

### 10. `TripLogic.validateTrip()` / `Trip`, `Quiz`, `Schedule` 생성자

- 위치:
	- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/trip/logic/TripLogic.java:63`
	- `slcn-spec/src/main/java/com/seoulchonnom/spec/trip/entity/Trip.java:31`
	- `slcn-spec/src/main/java/com/seoulchonnom/spec/trip/entity/vo/Quiz.java:30`
	- `slcn-spec/src/main/java/com/seoulchonnom/spec/schedule/entity/Schedule.java:39`
- 문제:
	- 도메인 엔티티/VO가 `*Cdo`, `*Udo` API DTO에 직접 의존한다.
	- `docs/learning/domain-entity-must-not-depend-on-api-dto.md`의 규칙과 다르다.
- 운영 영향:
	- API 계약 변경이 도메인 생성 로직에 직접 전파된다.
	- 검증과 매핑 책임이 섞여 회귀 위험이 커진다.
- 권장 조치:
	- DTO -> 도메인 변환을 mapper/factory로 옮기고, 도메인은 도메인 타입만 받도록 정리한다.

### 11. `FileResource.getFile()`

- 위치: `slcn-rest/src/main/java/com/seoulchonnom/rest/file/FileResource.java:34`
- 문제:
	- `MediaType.parseMediaType(imageFileRdo.getMimeType())`는 잘못된 MIME 문자열에서 예외가 날 수 있다.
	- 다운로드 cache header, ETag, range 요청 처리가 없다.
- 운영 영향:
	- 파일 저장소/OS가 비정상 MIME을 반환하면 500 가능
	- 이미지 요청이 많아질 때 불필요한 트래픽 증가
- 권장 조치:
	- MIME 파싱 실패 시 fallback 처리한다.
	- 정적 파일 서빙 또는 CDN/object storage 사용을 검토한다.

## 추가 확인이 필요한 항목

| 항목 | 확인 이유 |
| --- | --- |
| DB schema의 실제 unique/FK/index | JPA 모델만으로 운영 DB 제약을 단정할 수 없음 |
| `id_sequence` 초기 데이터와 운영 동시 요청량 | ID 중복 위험의 실제 발생 가능성 산정 필요 |
| 업로드 저장소가 로컬 디스크인지 공유 볼륨인지 | 다중 인스턴스 운영 시 파일 조회 불일치 가능 |
| Redis 운영 정책 | refresh session 장애 영향과 TTL 보장 확인 필요 |
| 초기 관리자 생성 절차 | `/user/register`가 ADMIN 전용이라 bootstrap 경로 확인 필요 |

## 수정 적용 시 API 스펙 변경 가능성이 있는 부분

아래 항목은 내부 안정화 작업처럼 보이더라도 클라이언트가 체감하는 요청 형식, 응답 형식, HTTP 상태 코드, 쿠키 정책, 권한 정책이 바뀔 수 있다. 수정 전 프론트엔드/외부 연동 주체와 API 계약 변경 여부를 먼저 확인해야 한다.

| 관련 함수 | 변경 가능 API | 변경 가능성 | 영향 |
| --- | --- | --- | --- |
| `FileUtils.saveImages()` | `POST /file` | 업로드 허용 파일 기준 변경 | 기존에 통과하던 대문자 확장자, SVG, MIME 불일치 파일이 거절될 수 있음 |
| `FileLogic.getImageFile()` / `FileResource.getFile()` | `GET /file` | 응답 헤더/전송 방식 변경 | `Content-Length`, `Content-Type`, cache header, streaming 응답 동작이 달라질 수 있음 |
| `RefreshSessionStore.findBySessionId()` / `UserAuthLogic.reissueToken()` | `POST /user/token` | 실패 상태 코드와 에러 메시지 변경 | Redis/session 손상 케이스가 500에서 400 또는 401로 바뀔 수 있음 |
| `JwtAuthenticationFilter.doFilterInternal()` | 인증이 필요한 전체 API | 인증 실패 처리 변경 | 삭제된 사용자/유효하지 않은 토큰의 응답이 500에서 401로 바뀔 수 있음 |
| `SecurityConfiguration.filterChain()` | 전체 API 권한 정책 | 접근 가능/불가능 경로 변경 | 기존 호출이 401/403으로 막히거나, 반대로 신규 공개 API가 생길 수 있음 |
| `UserResource.buildCookie()` | `POST /user/login`, `/user/token`, `/user/logout` | Set-Cookie 속성 변경 | `Secure`, `SameSite`, `Max-Age`, cookie path 변경 시 브라우저 저장/전송 조건이 달라짐 |
| `UserLogic.registerUser()` | `POST /user/register` | 중복 username 응답 추가 | 기존 중복 등록 성공/DB 오류가 명시적 400 또는 conflict 계열 응답으로 바뀔 수 있음 |
| `UserAuthLogic.issueLoginToken()` / `UserLogin.isLoginBlocked()` | `POST /user/login` | 계정 잠금 정책 변경 | 잠금 해제 시간, 실패 횟수 초과 응답, 재시도 가능 시점이 응답에 포함될 수 있음 |
| `CalendarLogic.deleteCalendar()` | `DELETE /calendar/{calendarId}` | 삭제 정책 변경 | 하드 삭제가 soft delete로 바뀌거나, 참조 일정 존재 시 400/409로 실패할 수 있음 |
| `ScheduleLogic.getSchedulesForRange()` | `GET /schedule` | 조회 범위 제한/페이징 추가 | 넓은 기간 조회가 실패하거나 page/size 파라미터가 필요해질 수 있음 |
| `TripLogic.getTripQuiz()` / `checkTripQuizAnswer()` | `GET /trip/quiz/{tripId}`, `GET /trip/quiz/check` | quiz 없음 응답 명확화 | 기존 500이 400/404 또는 별도 비즈니스 에러 메시지로 바뀔 수 있음 |
| `CommonExceptionHandler.exception()` | 전체 API 에러 응답 | 공통 에러 상태 코드 세분화 | 클라이언트가 의존하던 500 응답이 400/401/404/409 등으로 바뀔 수 있음 |

### 파일 API

- `POST /file`
	- MIME type, 파일 signature, 대소문자 확장자 정책을 강화하면 업로드 허용 범위가 변경된다.
	- 현재 상수는 `svg`를 허용하지만 에러 메시지는 `JPG, PNG 파일만 업로드 가능합니다.`라고 되어 있어, 실제 정책을 정리하면 API 문구와 허용 확장자가 함께 바뀔 수 있다.
	- 파일 크기 제한을 업로드 시점뿐 아니라 조회 시점에도 적용하면 기존 저장 파일 중 일부가 조회 불가가 될 수 있다.
- `GET /file`
	- streaming 응답으로 바꿔도 response body는 여전히 파일 binary지만, `Content-Length`가 빠지거나 range/cache 관련 header가 추가될 수 있다.
	- MIME fallback 정책을 바꾸면 `application/octet-stream`으로 내려가던 파일이 더 구체적인 image type으로 내려가거나 반대가 될 수 있다.

### 인증/사용자 API

- `POST /user/login`
	- 계정 잠금 자동 해제 정책을 도입하면 동일한 실패 횟수에서도 시간 경과 여부에 따라 성공/실패 결과가 달라진다.
	- 잠금 상태에 남은 시간, 실패 횟수 등을 응답에 포함하려면 `ErrorResponse` 또는 별도 오류 DTO 확장이 필요할 수 있다.
- `POST /user/token`
	- refresh session 손상, Redis 누락, token 재사용 감지 케이스를 구분하면 현재 단일 `InvalidRefreshTokenException` 응답보다 세분화된 상태 코드/메시지가 생길 수 있다.
	- refresh token 재사용 감지 시 쿠키 만료 응답을 함께 내려야 한다면 `Set-Cookie` 헤더 동작도 변경된다.
- `POST /user/logout`
	- session id가 없는 요청을 항상 204로 유지할지, 잘못된 세션으로 400/401을 반환할지 정책 결정이 필요하다.
- `POST /user/register`
	- username unique constraint와 중복 검증을 추가하면 중복 요청의 응답 코드가 명확해진다. HTTP 관점에서는 400 또는 409 중 하나를 API 계약으로 정해야 한다.

### 일정/캘린더 API

- `DELETE /calendar/{calendarId}`
	- 참조 일정이 있는 캘린더 삭제를 막으면 기존 204 응답이 400/409로 바뀔 수 있다.
	- 하드 삭제를 soft delete로 바꾸면 응답 코드는 유지할 수 있지만, 이후 `GET /calendar`, `GET /schedule`의 조회 결과 해석이 달라질 수 있다.
- `GET /schedule`
	- 조회 최대 기간을 추가하면 기존에 허용되던 장기간 조회 요청이 실패한다.
	- 페이징을 도입하면 응답 body가 `List<ScheduleRdo>`에서 page wrapper 형태로 바뀔 수 있어 API 스펙 변경 폭이 크다.
- `POST /schedule`, `PUT /schedule`
	- 날짜 파싱 책임을 정리하는 것만으로는 API 변경이 필요 없지만, all-day 일정의 start/end 형식을 엄격히 분리하면 요청 검증 기준이 바뀐다.

### 여행/퀴즈 API

- `GET /trip/quiz/{tripId}`, `GET /trip/quiz/check`
	- quiz가 없는 여행을 명시적으로 처리하면 기존 500 대신 비즈니스 오류 응답이 내려간다.
	- 이 경우 “여행은 있으나 quiz가 없음”을 400, 404, 또는 빈 응답 중 무엇으로 볼지 API 계약을 정해야 한다.
- `POST /trip`
	- DTO 의존 제거 자체는 API 스펙 변경이 필요 없지만, 검증을 강화하면 등록 가능한 요청 값이 줄어들 수 있다.
	- ID 발급 순서를 검증 이후로 바꾸는 것은 응답 스펙에는 영향이 없다.

### 공통 에러 응답

- `CommonExceptionHandler`를 세분화하면 같은 요청 오류가 더 정확한 HTTP 상태 코드로 내려갈 수 있다.
- 현재 `ErrorResponse.from(false, message)` 형태를 유지하면 body 구조는 유지 가능하다.
- 409, 404, 401 등 새로운 에러 코드를 추가하면 `ErrorCode` enum과 API 문서의 에러 코드 표를 함께 갱신해야 한다.

## 개선 우선순위 제안

1. ID 발급 동시성 보장: DB sequence 또는 잠금 적용
2. 파일 업로드/조회 안정화: 디렉터리 생성, streaming 응답, MIME 검증
3. 인증 예외 안정화: Redis 파싱 실패와 사용자 조회 실패를 401/400 계열로 정리
4. 사용자 중복/로그인 잠금 정책 정리: unique constraint, 자동 잠금 해제
5. 캘린더-일정 삭제 정책 정리: soft delete 또는 참조 무결성 적용
6. DTO 의존 도메인 생성자 제거: mapper/factory로 책임 분리
