# Schedule ICS 리뷰 지적 사항 수정 설계

- 작성일: 2026-09-07
- 대상: PR #5 `feat/schedule-ics-feed` (base `main`, head 57fd48d)
- 목적: PR #5 검증에서 확인된 결함 9건(H1~L3)의 수정 방안 확정

## 배경

PR #5는 빌드와 테스트를 통과한다(`./gradlew test` → 368 tests, 실패 0). 그러나 검증 과정에서
운영 중단 가능성이 있는 결함 1건과 계약·확장성·운영 관련 결함 8건을 확인했다. 이 문서는 그
9건의 수정 방안을 확정한다.

**서비스 규모.** SLCN은 커플 2인이 쓰는 서비스다. 이 사실이 아래 런북의 형태를 결정한다 —
데이터 건수가 적어 배포 절차에서 대량 마이그레이션이나 별도 백업 테이블이 필요하지 않고,
영향받는 행을 직접 눈으로 확인한 뒤 처리하는 편이 더 안전하고 단순하다. 다인 서비스로
성격이 바뀌면 8절·9절을 다시 설계해야 한다.

검증에서 확인한 스키마 사실(Hibernate 메타데이터에서 직접 추출, 추정 아님):

```
TABLE slcn.schedule
  id              varchar(255)  NOT NULL
  entity_version  bigint        NOT NULL
  registered_time bigint        NULL
  modified_time   bigint        NULL
  calendar_id     varchar(255)  NULL
  title           varchar(255)  NULL
  body            varchar(255)  NULL
  is_all_day      boolean       NOT NULL
  start_time      timestamp(6)  NULL
  end_time        timestamp(6)  NULL
  location        varchar(255)  NULL
  recurrence_rule text          NULL
  hidden          boolean       NOT NULL
```

두 가지가 설계에 직접 영향을 준다.

- `start_time` / `end_time` 이 **nullable** 이다. CHECK 제약만으로는 NULL을 막지 못하므로
  (`NULL > NULL` 은 `NULL` 이고 CHECK는 이를 통과시킨다) NOT NULL 전환이 별도로 필요하다.
- `hidden` 이 **NOT NULL, 기본값 없음** 이다. 엔티티에서 필드만 제거하면 INSERT문에서 컬럼이
  빠지고 DB가 거부하므로, `DROP COLUMN` 은 선택이 아니라 필수 절차다.

## 확정된 결정

| ID | 결정 | 근거 |
|----|------|------|
| D1 | 종일 일정의 `end` 는 **exclusive**. 하루짜리는 `start=01-01, end=01-02` | 저장·API·ICS가 같은 의미를 갖고 RFC 5545와 1:1이 된다 |
| D2 | 백필 불필요 | 운영 DB에 `allDay=true` 행이 없음을 확인 |
| D3 | `start < end` 불변식은 **쓰기 매퍼 `@AfterMapping` + DB CHECK + NOT NULL** 3계층 | 복원 경로를 검증에서 제외해 조회 장애 위험을 만들지 않는다 |
| D4 | `ScheduleIcsRenderer` 는 **fail-fast 유지** (이벤트별 skip 미채택) | D3이 start/end 경로를 원천 차단한다. 잔여 위험은 아래 "남는 위험"에 명시 |
| D5 | Schedule의 **soft delete 제거**, hard delete로 단일화 | 복구 API가 없어 현재의 soft delete는 쓰기 전용이다 |
| D6 | ICS 피드에 **시간 창** 도입, 창 경계는 **월 초로 스냅** | 무제한 조회를 막으면서 ETag 안정성을 유지한다 |
| D7 | 반복 후보 조회는 **인덱스 추가만**, `recurrence_end` 파생 컬럼은 후속 | `COUNT` 소진 계산 비용이 커 이번 범위를 넘는다 |
| D8 | `SLCN_PUBLIC_BASE_URL` 설정 도입 | `forward-headers-strategy: none` 기본값을 유지하면서 `feedUrl` 을 사용 가능하게 한다 |
| D9 | `schedule.calendar_id` 에 **FK 추가** | check-then-delete 레이스를 DB가 최종 차단한다 |
| D10 | `X-WR-CALNAME` / `X-WR-TIMEZONE` 출력 | 구독 시 캘린더 이름이 URL로 표시되는 문제를 해결한다 |
| D11 | RRULE `INTERVAL` 지원은 **후속 이슈** | 이번 PR은 리뷰 지적 수정 범위로 한정한다 |
| D12 | PR 본문에 호환성 파괴 변경과 DDL 절차 명시 | 배포 순서 조율에 필요하다 |

