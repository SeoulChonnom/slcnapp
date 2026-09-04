# Schedule ICS 구독 피드 운영 가이드

SLCN Schedule을 Apple Calendar와 Google Calendar가 읽기 전용으로 구독할 수 있는
RFC 5545 iCalendar 피드다. SLCN이 일정의 원본이며 외부 Calendar의 수정·삭제를
SLCN으로 가져오지 않는다. 외부 Calendar가 다음 갱신 때 HTTPS URL을 다시 조회하면
SLCN의 변경 사항이 반영된다.

## 보안 모델과 전제

- 운영 URL은 반드시 HTTPS를 사용한다. URL의 feed token은 URL을 가진 사람에게
  공유 일정 전체를 읽게 하는 bearer credential이다.
- 기본 애플리케이션 context path는 `/api`다. 아래 API 예시는
  `https://<host>/api/...`를 사용한다.
- 관리 API는 기존 SLCN JWT 인증이 필요하고 `ADMIN` 권한만 허용한다. `Bearer` 형식과
  기존 `X-AUTH-TOKEN` 헤더를 사용할 수 있다.
- ICS GET만 JWT 없이 진입할 수 있다. 공개로 보이는 진입 뒤에
  `ScheduleFeedFlow`가 원문 token의 SHA-256 hash를 DB에서 검증하므로, JWT를
  제시해도 잘못되거나 폐기된 token을 우회할 수 없다.
- token은 SecureRandom 256-bit 값의 URL-safe, no-padding Base64 문자열이다. 원문은
  생성 응답에서 한 번만 반환되고 DB에는 hash만 저장된다. 목록, 오류, ETag, 로그에는
  원문 token을 넣지 않는다.
- 모든 유효한 token은 사용자·Calendar별 scope 없이 동일한 전역 Schedule 집합을
  읽는다. token 만료와 사용 감사 이력은 현재 범위가 아니다.
- Google/Apple SDK, OAuth, webhook, sync token, CalDAV 또는 외부 이벤트 import는
  사용하지 않는다.

### Forwarded header 설정

`server.forward-headers-strategy`의 기본값은 `none`이다. 이 설정에서는 애플리케이션이
클라이언트가 임의로 보낸 `X-Forwarded-Proto`, `X-Forwarded-Host`,
`X-Forwarded-Prefix`를 신뢰하지 않으므로, 공격자가 feed 생성 응답의 scheme·host·path를
위조할 수 없다.

TLS를 신뢰할 수 있는 reverse proxy에서만 종료하는 운영 배포라면 다음처럼 환경변수로
Spring Boot의 `framework` 전략을 선택할 수 있다.

```text
SLCN_FORWARD_HEADERS_STRATEGY=framework
```

`framework`는 proxy가 위 세 헤더를 외부 입력에서 제거한 뒤 실제 요청 값으로 덮어쓰고,
애플리케이션이 proxy를 거치지 않은 직접 접근으로부터 격리되어 있을 때만 사용한다.
인터넷에 직접 노출된 애플리케이션이나 헤더를 정규화하지 않는 proxy에서는 기본값
`none`을 유지한다. Spring Boot의 `framework` 전략이 활성화되면 Resource가 신뢰된
forwarded scheme/host/prefix를 반영해 `feedUrl`을 생성한다.

## 관리 API (ADMIN)

### Feed 생성

```http
POST https://<host>/api/schedule/feeds
Authorization: Bearer <SLCN-JWT>
Content-Type: application/json

{"name":"Google Calendar"}
```

