# Schedule 반복 일정 및 ICS 구독 피드 설계

## 1. 목적

SLCN을 일정의 원본 시스템으로 유지하면서 Apple Calendar와 Google Calendar가 SLCN Schedule을 읽기 전용으로 구독할 수 있게 한다.

외부 Calendar의 이벤트를 SLCN으로 가져오지 않는다. SLCN은 RFC 5545 iCalendar 형식의 HTTPS URL을 제공하고, 외부 Calendar가 자신의 갱신 주기에 따라 해당 URL을 조회한다.

반복 일정은 ICS 구독 피드보다 먼저 SLCN Schedule에 도입한다. 반복 규칙 원본을 저장하고 기존 JSON 기간 조회에서는 서버가 기간 내 occurrence를 계산해 반환한다. ICS 피드에서는 반복 원본과 RRULE을 발행해 외부 Calendar가 occurrence를 계산하게 한다.

## 2. 확정된 요구사항

- Calendar와 Schedule은 두 사용자가 공유하는 전역 데이터다. 사용자별 소유권을 추가하지 않는다.
- 서비스 시간대는 `Asia/Seoul`로 고정한다.
- 외부 Calendar 연동은 읽기 전용이다.
- 기존 Calendar/Schedule JSON API 계약은 유지하고 필요한 필드만 하위 호환 방식으로 추가한다.
- 외부 Calendar는 신규 ICS URL을 통해 Schedule을 구독한다.
- 모든 feed token은 동일한 전역 Schedule 집합을 조회한다.
- Google/Apple 공급자별 SDK, OAuth, webhook, sync token은 사용하지 않는다.
- 즉시 동기화는 보장하지 않는다. 수정·숨김·삭제는 외부 Calendar의 다음 피드 갱신 이후 반영된다.
- 여러 feed token을 발급할 수 있고, token별로 독립적으로 폐기할 수 있다.
- Calendar 이름은 각 외부 일정의 제목 prefix와 카테고리에 포함한다.

## 3. 범위 밖

- 외부 Calendar 이벤트를 SLCN에 import
- 외부 Calendar에서 발생한 수정·삭제를 SLCN에 반영
- 특정 반복 occurrence만 수정하거나 삭제하는 반복 예외
- 외부 참석자, 초대, 참석 응답 동기화
- 공급자별 색상 강제 적용
- CalDAV 서버 구현
- 외부 Calendar 갱신 주기 제어 또는 즉시 push
- 사용자별 또는 Calendar별 feed scope
- feed token 만료와 token 사용 감사 이력

## 4. 전체 구조

```text
기존 JWT 관리 API
  POST   /api/schedule/feeds
  GET    /api/schedule/feeds
  DELETE /api/schedule/feeds/{feedId}
              |
              v
       ScheduleFeedTokenLogic
              |
              v
       ScheduleFeedTokenStore ── PostgreSQL(token hash)

Apple/Google Calendar
  GET /api/schedule/feeds/{feedToken}/calendar.ics
              |
              v
       ScheduleFeedFlow
         1. token hash 검증
         2. Schedule/Calendar 조회
         3. ICS 렌더링
              |
              v
       text/calendar; charset=UTF-8
```

기존 Schedule JSON 조회와 신규 ICS 조회는 같은 Schedule 원본을 사용하되 표현 방식을 분리한다.

## 5. 반복 일정 모델

### 5.1 저장 모델

`Schedule`, `ScheduleJpo`, `ScheduleCdo`, `ScheduleUdo`, `ScheduleRdo`에 nullable `recurrenceRule`을 추가한다.

- 형식은 RFC 5545 RRULE의 값 부분이다.
- 예: `FREQ=WEEKLY;BYDAY=TU;COUNT=10`
- DB에는 입력 원본을 보존한다.
- 등록·수정 시 문법과 지원 범위를 검증한다.
- 빈 문자열은 반복 없음으로 정규화한다.

운영 DB에 과거 `recurrence_rule` 컬럼이 남아 있을 수 있으므로 스키마 변경 전에 실제 컬럼과 데이터 상태를 확인한다.

### 5.2 초기 지원 범위

- `FREQ`: `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`
- 종료 조건: `COUNT` 또는 `UNTIL`, 혹은 무기한
- 요일 지정: `BYDAY`
- 월 일자 지정: `BYMONTHDAY`
- 종일 및 시간이 있는 Schedule

초기 버전은 `EXDATE`, `RDATE`, `RECURRENCE-ID` 기반의 특정 occurrence 예외를 지원하지 않는다. 수정·숨김·삭제는 반복 시리즈 전체에 적용한다.

### 5.3 JSON 기간 조회