---

## 1. H1 — 종일 일정 규약과 `start < end` 불변식

### 1.1 문제

`ScheduleIcsRenderer.validateDateRange` 는 `start >= end` 인 일정에 `IllegalStateException` 을
던진다. `render()` 는 이벤트를 스트림으로 한 번에 처리하므로 한 건이 실패하면 전체가 중단되고,
`CommonExceptionHandler` 의 `@ExceptionHandler(Exception.class)` 가 이를 500으로 변환한다.
결과적으로 **일정 한 건 때문에 모든 구독자의 피드가 동시에 끊긴다.**

이 PR은 `ScheduleLogic` 의 검증을 `startDateTime.isAfter(endDateTime)` 에서
`!startDateTime.isBefore(endDateTime)` 로 바꿨다. 즉 이전 API는 `start == end` 저장을 허용했고,
이는 종일 일정을 inclusive로 표현하던 기존 규약이었다. 새 렌더러는 exclusive를 가정하므로 두
규약이 충돌한다.

### 1.2 규약 확정 (D1, D2)

`allDay=true` 일 때 `end` 는 exclusive 경계다.

| 표현 | 값 |
|------|-----|
| 저장 | `start_time = 2026-01-01T00:00`, `end_time = 2026-01-02T00:00` |
| API | `"start": "2026-01-01"`, `"end": "2026-01-02"` |
| ICS | `DTSTART;VALUE=DATE:20260101`, `DTEND;VALUE=DATE:20260102` |

`ScheduleIcsRenderer.addAllDayDates` 는 변환 없이 그대로 내보낸다. `ScheduleLogic` 의
`!startDateTime.isBefore(endDateTime)` 검증은 현 PR 상태를 유지한다.

운영 DB에 `allDay=true` 행이 없으므로 백필 UPDATE는 필요 없다. 프론트엔드는 하루짜리 종일
일정을 `end = start + 1일` 로 보내도록 함께 변경한다.

### 1.3 불변식 3계층 (D3)

**계층 1 — 응용 검증 (기존 유지).** `ScheduleLogic.validateScheduleMutation` 이 구체적인 메시지
(`"start는 end보다 빨라야 합니다."`)로 400을 반환한다. 사용자가 실제로 보는 오류다.

**계층 2 — 쓰기 매퍼 `@AfterMapping`.** MapStruct 생성 코드를 확인한 결과 두 매퍼 모두
`new Schedule()` + setter를 사용한다(`Schedule` 에 `@NoArgsConstructor` 가 있어 MapStruct가
무인자 생성자를 우선 선택한다). 따라서 생성자에 검증을 넣어도 호출되지 않는 죽은 코드가 된다.
검증은 쓰기 매퍼의 `@AfterMapping` 훅에 둔다.

```java
// slcn-spec : ScheduleMapper
@AfterMapping
default void validateDateRange(@MappingTarget Schedule schedule) {
	LocalDateTime start = schedule.getStart();
	LocalDateTime end = schedule.getEnd();
	if (start == null || end == null || !start.isBefore(end)) {
		throw new IllegalArgumentException("start는 end보다 빨라야 합니다.");
	}
}
```

`@MappingTarget Schedule` 이므로 `toSchedule(ScheduleCdo)` 와
`updateSchedule(ScheduleUdo, Schedule)` 에만 적용된다. 복원 경로인
`ScheduleJpoMapper.toDomain(ScheduleJpo)` 는 다른 모듈의 다른 매퍼이므로 영향받지 않는다.
이는 의도된 설계다 — DB에 이미 있는 행을 **읽는** 것까지 실패시키면 피드뿐 아니라 월별 조회까지
중단되어 원래 문제보다 장애 범위가 넓어진다.

