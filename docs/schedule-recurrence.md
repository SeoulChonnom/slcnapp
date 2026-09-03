# Schedule 반복 일정 API

기존 Schedule API는 반복 일정의 원본(master) 하나만 저장하고, 조회 범위에 맞는 occurrence를 서버에서 계산한다. occurrence를 별도 DB 행으로 저장하지 않으므로 기존 `/api/schedule` 경로와 상태 코드는 그대로 유지된다.

## JSON 계약

`POST /api/schedule`와 `PUT /api/schedule` 요청은 기존 필드와 함께 선택적인 `recurrenceRule`을 받을 수 있다. 값은 RFC 5545 RRULE의 value-part만 사용하며 `RRULE:` 접두사는 포함하지 않는다.

```json
{
  "calendarId": "CALENDAR-0001",
  "title": "주간 약속",
  "body": "",
  "start": "2026-09-01T19:00:00+09:00",
  "end": "2026-09-01T20:00:00+09:00",
  "allDay": false,
  "location": "",
  "recurrenceRule": "FREQ=WEEKLY;BYDAY=TU"
}
```

수정 요청은 기존 계약대로 `id`에 Schedule master ID를 전달한다.

```json
{
  "id": "SCHEDULE-0001",
  "calendarId": "CALENDAR-0001",
  "title": "주간 약속",
  "body": "",
  "start": "2026-09-01T19:00:00+09:00",
  "end": "2026-09-01T20:00:00+09:00",
  "allDay": false,
  "location": "",
  "recurrenceRule": "FREQ=WEEKLY;BYDAY=TU"
}
```

등록·수정 mutation 응답에는 기존 필드를 유지하면서 다음 nullable 필드를 추가한다. mutation은 Schedule master를 반환하므로 `occurrenceId`는 `null`이다.

```json
{
  "id": "SCHEDULE-0001",
  "calendarId": "CALENDAR-0001",
  "title": "주간 약속",
  "body": "",
  "start": "2026-09-01T19:00:00+09:00",
  "end": "2026-09-01T20:00:00+09:00",
  "allDay": false,
  "location": "",
  "recurrenceRule": "FREQ=WEEKLY;BYDAY=TU",
  "occurrenceId": null
}
```

기간 조회에서 서버가 계산한 반복 occurrence 응답에는 master ID를 유지하면서 `occurrenceId`가 채워진다.

```json
{
  "id": "SCHEDULE-0001",
  "start": "2026-09-01T19:00:00+09:00",
  "end": "2026-09-01T20:00:00+09:00",
  "recurrenceRule": "FREQ=WEEKLY;BYDAY=TU",
  "occurrenceId": "SCHEDULE-0001/2026-09-01T19:00:00+09:00"
}
```

반복이 아닌 Schedule은 `recurrenceRule`과 `occurrenceId`가 모두 `null`이다. `recurrenceRule`을 생략하거나 `null`, 공백 문자열로 보내면 반복 없음으로 정규화되어 DB에는 `NULL`로 저장된다. 유효한 nonblank value-part는 대소문자와 순서를 포함해 입력 문자열을 그대로 보존한다.

## 지원하는 RRULE 부분집합

다음 key만 지원한다.

| Key | 지원 값 |
| --- | --- |
| `FREQ` | `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY` |
| `COUNT` | 1 이상의 양의 정수. `UNTIL`과 동시에 사용할 수 없음 |
| `UNTIL` | `allDay=true`는 `YYYYMMDD`, 시간 일정은 UTC `YYYYMMDDTHHMMSSZ` |
| `BYDAY` | `MO`~`SU`, 쉼표로 여러 요일 지정. RFC ordinal(예: `-1SU`, `2TU`)은 `MONTHLY`·`YEARLY`에서만 지원 |
| `BYMONTHDAY` | `1`~`31`, RFC leading-plus 형식인 `+1`~`+31`, 또는 `-1`에서 `-31` 사이, 쉼표로 여러 값 지정 |

`COUNT`와 `UNTIL`을 생략하면 반복은 무기한으로 간주하지만, 조회는 기존과 동일하게 최대 1개월 범위 안에서만 확장한다. 반복 계산과 occurrence ID의 시간대는 `Asia/Seoul`이다.

`EXDATE`, `RDATE`, `RECURRENCE-ID` 및 특정 occurrence만 수정하는 예외 규칙은 현재 지원하지 않는다. 지원하지 않는 key, frequency, 값 형식, `RRULE:` 접두사 또는 중복/빈 value-part는 기존 Schedule 검증 응답으로 거부한다.

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{"success":false,"message":"올바르지 않은 반복 일정 규칙입니다."}
```

## 조회와 mutation 의미

`GET /api/schedule`와 `GET /api/schedule/now`는 반복 master를 요청 범위의 occurrence로 확장한다. 원본 `start`가 요청 범위보다 앞서더라도 해당 범위와 겹치는 occurrence가 반환된다. 각 occurrence의 `id`는 mutation을 위해 원본 master의 Schedule ID를 그대로 사용하고, 화면에서 occurrence를 구분할 때는 안정적인 `occurrenceId`를 사용한다.

`occurrenceId` 형식은 다음과 같다.

```text
{master Schedule ID}/{occurrence 시작 시각(Asia/Seoul)의 ISO offset}
```

따라서 PUT 수정, hide, DELETE는 occurrenceId가 아니라 master Schedule ID를 대상으로 하며 시리즈 전체에 적용된다. occurrence는 조회 시 계산될 뿐 저장·수정·삭제되지 않는다.

## 배포 전 DB precheck

Task 1에서 확인한 이 worktree에는 `SLCN_POSTGRESQL_URL`, `SLCN_POSTGRESQL_USER`, `SLCN_POSTGRESQL_PW` 설정과 `psql` 클라이언트가 없어 운영 PostgreSQL의 실제 상태를 조회하지 못했다. 스키마 상태를 추정하거나 변경하지 않았으므로 배포 전에 대상 DB에서 다음 쿼리를 실행한다.

```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'slcn'
  AND table_name = 'schedule'
  AND column_name = 'recurrence_rule';
```

`recurrence_rule`이 존재하면 null 개수와 대표 값을 추가로 확인하고 기존 데이터를 삭제하거나 재작성하지 않는다. 애플리케이션 매핑은 nullable PostgreSQL `text` 컬럼을 전제로 한다.