성공하면 `201 Created`와 함께 URL을 포함한 응답을 한 번 받는다.

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "<feed-id>",
  "name": "Google Calendar",
  "feedUrl": "https://<host>/api/schedule/feeds/<one-time-token>/calendar.ics",
  "registeredTime": 1757000000000
}
```

`feedUrl`의 `<one-time-token>`을 즉시 비밀 저장소에 복사한다. 응답을 잃으면
기존 token을 다시 조회할 수 없으므로 새 feed를 생성해야 한다. `name`은 공백이 아닌
최대 100자다.

### Feed 목록

```http
GET https://<host>/api/schedule/feeds
Authorization: Bearer <SLCN-JWT>
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": "<feed-id>",
    "name": "Google Calendar",
    "registeredTime": 1757000000000
  }
]
```

목록에는 `feedUrl`, 원문 token, `tokenHash`가 없다.

### Feed 폐기

```http
DELETE https://<host>/api/schedule/feeds/<feed-id>
Authorization: Bearer <SLCN-JWT>
```

성공하면 `204 No Content`다. 이미 없는 ID는 `404 Not Found`다. 삭제된 URL도 다음
요청부터 동일하게 `404`가 되며, 외부 Calendar에 남아 있는 캐시 데이터가 즉시
지워지는 것은 아니다.

관리 API의 대표 오류는 다음과 같다.

| 요청 | 결과 |
| --- | --- |
| JWT 없음 | `401 Unauthorized` |
| USER 또는 CLIENT만 보유 | `403 Forbidden` |
| 없는 feed ID 또는 잘못된/폐기된 URL token | `404 Not Found` |
| 연결된 Schedule이 있는 Calendar 삭제 | `409 Conflict` |

## 구독 절차

1. SLCN에 인증한 뒤 `POST /api/schedule/feeds`로 feed를 생성한다.
2. 생성 응답의 `feedUrl`을 한 번만 복사해 비밀 저장소에 보관한다.
3. Apple Calendar 또는 Google Calendar의 구독 UI에 URL을 붙여 넣는다.
4. URL을 회전할 때는 기존 feed를 삭제하기 전에 대체 feed를 먼저 생성하고 새 URL로
   구독을 바꾼다.
5. Schedule 변경·숨김·삭제는 외부 Calendar의 다음 refresh 이후에 반영된다고
   안내한다. 즉시 동기화는 보장하지 않는다.

### Apple Calendar

macOS Calendar에서 `File > New Calendar Subscription`을 선택해 `feedUrl`을
붙여 넣고, 가능하면 iCloud 위치를 선택한다. 구독 Calendar는 읽기 전용이며, iCloud에
저장하면 같은 Apple 계정의 iPhone/iPad에도 해당 구독이 표시된다. Apple 버전에 따라
메뉴 이름이나 refresh UI가 다를 수 있으므로 URL을 일반 캘린더로 import하지 말고
구독으로 추가한다.

### Google Calendar

데스크톱 웹 Google Calendar에서 `Other calendars` 옆 `+`를 선택하고 `From URL`에
`feedUrl`을 붙여 넣은 뒤 추가한다. Google이 관리하는 refresh 주기 후 계정의 모바일
앱에도 구독 Calendar가 표시된다. SLCN JWT나 Google OAuth를 입력하지 않는다. Google이
외부 URL을 조회하는 주기와 캐시 만료는 SLCN이 제어하지 않는다.

## ICS 조회 계약

```http
GET https://<host>/api/schedule/feeds/<feed-token>/calendar.ics
If-None-Match: "<etag>"
```

유효한 token이면 `200 OK`와 함께 다음 헤더를 반환한다.

```http
Content-Type: text/calendar; charset=UTF-8
Cache-Control: no-cache, private
ETag: "<64-lowercase-hex-digest>"
```

ETag는 token이 아니라 canonical UTF-8 ICS 본문의 SHA-256이다. 본문은 요청 시각을
포함하지 않으므로 데이터가 같으면 모든 token과 요청에서 같은 ETag가 나온다.
`If-None-Match`가 현재 ETag와 일치하면 강한 형식, `W/` 약한 형식, 쉼표로 구분한
목록, `*` 모두 `304 Not Modified`와 빈 body를 반환한다. `Cache-Control`은
`no-cache, private`로 유지해 공유 캐시가 일정을 공개하지 않으면서 클라이언트가
재검증할 수 있게 한다.

유효한 token에 일정이 하나도 없어도 `200`과 parse 가능한 빈 `VCALENDAR`를 반환한다.
잘못된·공백·폐기된 token은 token 값을 오류 응답에 포함하지 않고 `404 Not Found`로
처리한다. 이 경로의 POST/PUT/DELETE나 `calendar.ics`가 아닌 경로는 공개하지 않는다.

## Schedule/Calendar 매핑

| SLCN 값 | ICS 값과 의미 |
| --- | --- |
| Schedule ID | `UID:{scheduleId}@slcn` (수정해도 유지) |
| Calendar 이름 + Schedule title | `SUMMARY:[{calendar.name}] {schedule.title}` |
| Schedule body | `DESCRIPTION`; 비어 있으면 property 생략 |
| Calendar 이름 | `CATEGORIES`의 하나의 category |
| Schedule location | `LOCATION`; 비어 있으면 생략 |
| timed start/end | `DTSTART/DTEND;TZID=Asia/Seoul` |
| all-day start/end | `DTSTART/DTEND;VALUE=DATE`; `DTEND`는 exclusive |
| Schedule recurrenceRule | occurrence를 펼치지 않고 원문 value-part 하나를 `RRULE`로 발행 |
| Schedule entityVersion | `SEQUENCE` |
| 최신 Schedule/Calendar modifiedTime | `LAST-MODIFIED`와 `DTSTAMP` (UTC) |

Calendar 이름은 `SUMMARY` prefix와 `CATEGORIES`에만 넣고 `DESCRIPTION`에는 반복하지
않는다. 한글과 text의 역슬래시·쉼표·세미콜론·줄바꿈은 RFC 5545 규칙으로 escape한다.
모든 line은 CRLF로 끝나며 UTF-8 기준 75 octet에서 folding한다. VCALENDAR에는
`VERSION:2.0`, 고정 `PRODID`, `CALSCALE:GREGORIAN`, `METHOD:PUBLISH`와
`Asia/Seoul` VTIMEZONE 하나가 포함된다. VEVENT의 고정 Organizer는
`https://github.com/SeoulChonnom/slcnapp`이다.