기존 기간 조회는 다음 후보를 합쳐서 처리한다.

1. 일반 Schedule: 기존과 같이 `[start, end)` 기간이 겹치는 행
2. 반복 Schedule: `hidden=false`, `recurrenceRule is not null`, 원본 `start < rangeEnd`인 행

후보 반복 Schedule은 서버에서 요청 범위 내 occurrence로 확장한다. 기존 최대 1개월 조회 제한을 유지해 무기한 RRULE도 유한하게 계산한다.

`ScheduleRdo`에는 다음 필드를 하위 호환 방식으로 추가한다.

- `recurrenceRule`: 원본 반복 규칙. 반복이 아니면 null
- `occurrenceId`: 화면에서 occurrence를 구분하는 안정적인 값. 일반 Schedule은 null

반복 occurrence의 기존 `id`는 원본 Schedule ID를 유지한다. 수정·숨김·삭제 API는 원본 Schedule ID를 받아 시리즈 전체에 적용한다. 프런트엔드는 반복 occurrence를 렌더링할 때 `occurrenceId`를 화면 식별자로 사용한다.

## 6. Feed token

### 6.1 token 생성

- `SecureRandom`으로 256-bit 값을 생성한다.
- Base64 URL-safe, no-padding 문자열로 표현한다.
- 원문 token과 완성된 feed URL은 생성 응답에서 한 번만 반환한다.
- DB에는 `SHA-256(token)`만 저장한다.
- 순차 ID, Calendar ID, Schedule ID, 기존 JWT를 feed token으로 사용하지 않는다.

### 6.2 저장 모델

초기 모델은 최소 필드만 둔다.

```text
schedule_feed_token
  id                UUID primary key
  name              VARCHAR not null
  token_hash        VARCHAR unique not null
  registered_time   BIGINT
  modified_time     BIGINT
  entity_version    BIGINT
```

폐기는 row를 물리 삭제한다. 만료·비활성 상태·사용 이력은 초기 범위에서 제외한다. token을 회전하려면 새 token을 만든 뒤 외부 Calendar의 URL을 변경하고 기존 token을 삭제한다.

### 6.3 검증

ICS 요청에서 받은 PathVariable token을 SHA-256으로 변환해 `token_hash`를 조회한다.

- 일치하는 row가 있으면 feed 생성
- 일치하지 않으면 `404 Not Found`
- token 원문을 로그, 예외, 응답 또는 ETag에 포함하지 않음

Spring Security는 ICS 조회 경로에 JWT 없이 진입하도록 허용한다. 이는 공개 데이터 endpoint라는 뜻이 아니며, `ScheduleFeedFlow`의 DB token 검증이 실제 인증 경계다. feed 관리 API와 기존 Schedule API는 계속 JWT `USER` 권한을 요구한다.

## 7. ICS API

### 7.1 조회

```http
GET /api/schedule/feeds/{feedToken}/calendar.ics
```

응답:

```http
HTTP/1.1 200 OK
Content-Type: text/calendar; charset=UTF-8
Cache-Control: private, no-cache
ETag: "<canonical-feed-hash>"
```

`If-None-Match`가 현재 ETag와 일치하면 body 없이 `304 Not Modified`를 반환한다.

유효한 token에 Schedule이 하나도 없어도 유효한 빈 VCALENDAR를 `200`으로 반환한다.

### 7.2 발행 대상

- `Schedule.hidden=false`인 모든 Schedule을 발행한다.
- Calendar의 `visible`은 현재 화면 선택용 속성으로 보고 feed 포함 여부에 사용하지 않는다.
- Calendar가 존재하지 않는 고아 Schedule은 feed에서 제외하고 경고 로그를 남긴다.
- 데이터 규모가 작은 서비스이므로 초기에는 날짜 horizon 없이 전체 Schedule을 발행한다.
- 일반 Schedule은 VEVENT 하나로 발행한다.
- 반복 Schedule도 occurrence를 펼치지 않고 VEVENT 하나와 RRULE로 발행한다.

Calendar 이름을 안정적으로 제공하기 위해 Schedule이 연결된 Calendar의 hard delete는 거부한다. 먼저 연결된 Schedule을 다른 Calendar로 이동하거나 삭제해야 한다.

### 7.3 VEVENT 매핑

| SLCN | iCalendar |
|---|---|
| Schedule UUID | `UID:{scheduleId}@slcn` |
| Calendar name + title | `SUMMARY:[{calendar.name}] {schedule.title}` |
| body | `DESCRIPTION` |
| Calendar name | `CATEGORIES` |
| location | `LOCATION` |
| timed start/end | `DTSTART/DTEND;TZID=Asia/Seoul` |
| all-day start/end | `DTSTART/DTEND;VALUE=DATE` |
| recurrenceRule | `RRULE` |
| entityVersion | `SEQUENCE` |
| Schedule/Calendar modifiedTime 중 최신 값 | `LAST-MODIFIED` |
| Schedule/Calendar modifiedTime 중 최신 값 | `DTSTAMP` |