`IllegalArgumentException` 은 `CommonExceptionHandler` 가 이미 400
(`"입력이 올바르지 않습니다."`)으로 매핑하므로 500이 되지 않는다. `slcn-spec` 은 최하위 레이어라
`BadRequestException`(slcn-aggregate)을 참조할 수 없으므로 `IllegalArgumentException` 을 쓴다.

**계층 3 — DB 제약.** 아래 8절의 DDL 런북에서 NOT NULL과 CHECK를 적용한다. `ScheduleJpo` 에도
`@Column(name = "start_time", nullable = false)` / `@Column(name = "end_time", nullable = false)`
를 붙여 코드와 스키마 의도를 일치시킨다. 다만 `ddl-auto=update` 는 기존 컬럼의 nullability를
바꾸지 않으므로 실제 적용은 수동 DDL이 담당한다.

### 1.4 남는 위험 (D4)

렌더러의 이벤트별 skip을 채택하지 않았으므로 다음 세 가지가 남는다. 인지된 선택이며 향후 재검토
대상이다. 이 중 CHECK 제약이 원천적으로 표현할 수 없는 것은 1번(RRULE)뿐이다. 2번은 CHECK가
표현은 할 수 있지만 이번 3계층이 datetime 단위로만 검증해 all-day의 날짜 단위 규약을 놓치는
경우이고, 3번은 3계층이 아직 배포되지 않은 시점의 문제다. 셋 다 결과적으로 "앱 mutation 경로를
거치지 않고 들어온 행"이라는 같은 범주에 속하며, RRULE만 특별한 것이 아니다.

1. **RRULE 경로는 어느 계층도 덮지 못한다.** CHECK 제약은 RRULE 유효성을 표현할 수 없다. 나중에
   `ScheduleRecurrenceRuleValidator.SUPPORTED_KEYS` 를 좁히면 기존 행이 좌초해
   `validateRecurrenceRule` → 500 → 전 구독자 피드 중단이 그대로 재현된다.
2. **all-day 행의 날짜 단위 규약은 1~3계층 중 어느 것도 검증하지 않는다.** 계층 1·2(`start.isBefore(end)`)와
   계층 3(`end_time > start_time`)은 모두 datetime 단위 비교이므로, `is_all_day = true` 이면서
   같은 날 09:00~10:00처럼 `start`/`end` 가 같은 날짜인 행도 통과시킨다. 반면
   `ScheduleIcsRenderer.validateDateRange` 는 all-day에 대해
   `start.toLocalDate().isBefore(end.toLocalDate())` 를 요구하므로 이런 행을 렌더러 단계에서만
   거부해 `500` → 전 구독자 피드 중단을 일으킨다. 현재 API는 all-day 값을 항상 `atStartOfDay()`
   로 강제하므로 API 경로로는 이런 행이 만들어지지 않는다 — 즉 3번과 마찬가지로 "앱을 거치지 않고
   DB에 직접 들어온 행" 범주이며, API가 만들 수 있는 위험이 아니다.
3. **DDL 적용 전에 유입된 위반 행이 있으면** 사전 점검 쿼리가 유일한 방어선이다.

세 위험 모두 `render()` 의 이벤트별 try/catch(skip + `log.warn`) 한 겹으로 해소된다. 이는
`ScheduleFeedFlow` 가 orphan 일정을 처리하는 방식과도 일치한다.

---

## 2. M1 — soft delete 제거 (D5)

`PUT /schedule/{id}/hide` 는 Facade 문서상 "일정 삭제(숨김)", `DELETE /schedule/{id}` 는
"일정 완전 삭제"다. 즉 `hidden` 은 사용자에게 노출되는 삭제다. 그러나 복구(un-hide) API가 없어
현재의 soft delete는 DB를 직접 다룰 때만 의미가 있다.

부수적으로 `CalendarLogic.deleteCalendar` 의 가드가 `existsByCalendarId` 로 hidden 행까지 세기
때문에, 사용자가 일정을 모두 삭제해도 캘린더는 영구히 409가 된다.