Feed는 다음 Schedule만 발행한다.

- `hidden=false`인 모든 Schedule을 날짜 horizon 없이 발행한다.
- `Calendar.visible`은 화면 선택 속성이므로 feed 포함 여부를 결정하지 않는다.
- Calendar이 없는 고아 Schedule은 제외하고 ID만 경고 로그에 남긴다.
- 일정이 없는 유효한 feed는 위에서 설명한 빈 VCALENDAR를 발행한다.

### 반복 일정

ICS에는 반복 master 하나를 VEVENT 하나와 `RRULE` 하나로 발행한다. `DAILY`, `WEEKLY`,
`MONTHLY`, `YEARLY`와 `COUNT`, `UNTIL`, `BYDAY`, `BYMONTHDAY`의 지원 범위 및
`EXDATE`·`RDATE`·`RECURRENCE-ID` 미지원은 [Schedule 반복 일정 API](schedule-recurrence.md)와
동일하다. JSON 기간 조회와 달리 ICS에서 occurrence를 서버가 펼치지 않는다.

### 수정·숨김·삭제

- Schedule 수정은 같은 UID를 유지하고 `entityVersion` 기반 `SEQUENCE`, 내용, 날짜,
  recurrence, `LAST-MODIFIED`, feed ETag를 갱신한다.
- Schedule을 숨기면 row는 남지만 다음 feed부터 VEVENT를 발행하지 않는다. 삭제도
  다음 feed부터 제외하며 `STATUS:CANCELLED` 같은 tombstone은 발행하지 않는다.
- Calendar 이름을 수정하면 Calendar `modifiedTime`이 갱신되어 해당 Calendar의
  `SUMMARY`, `CATEGORIES`, `LAST-MODIFIED`와 전체 feed ETag가 함께 바뀐다.
- 연결된 Schedule이 있는 Calendar의 hard delete는 `409 Conflict`로 거부한다. 먼저
  Schedule을 다른 Calendar로 이동하거나 삭제해야 feed의 이름 매핑이 안정적으로
  유지된다.