`SUMMARY`는 다음 형식을 사용한다.

```text
[{calendar.name}] {schedule.title}
```

`DESCRIPTION`에는 Schedule body만 출력한다. Calendar 이름은 `SUMMARY`와 `CATEGORIES`에 이미 있으므로 중복하지 않는다. body가 없으면 `DESCRIPTION`을 생략한다.

`UID`의 `@slcn` suffix는 설정값이나 배포 도메인 변경에 영향받지 않는 고정 제품 식별자로 취급한다.

### 7.4 직렬화 규칙

- RFC 5545의 CRLF 줄바꿈과 마지막 CRLF를 사용한다.
- text의 역슬래시, 쉼표, 세미콜론, 줄바꿈을 escape한다.
- content line은 UTF-8 기준 75 octet 규칙으로 folding한다.
- Calendar/Schedule 정렬을 고정해 같은 데이터는 같은 canonical body와 ETag를 만든다.
- 요청 시각을 ICS 본문에 넣지 않는다. `DTSTAMP`도 이벤트의 최신 수정 시각에서 계산해 데이터가 바뀌지 않으면 canonical body와 ETag가 유지되게 한다.
- 종일 `DTEND`는 exclusive 날짜를 유지한다.
- VCALENDAR에는 `VERSION:2.0`, 고정 `PRODID`, `CALSCALE:GREGORIAN`, `METHOD:PUBLISH`와 `Asia/Seoul` VTIMEZONE을 포함한다.

## 8. 수정·숨김·삭제 동작

### 8.1 수정

- Schedule ID와 ICS UID는 유지한다.
- JPA `entityVersion` 증가값을 ICS `SEQUENCE`로 사용한다.
- Schedule 수정 시 `modifiedTime`을 현재 시각으로 갱신한다.
- 변경된 title/body/location/start/end/Calendar/recurrenceRule은 다음 feed 조회에서 반영된다.
- Calendar 이름 수정 시 Calendar의 `modifiedTime`을 갱신한다. 해당 Calendar에 속한 VEVENT의 `SUMMARY`, `CATEGORIES`, `LAST-MODIFIED`와 전체 feed ETag가 함께 변경된다.

### 8.2 숨김

- row를 유지하고 `hidden=true`로 변경한다.
- 다음 feed 응답부터 VEVENT를 제외한다.
- tombstone 또는 `STATUS:CANCELLED`는 발행하지 않는다.

### 8.3 삭제

- 현재 hard delete 동작을 유지한다.
- 다음 feed 응답부터 VEVENT를 제외한다.
- 삭제 tombstone은 저장하지 않는다.

외부 Calendar에서 실제로 사라지는 시점은 해당 Calendar의 갱신 주기에 좌우된다. 즉시 반영은 보장하지 않는다.

## 9. 관리 API

기존 JWT 인증을 사용하는 신규 API를 제공한다.

### 9.1 생성

```http
POST /api/schedule/feeds
Content-Type: application/json
Authorization: Bearer <SLCN JWT>

{"name":"Google Calendar"}
```

생성 응답은 feed ID, 이름, 원문 token이 포함된 전체 `feedUrl`을 반환한다.

### 9.2 목록

```http
GET /api/schedule/feeds
Authorization: Bearer <SLCN JWT>
```

목록은 ID, 이름, 등록 시각만 반환한다. hash와 원문 URL은 반환하지 않는다.

### 9.3 폐기

```http
DELETE /api/schedule/feeds/{feedId}
Authorization: Bearer <SLCN JWT>
```

row를 삭제하고 `204 No Content`를 반환한다. 이미 삭제되었거나 존재하지 않으면 `404`를 반환한다.

## 10. 모듈 경계

### slcn-spec

- 반복 규칙 필드를 포함한 Schedule 계약
- feed 생성·목록 DTO와 `ScheduleFeedFacade`
- feed token 순수 도메인 모델

### slcn-aggregate

- 반복 규칙 검증과 occurrence 계산
- 반복 Schedule candidate 조회
- feed token Logic/Store/Repository/JPO/Mapper
- token hashing과 난수 생성
- Schedule/Calendar 조합 및 ICS 변환 입력 모델

### slcn-rest

- JWT 기반 feed 관리 Resource
- 공개 진입 후 DB token을 검증하는 ICS Resource
- HTTP header, ETag, 조건부 GET 처리
- RFC 5545 ICS renderer