### 2.1 제거 대상

| 위치 | 변경 |
|------|------|
| `ScheduleFacade`, `ScheduleResource` | `hideSchedule` 및 `PUT /{id}/hide` 삭제 |
| `ScheduleLogic` | `hideSchedule()` 삭제 |
| `Schedule` (slcn-spec) | `hidden` 필드, `hideSchedule()` 삭제. PR이 추가한 8인자 호환 생성자 형태 변경 |
| `ScheduleJpo` | `hidden` 필드 삭제 |
| `ScheduleRepository` | `…AndHiddenFalse…` 4개 메서드에서 술어 제거 |
| `ScheduleMapper` | `@Mapping(target = "hidden", …)` 2줄 삭제 |
| `ScheduleConstant` | `DELETE_…` / `HARD_DELETE_…` 메시지 통합 |
| `CalendarLogic` 가드 | 코드 변경 없이 `existsByCalendarId` 의 의미가 정확해진다 |

### 2.2 스키마

`hidden` 은 NOT NULL이고 기본값이 없으므로 **`DROP COLUMN` 이 필수**다. 8절 런북 참조.

### 2.3 규약 분기

`Travel` 은 `hidden` 을 계속 사용한다. 두 도메인의 삭제 규약이 갈라지므로 `docs/module.md` 에
의도된 분기임을 한 줄 남긴다.

---

## 3. M2 — 무제한 쿼리

### 3.1 ICS 피드 시간 창 (D6)

`ScheduleStore.findAllNonHiddenForFeed()` 는 비숨김 전체 일정을 매 요청마다 로드해 렌더한다.
외부 캘린더가 주기적으로 폴링하므로 데이터가 쌓일수록 응답 크기와 렌더 시간이 선형 증가한다.

창 기반 조회로 교체한다.

| 종류 | 조건 |
|------|------|
| 비반복 (`recurrence_rule IS NULL`) | `end_time > windowStart AND start_time < windowEnd` |
| 반복 (`recurrence_rule IS NOT NULL`) | `start_time < windowEnd` |

반복 일정은 과거에 시작해 창 안까지 이어질 수 있으므로 하한을 두지 않는다. RRULE은 원본
그대로 내보내므로 `UNTIL` / `COUNT` 해석은 구독 클라이언트의 몫이다.

설정값:

| 환경변수 | 기본값 |
|----------|--------|
| `SLCN_ICS_WINDOW_PAST_MONTHS` | 12 |
| `SLCN_ICS_WINDOW_FUTURE_MONTHS` | 24 |

**창 경계는 월 초로 스냅한다.** `now()` 기준으로 잡으면 요청마다 경계가 미세하게 밀려 본문
해시가 달라지고 ETag/304가 무력화된다. 스냅하면 같은 달 안에서 ETag가 안정된다.

부작용: 창 밖 과거 일정이 구독 캘린더에서 사라진다. ICS 피드로는 일반적인 동작이며 기본값이
넉넉해 실사용 영향은 작다. `docs/schedule-ics-feed.md` 에 명시한다.

### 3.2 반복 후보 조회 인덱스 (D7)

`findAllByStartBeforeAndHiddenFalseAndRecurrenceRuleIsNotNull(rangeEnd)` 는 `UNTIL` / `COUNT` 로
이미 종료된 반복까지 매번 로드한다. 정확히 자르려면 `recurrence_end` 파생 컬럼이 필요한데
`COUNT` 소진 계산이 얽혀 비용이 크다. 이번에는 인덱스만 추가하고 파생 컬럼은 후속 이슈로 둔다.

`TravelJpo` 가 이미 쓰는 `@Index` 패턴을 따른다.

```java
@Table(name = "schedule", schema = "slcn", indexes = {
	@Index(name = "idx_schedule_start_end", columnList = "start_time,end_time"),
	@Index(name = "idx_schedule_calendar_id", columnList = "calendar_id")
})
```

`calendar_id` 인덱스는 `existsByCalendarId` 의 풀스캔도 함께 해소한다.

---

## 4. M3 — `feedUrl` 이 내부 주소로 반환되는 문제 (D8)