- SLCN의 등록·수정 경계는 `start < end`인 양의 기간만 허용한다. renderer도 방어적으로
  `start >= end` 또는 날짜 누락을 거부하므로 RFC-invalid인 동일 `DTSTART`/`DTEND`를
  발행하지 않는다. 과거 DB에 잘못된 row가 있으면 해당 feed는 일반적인 `500` 오류가
  될 수 있으므로 배포 전에 데이터를 교정한다.

## Token 회전과 폐기

URL은 비밀번호처럼 취급한다. 유출이 의심되면 다음 순서를 따른다.

1. `POST /api/schedule/feeds`로 대체 feed를 생성한다.
2. 새 `feedUrl`을 Apple/Google 구독에 등록하고 갱신을 확인한다.
3. `DELETE /api/schedule/feeds/{oldFeedId}`로 이전 feed를 폐기한다.
4. 이전 URL을 직접 요청해 `404`인지 확인한다.

기존 외부 Calendar가 폐기 전 본문을 캐시할 수 있지만, 폐기된 URL로 이후 갱신을
성공시킬 수는 없다. 사용하지 않는 feed도 같은 폐기 절차로 제거한다.

## 인프라 로그와 관측 정보 보호

애플리케이션 앞단과 관측 도구가 path를 수집하기 전에 다음 치환을 적용한다.

```text
/api/schedule/feeds/{any-token}/calendar.ics
→ /api/schedule/feeds/***/calendar.ics
```

이 규칙은 reverse proxy, ingress, load balancer, APM trace/span, WAF, access log,
error log, request metrics의 path label에 모두 적용한다. 원문 URL을 referer,
exception message, 사용자 정의 audit event, debug log에 남기지 않는다. token은
쿼리 파라미터로 전달하지 않으며 HTTPS를 종료하는 첫 hop부터 redaction해야 한다.
운영 점검 시에는 raw token 검색 대신 위 마스킹 경로와 응답 상태·지연 시간만
확인한다. DB에는 `token_hash`만 있어야 하고 ETag나 목록 응답에도 token이 없어야 한다.

## Task 9 호환성 상태와 배포 후 확인

2026-09-04 현재 이 로컬 worktree에는 외부에서 접근 가능한 HTTPS 배포 주소와 Apple/
Google 계정이 없으므로 실제 macOS/iOS 또는 Google Calendar 구독을 수행하지 않았다.
자동화된 iCal4j renderer component/round-trip 및 HTTP contract 테스트가 한글·escape·
folding, timed/all-day/반복, 수정·숨김·삭제, ETag/조건부 GET, token 보안 및 ADMIN 권한
계약을 검증한다. 이는 외부 provider의 실제 동기화 성공을 의미하지 않는다.
다음 표는 배포 후 실제 증거로 채운다. 실행하지 않은 검사를 통과로 표시하지 않는다.

| 클라이언트 | 상태 | 배포 후 확인 항목 |
| --- | --- | --- |
| Apple Calendar macOS | 미실행 — 공개 HTTPS 필요 | 구독 추가, 읽기 전용, 한글, timed/all-day/반복, Calendar 이름 |
| Apple Calendar iOS/iCloud | 미실행 — 공개 HTTPS와 iCloud 계정 필요 | iPhone 표시, refresh 후 수정·숨김·삭제 |
| Google Calendar 웹 | 미실행 — 공개 HTTPS와 Google 계정 필요 | `From URL`, 제목 prefix, category 표시, refresh |
| Google Calendar 모바일 | 미실행 — 웹 구독과 계정 refresh 필요 | 동일 계정 표시, 수정·숨김·삭제 지연 |

배포 후 대표 일정(한글 timed, one-day all-day, `FREQ=WEEKLY;BYDAY=TU;COUNT=3`, 빈
body, 긴 한글 body)을 만들고 각 클라이언트에서 표시를 확인한다. 한 일정 수정 후
UID가 유지되고, 숨김·삭제 후 다음 provider refresh에서 사라지는지 확인한다. 마지막으로
이전 feed를 폐기하고 다음 요청이 `404`인지 확인한다. Provider별 refresh 지연이나
category 표시 차이는 기록하되, RFC 출력이 잘못된 경우가 아니라면 특정 UI를 위해
서버 표현을 변경하지 않는다.