### slcn-auth

- 기존 JWT 인증을 그대로 사용한다.
- feed token을 JWT나 Client 인증으로 통합하지 않는다.

### slcn-boot

- ICS 조회 경로 Security matcher
- token PathVariable access-log 마스킹을 위한 운영 설정 또는 배포 설정 문서

Resource는 HTTP 책임만 가지고, token 검증과 Schedule/Calendar 조합은 Flow에 위임한다.

## 11. 오류 처리

- 잘못된 feed token: `404`
- feed 관리 ID 없음: `404`
- 잘못된 RRULE: 기존 BusinessException 규칙에 맞춘 `400`
- 연결된 Schedule이 있는 Calendar 삭제: `CalendarScheduleConflictException`과 `ErrorCode.CALENDAR_SCHEDULE_CONFLICT`를 통한 `409`
- 유효한 token이지만 일정 없음: `200` + 빈 VCALENDAR
- ICS 생성 중 예상하지 못한 오류: `500`, token과 Schedule 본문은 로그에 출력하지 않음

## 12. 테스트 전략

### 반복 일정

- RRULE 등록·수정·해제와 JPO/DTO 매핑
- `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`
- `COUNT`, `UNTIL`, 무기한 반복
- `BYDAY`, `BYMONTHDAY`
- 조회 범위보다 오래전에 시작한 반복의 occurrence
- `[start, end)` 경계와 종일 반복
- 잘못된 RRULE 거부
- 일반 Schedule 기존 동작 회귀

### feed token

- 256-bit URL-safe token 생성
- DB에는 hash만 저장
- 같은 token hash 조회 성공
- 잘못된 token, 삭제된 token은 실패
- 여러 token이 같은 Schedule 집합을 조회
- 목록 응답에 token/hash가 없음

### ICS

- Content-Type, Cache-Control, ETag
- `If-None-Match`의 `304`와 빈 body
- 유효한 빈 VCALENDAR
- 안정적인 UID와 정렬
- 수정 후 동일 UID, 증가한 SEQUENCE, 갱신된 LAST-MODIFIED
- hide/delete 후 VEVENT 제외
- timed/all-day 및 RRULE
- Calendar 이름이 SUMMARY prefix와 CATEGORIES에 포함되고 DESCRIPTION에는 중복되지 않음
- 한글 UTF-8, escape, CRLF, 75-octet folding
- 생성 결과를 iCalendar parser로 다시 읽는 round-trip

### 보안 및 HTTP

- JWT 없이 유효한 URL token으로 feed 조회 성공
- JWT가 있어도 잘못된 feed token은 우회 불가
- feed 관리 API는 JWT 없이 접근 불가
- feed URL의 POST/PUT/DELETE는 허용하지 않음
- token 원문이 로그, ETag, 오류 응답에 포함되지 않음

### 실제 호환성

- Apple Calendar URL 구독
- iCloud 위치 구독 후 iPhone 표시
- Google Calendar 데스크톱 웹 URL 추가
- 한글, 종일, 반복, 수정, 숨김, 삭제 갱신 확인

## 13. 구현 순서

1. 운영 DB의 recurrence 컬럼 잔존 여부를 확인한다.
2. 반복 규칙 저장·검증·매핑을 추가한다.
3. 반복 candidate 조회와 JSON occurrence 확장을 추가한다.
4. Schedule 수정 시각 갱신과 Calendar 삭제 보호를 추가한다.
5. feed token 도메인과 persistence를 추가한다.
6. JWT 기반 feed 관리 API를 추가한다.
7. ICS renderer와 ScheduleFeedFlow를 추가한다.
8. token 기반 ICS 조회와 Security matcher를 추가한다.
9. ETag/조건부 GET을 추가한다.
10. 전체 회귀 테스트와 Apple/Google 호환성 테스트를 수행한다.

## 14. 성공 기준

- 기존 Calendar/Schedule API 테스트가 통과한다.
- 반복 Schedule이 기존 JSON 기간 조회에서 정확한 occurrence로 반환된다.
- 유효한 feed URL을 Apple Calendar와 Google Calendar에 등록할 수 있다.
- 두 서비스에서 일반·종일·반복 Schedule과 Calendar 이름을 확인할 수 있다.
- Schedule 수정 후 UID는 유지되고 내용·SEQUENCE·LAST-MODIFIED가 갱신된다.
- Schedule hide/delete 후 외부 Calendar의 다음 갱신에서 일정이 사라진다.
- feed token 원문은 생성 응답 이외의 DB/API/log에 남지 않는다.
- token을 삭제하면 해당 feed URL은 `404`를 반환한다.