`application.yml` 의 `forward-headers-strategy` 기본값은 `none` 인데
`ScheduleFeedResource.createFeed` 는 `ServletUriComponentsBuilder.fromCurrentContextPath()` 로
URL을 만든다. 리버스 프록시 뒤 기본 배포에서는 `http://internal:8080/api/...` 형태가 반환되어
그대로는 구독할 수 없다.

`SLCN_PUBLIC_BASE_URL` 설정을 추가한다. 값이 있으면 그것으로 조립하고, 없으면 현재처럼
`ServletUriComponentsBuilder` 로 폴백한다. `forward-headers-strategy: none` 이라는 안전한
기본값은 유지한다.

---

## 5. M4 — 캘린더 삭제 레이스 (D9)

`CalendarLogic.deleteCalendar` 는 `existsByCalendarId` 확인 후 `delete` 를 호출한다.
`ScheduleJpo.calendarId` 는 FK 없는 단순 `String` 컬럼이므로, 확인과 커밋 사이에 다른 트랜잭션이
같은 캘린더로 일정을 등록하면 고아 일정이 생긴다. 이 PR이 추가한 409 가드가 막으려던 상황이
그대로 재현된다.

FK를 추가해 DB가 최종적으로 차단한다(8절 런북). FK 도입 후 `CalendarLogic` 의 409 가드는
사용자에게 친절한 메시지를 주는 역할로 정리된다. 레이스가 실제로 발생했을 때
`DataIntegrityViolationException` 이 500으로 새지 않도록 `CommonExceptionHandler` 에 핸들러를
추가한다(409 응답).

---

## 6. H2 — PR 본문 상세화 (D12)

`gh pr edit 5 --body-file <파일>` 로 갱신한다. 추가할 섹션:

**호환성 파괴 변경**

| 변경 | 영향 |
|------|------|
| `start == end` 등록·수정이 400 | 0분 일정을 만들던 클라이언트 |
| 종일 일정 `end` 가 exclusive로 전환 | 프론트엔드 필수 변경 |
| `PUT /schedule/{id}/hide` 제거 | 프론트엔드 필수 변경 |
| `DELETE /schedule/{id}` 가 영구 삭제 | 복구 불가 |
| 참조 중인 캘린더 삭제가 409 | 관리 화면 |

**수동 DDL 절차** — 8절 런북을 그대로 인용한다.

**배포 순서** — 9절을 인용한다.

---

## 7. L1 ~ L3

### 7.1 L1 — RRULE `INTERVAL` 미지원 (D11)

`ScheduleRecurrenceRuleValidator.SUPPORTED_KEYS` 에 `INTERVAL` 이 없어 격주·격월 반복이
불가능하다. `docs/schedule-recurrence.md` 에 명시된 의도된 스코프이므로 이번 범위에서 제외하고
후속 이슈로 등록한다. 실제 작업량은 작다 — `SUPPORTED_KEYS` 에 키를 추가하고 양의 정수 검증을
넣으면 되며, 확장은 ical4j가, ICS 출력은 원본 RRULE 통과라 추가 작업이 없다.

### 7.2 L2 — `System.setProperty` 반복 호출

`ScheduleIcsRenderer.render()` 가 매 요청마다 `disableTimeZoneUpdates()` 를 호출해 JVM 전역
프로퍼티를 건드린다. ical4j의 원격 타임존 조회를 막는 것 자체는 옳으나 요청마다 할 이유가 없다.
`render()` 안의 호출을 제거하고 static 초기화 블록만 남긴다.

### 7.3 L3 — `X-WR-CALNAME` 과 호환성 게이트 (D10)

현재 피드에는 `X-WR-CALNAME` 이 없어 구독 시 클라이언트가 캘린더 이름을 URL로 표시할 수 있다.
`ScheduleFeedToken.name`("가족 캘린더" 등)을 렌더러에 전달해 `X-WR-CALNAME` 과
`X-WR-TIMEZONE:Asia/Seoul` 을 출력한다.

시그니처가 `render(events)` → `render(feedName, events)` 로 바뀌고 ETag가 피드별로 갈라진다.
`ScheduleFeedFlow.getFeedEvents` 가 토큰 이름도 함께 반환하도록 조정한다.

실기기 검증(Apple macOS/iOS, Google web/mobile)은 공개 HTTPS 배포 후에만 가능하다. 체크리스트를
`docs/schedule-ics-feed.md` 에 남기고 PR 본문에서 참조한다. 이 게이트를 통과하기 전에는 외부
공지를 하지 않는다.

---

## 8. 수동 DDL 런북

`ddl-auto=update` 는 CHECK 제약 추가, nullability 변경, 컬럼 삭제, FK 추가를 수행하지 않는다.
아래를 순서대로 수동 실행한다. 각 점검 쿼리의 결과가 `0` 이어야 다음 단계로 넘어간다.

### 8.1 사전 점검 (배포 전)

```sql
-- P1. 시간 범위 위반. 0이어야 한다.
SELECT count(*) FROM slcn.schedule
 WHERE start_time IS NULL OR end_time IS NULL OR end_time <= start_time;

-- P2. 고아 일정. 0이어야 한다.
SELECT count(*) FROM slcn.schedule s
 WHERE NOT EXISTS (SELECT 1 FROM slcn.calendar c WHERE c.id = s.calendar_id);

-- P3. 종일 일정. 0이면 백필 불필요(D2의 전제 재확인).
SELECT count(*) FROM slcn.schedule WHERE is_all_day = true;

-- P4. hidden=true(soft delete) 행. 건수와 내용을 눈으로 확인한다.
SELECT id, calendar_id, title, start_time, end_time
  FROM slcn.schedule WHERE hidden = true;
```

P1 또는 P2가 0이 아니면 중단하고 데이터를 먼저 정리한다. P3가 0이 아니면 D2가 성립하지 않으므로
설계를 재검토한다.

**P4에 행이 나오면 9절 3단계에서 처리한다.** 이 PR은 soft delete(`hidden`)를 제거하고 4개
`ScheduleRepository` 조회 메서드에서 `…AndHiddenFalse…` 술어를 뺐다. 신 배포본이 뜨는 순간부터
`hidden = true` 인 행은 평범한 행으로 취급되어 월별 조회와 모든 구독자의 ICS 피드에 다시
나타난다 — 사용자가 "삭제"했다고 믿는 일정이 되살아난다. `DROP COLUMN hidden`(8.2)은 컬럼만
지울 뿐 이미 값이 채워진 행 자체를 지우지 않으므로 이 부활은 `DROP COLUMN` 으로 막을 수 없고,
DDL 적용 시점과 무관하게 신 배포본이 뜨는 즉시 일어난다. 부수적으로 `existsByCalendarId` 도 이
행들을 정상 행으로 세게 되어 M1에서 의도한 캘린더 삭제 409 해소가 무력화된다(이미 hidden
상태였던 일정이 있는 캘린더는 여전히 영구 409).

2인 서비스라 이 행은 많아야 몇 건이다. 별도 백업 테이블을 만들지 않고 위 `SELECT` 결과를 그대로
기록해 두는 것으로 충분하다 — 되살리고 싶으면 그 값으로 다시 등록하면 된다.

### 8.2 적용 (애플리케이션 배포와 함께)

```sql
BEGIN;

-- 1. start/end NOT NULL 전환
ALTER TABLE slcn.schedule ALTER COLUMN start_time SET NOT NULL;
ALTER TABLE slcn.schedule ALTER COLUMN end_time   SET NOT NULL;

-- 2. 시간 범위 CHECK
ALTER TABLE slcn.schedule
  ADD CONSTRAINT ck_schedule_time_range CHECK (end_time > start_time);

-- 3. 캘린더 FK
ALTER TABLE slcn.schedule
  ADD CONSTRAINT fk_schedule_calendar
  FOREIGN KEY (calendar_id) REFERENCES slcn.calendar(id);

-- 4. soft delete 컬럼 제거 (신규 배포본은 이 컬럼을 채우지 않는다)
ALTER TABLE slcn.schedule DROP COLUMN hidden;

COMMIT;
```

`DROP COLUMN hidden` 은 신규 배포본이 뜬 **뒤에** 실행해야 한다. 구 배포본이 살아 있는 동안
컬럼을 지우면 구 인스턴스의 INSERT가 실패한다. 반대로 구 배포본이 죽은 뒤 신 배포본이
`hidden` 을 채우지 않은 채 INSERT하면 NOT NULL 위반이 난다. 따라서 9절의 순서를 지킨다.

### 8.3 롤백

```sql
ALTER TABLE slcn.schedule DROP CONSTRAINT IF EXISTS ck_schedule_time_range;
ALTER TABLE slcn.schedule DROP CONSTRAINT IF EXISTS fk_schedule_calendar;
ALTER TABLE slcn.schedule ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE slcn.schedule ALTER COLUMN end_time   DROP NOT NULL;
ALTER TABLE slcn.schedule ADD COLUMN hidden boolean NOT NULL DEFAULT false;
```

`hidden` 을 복원할 때는 기본값을 반드시 함께 지정한다. 원본에는 기본값이 없었으나, 복원 시점의
구 배포본이 이 컬럼을 채우지 못하면 INSERT가 실패하기 때문이다.

**주의 — 이 롤백은 삭제된 데이터를 복원하지 않는다.** 9절 3단계에서 `hidden = true` 행을
`DELETE` 했다면, 위 롤백 스크립트로 컬럼과 제약을 되돌려도 그 행 자체는 돌아오지 않는다
(`DROP COLUMN` 과 `DELETE` 는 서로 별개의 되돌릴 수 없는 작업이다). 복구는 3단계 전에 기록해 둔
8.1 P4의 `SELECT` 결과로 다시 등록하는 방식이며, 이 런북은 그 작업을 포함하지 않는다.

---

## 9. 배포 순서

`hidden` 컬럼과 종일 규약 두 가지가 배포 순서를 제약한다. 또한 **이 배포는 무중단이 아니다.**
8.2는 `ALTER COLUMN … SET NOT NULL`, `ADD CONSTRAINT … CHECK`, `ADD CONSTRAINT … FOREIGN KEY`
를 한 트랜잭션에서 실행하는데, 이 DDL들은 기존 행을 스캔하는 동안 `slcn.schedule` 에 ACCESS
EXCLUSIVE 락을 잡고 FK 추가는 `slcn.calendar` 에도 같은 락을 건다. 그 스캔이 끝날 때까지 ICS
피드 GET을 포함한 해당 테이블의 모든 읽기·쓰기가 대기한다. 따라서 8.2는 "무중단"이 아니라
**"짧게 차단된다"** 로 취급한다. 트래픽이 적은 시간대에 실행하고, 스캔 시간은 실제 운영 DB 행
수를 기준으로 스테이징에서 사전에 측정해 차단 구간을 공지한다.

1. **사전 점검** — 8.1의 P1·P2·P3·P4 실행, 모두 조건 충족 확인. P4가 0이 아니면 무시하지 않고
   3단계에서 처리한다.
2. **`hidden` 에 DB 기본값 부여** — `ALTER TABLE slcn.schedule ALTER COLUMN hidden SET DEFAULT false;`
   이 단계가 있어야 신 배포본이 컬럼을 채우지 않아도 INSERT가 성공하며, 구·신 배포본이 공존할 수 있다
3. **`hidden = true` 행 처리** — soft delete 제거로 이 행들이 신 배포본에서는 평범한 행이 되어
   즉시 되살아나므로, **4단계(백엔드 배포) 직전에** 처리한다. 8.1 P4의 `SELECT` 결과를 보고
   행마다 판단한다.
   - 되살아나면 안 되는 일정(사용자가 삭제한 것): 삭제한다.
   - 되살아나도 되는 일정: 그대로 둔다. 배포 후 피드에 다시 나타나는 것이 의도한 결과다.
   ```sql
   -- 삭제하기로 한 경우. 실행 전 P4의 SELECT 결과를 따로 기록해 둔다.
   DELETE FROM slcn.schedule WHERE hidden = true;
   ```
   2인 서비스라 대상 행이 많아야 몇 건이므로 별도 백업 테이블을 만들지 않는다. 복구가 필요하면
   기록해 둔 `SELECT` 결과로 다시 등록한다. P4가 0이면 이 단계는 건너뛴다.

   구 배포본이 완전히 내려가기 전까지는 `PUT /schedule/{id}/hide` 가 계속 살아 있어 새로운
   `hidden = true` 행이 생길 수 있다. 4단계에서 구 배포본을 종료한 직후 P4를 한 번 더 실행해
   남은 행이 없는지 확인한다.
4. **백엔드 배포** — 신 배포본 기동, 구 배포본 종료
5. **프론트엔드 배포** — `hide` 호출 제거, 종일 일정 `end` 를 exclusive로 전환
6. **8.1 P1·P2 재점검** — 4·5단계 구간에서도 구 배포본이 `start == end` 저장을 허용하고(구
   배포본은 아직 구 검증 로직을 쓴다), calendar_id에 FK가 없어 M4 레이스도 여전히 열려 있다.
   따라서 1단계의 사전 점검은 이 시점에는 이미 낡은 정보다. **7단계(8.2)를 실행하기 직전에
   P1·P2를 다시 실행**해 둘 다 0인지 확인한다. 위반 행이 있으면 8.2를 실행하지 말고 먼저 그
   데이터를 정리한다 — 8.2는 하나의 `BEGIN`/`COMMIT` 이므로 `ADD CONSTRAINT` 가 실패하면
   `DROP COLUMN hidden` 을 포함한 블록 전체가 롤백되어 배포가 중간에 멈춘다.
7. **DDL 적용** — 8.2 실행 (`DROP COLUMN hidden` 포함)
8. **검증** — ICS 피드 200 응답, ETag/304 동작, 일정 등록·수정·삭제, 캘린더 삭제 409
9. **실기기 호환성 게이트** — 7.3 체크리스트

2단계는 7단계(8.2)에 포함하지 않고 먼저 실행한다는 점에 주의한다. 배포 중 구·신 배포본이 함께
떠 있는 구간을 넘기기 위한 조치다. 3단계는 4단계(백엔드 배포) 직전에 실행하고, 구 배포본 종료
직후 P4를 한 번 더 확인해 "신 배포본이 뜬 순간 숨김 일정이 되살아나는" 구간을 없앤다.

---

## 10. 테스트 전략

TDD로 진행한다. 모듈별 실행 후 마지막에 `./gradlew test` 로 마무리한다.

| 대상 | 테스트 |
|------|--------|
| `ScheduleMapper` | `@AfterMapping` 검증 — `start == end`, `start > end`, null 각각 `IllegalArgumentException` |
| `ScheduleJpoMapper` | 복원 경로는 검증하지 **않음** 을 보장 (위반 값이 들어와도 예외 없이 매핑) |
| `ScheduleIcsRenderer` | 종일 exclusive 렌더링, `X-WR-CALNAME` / `X-WR-TIMEZONE` 출력 |
| `ScheduleStore` | 피드 창 경계 — 창 밖 비반복 제외, 창 밖에서 시작한 반복 포함 |
| ETag | 같은 달 안에서 창 스냅으로 ETag가 안정적인지 |
| `CalendarLogic` | 일정이 있는 캘린더 삭제 409, 일정이 없으면 삭제 성공 |
| `ScheduleFeedResource` | `SLCN_PUBLIC_BASE_URL` 설정 시·미설정 시 `feedUrl` |
| 회귀 | `hidden` 제거로 깨지는 기존 테스트 정리, `hide` 엔드포인트 제거에 따른 보안 매트릭스 갱신 |

---

## 11. 후속 이슈

| 항목 | 내용 |
|------|------|
| RRULE `INTERVAL` 지원 | 격주·격월 반복. 검증기에 키 추가 + 양의 정수 검증 |
| `recurrence_end` 파생 컬럼 | 종료된 반복을 조회 단계에서 제외 |
| 렌더러 이벤트별 skip | 1.4의 잔여 위험 해소 |
| Schedule 복구 기능 | soft delete 제거로 사라진 복구 경로가 필요해질 경우 휴지통 모델로 재설계 |
