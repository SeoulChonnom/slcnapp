# 임장 기록 API (FE 연동)

context path는 `/api`다. 아래 경로는 그 뒤에 붙는다. 인증은 `X-AUTH-TOKEN` 헤더 또는
`Authorization: Bearer ...`를 쓴다.

설계 근거와 내부 구조는 `docs/field_research/implementation_design.md`를 본다.
이 문서는 **FE가 호출할 때 알아야 하는 것**만 담는다.

> 문서 안의 상대 경로 링크는 모두 **BE 저장소(`slcnapp`) 기준**이다. FE 저장소로 복사해 읽을 때는
> 링크를 따라갈 수 없으니 원본 저장소에서 본다.

**페이지네이션 공통 규약.** 목록 3종(`/inspection-areas`, `/inspection-visits`, 회차 이어받기)이
같은 문법을 쓴다.

- `?page=` 0-base, 기본 `0`. **음수면 `400`**
- `?size=` 기본 `20`, 상한 `100`. 상한을 넘기면 400이 아니라 **100으로 깎는다.** 0 이하는 기본값
- 응답은 `{ "items": [...], "totalCount": 0, "hasNext": false }`

---

## 1. 화면과 API의 대응

| 화면 | 호출 |
| --- | --- |
| 지역 목록 | `GET /inspection-areas` |
| 지역 상세(회차 탭) | `GET /inspection-areas/{areaId}` — 회차 목록 + 선택 회차 상세를 한 번에 |
| 임장 목록 | `GET /inspection-visits` |
| 임장 상세 | `GET /inspection-visits/{visitId}` |
| 매물 상세 | `GET /inspection-properties/{propertyId}` (또는 `GET /inspection-visits/{visitId}/properties/{propertyId}`) |
| 회차 간 매물 연결 | `GET /inspection-areas/{areaId}/properties` |
| 질문 관리(관리자) | `GET/POST/PUT/PATCH /inspection-questions` |

지역 상세 하나로 회차 탭 전환까지 처리된다. 회차를 바꿀 때 `?visitId=`만 갈아끼우면 되고,
회차가 많아 지연 로딩으로 전환할 때는 `?includeProperties=false`로 목록만 받는다.

---

## 2. 임장 지역

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-areas` | 목록 + 회차 집계. `?keyword=` `?revisitIntent=` `?sort=` `?page=` `?size=` |
| GET | `/inspection-areas/{areaId}` | 상세. `?visitId=`, `?includeProperties=`(기본 true) |
| GET | `/inspection-areas/{areaId}/properties` | 지역 전체 매물 경량 목록. `?complexName=` `?name=` |
| POST | `/inspection-areas` | 등록 |
| PUT | `/inspection-areas/{areaId}` | 수정 |
| DELETE | `/inspection-areas/{areaId}` | 임장 기록이 1건이라도 있으면 `409` |

**지역은 생활권 단위다.** "성수동", "잠실"이 들어가고 "트리마제" 같은 단지명은 매물의
`complexName`으로 내려간다.

동일 지역명이 이미 있으면 `409`와 함께 메시지에 기존 `areaId`가 담긴다. FE는 이걸 잡아
"기존 지역에 임장 추가"를 고르게 한다.

```json
POST /api/inspection-areas          { "name": "성수동", "description": "서울숲 ~ 뚝섬역 주변" }
PUT  /api/inspection-areas/{areaId} { "name": "성수동", "description": "..." }
→ 지역 목록 한 행과 같은 형태(집계 필드는 0/null)
DELETE /api/inspection-areas/{areaId} → 204
```

`DELETE`는 **하드 삭제**다. 임장 기록이 1건이라도 있으면 `409`라 사실상 빈 지역만 지워진다.

### 지역 목록 검색·정렬·필터

- `keyword` — **지역명 · 지역 설명 · 단지/건물명 · 매물명 · 태그 이름** 5종을 훑는다. **대소문자를 무시한다.**
  매물에 걸리면 그 지역 행의 `matchedProperty`가 채워진다
- `revisitIntent` — `YES|MAYBE|NO`. **최신 회차 기준**이다
- `sort` — `RECENT_VISIT`(기본) / `VISIT_COUNT` / `TOP_INTEREST`.
  임장이 0건인 지역은 어느 정렬에서든 맨 뒤다

> 동점일 때의 2차 키는 지역명 오름차순인데, DB collation이 `en_US.utf8`이라
> **한글이 가나다순으로 정렬되지 않는다.** 같은 조건이면 항상 같은 순서라 페이징은 안전하지만,
> 동점이 많은 `VISIT_COUNT`에서 표시 순서가 어색할 수 있다.

### 지역 목록 응답

```json
{
  "items": [
    {
      "areaId": "INSPECTION_AREA-0001",
      "name": "성수동",
      "description": "서울숲 ~ 뚝섬역 주변",
      "visitCount": 3,
      "firstVisitedAt": "2026-07-02T10:00",
      "lastVisitedAt": "2026-09-17T14:00",
      "totalPropertyCount": 7,
      "latestVisit": {
        "visitId": "INSPECTION_VISIT-0003",
        "visitedAt": "2026-09-17T14:00",
        "oneLineReview": "직주근접은 좋지만 가격이 부담",
        "revisitIntent": "YES",
        "status": "DRAFT",
        "tags": ["서울숲", "한강"]
      },
      "topProperty": {
        "propertyId": "8a71c0e2-...",
        "complexName": "트리마제",
        "name": "101동 1203호 / 84A",
        "interestLevel": 5
      },
      "matchedProperty": null,
      "incompleteSummary": { "draftVisitCount": 1, "draftPropertyCount": 2, "unansweredRequiredCount": 5 },
      "thumbnails": [{ "id": "...", "fileAssetId": "...", "file": { "...": "FileAssetRdo" } }],
      "totalImageCount": 24
    }
  ],
  "totalCount": 6,
  "hasNext": false,
  "revisitIntentCounts": { "total": 6, "YES": 3, "MAYBE": 1, "NO": 1 },
  "totals": { "areaCount": 6, "visitCount": 10, "propertyCount": 17 }
}
```

- `topProperty`는 **지역 전체**에서 고른다. 관심도 동점이면 **최신 회차**가 이긴다.
  임장 목록의 `topInterestProperty`는 그 회차 안에서만 고른다
- `matchedProperty`는 `keyword`가 **매물에** 걸렸을 때만 채워진다
  (`{ propertyId, visitId, complexName, name, interestLevel }`). 여러 건이 걸리면
  관심도 내림차순 → 최신 회차 → `sortOrder` 순으로 1건. 지역명·설명·태그로만 걸렸으면 `null`이고
  기존 `topProperty`를 그대로 쓴다
- `latestVisit`에는 `propertyCount`/`cover`/`incompleteSummary`가 비어 있다. 회차별 값이 필요하면 지역 상세를 부른다
- 집계 필드는 전부 서버가 조회 시 계산한다. 임장을 등록/삭제하면 다음 조회에 즉시 반영된다

**`revisitIntentCounts`와 `totals`는 `keyword`·`revisitIntent`·`page` 어느 것에도 영향받지 않는다.**
필터 칩의 N과 헤더 요약은 필터 전 값이라 필터된 목록을 세어서는 만들 수 없기 때문이다.

**`revisitIntentCounts.total`은 `YES + MAYBE + NO`와 다를 수 있다.** `total`은 지역 총 수이고,
셋은 최신 회차의 `revisitIntent`가 실제로 채워진 지역만 센다. 임장이 0건인 지역과 최신 회차가
DRAFT라 아직 의사를 안 적은 지역이 그 차이를 만든다. **`전체 N`에는 `total`을 쓴다.**

### 지역 상세 응답

```json
{
  "area": { "...": "지역 목록 한 행과 같은 형태" },
  "visits": [
    {
      "visitId": "INSPECTION_VISIT-0003",
      "visitedAt": "2026-09-17T14:00",
      "oneLineReview": "...",
      "revisitIntent": "YES",
      "status": "DRAFT",
      "tags": ["서울숲"],
      "propertyCount": 3,
      "incompleteSummary": { "draftPropertyCount": 2, "unansweredRequiredCount": 5, "visitMissingFields": [] }
    }
  ],
  "hasMoreVisits": false,
  "selectedVisit": { "...": "GET /inspection-visits/{visitId}와 같은 타입" }
}
```

- `visits`는 `visitedAt` 내림차순이고 **50건을 넘으면 최신 50건으로 잘린다.** 이때 `hasMoreVisits: true`가 온다.
  51번째부터는 `GET /inspection-visits?areaId={areaId}&page=1&size=50`으로 이어받는다 —
  **`size=50`을 명시해야 한다.** 절단 개수는 50인데 페이지 기본값은 20이라 생략하면 21~40번째를 받는다.
  두 조회의 정렬은 `visitedAt` 내림차순 → `visitId` 오름차순으로 **같으므로**, `visitedAt`이 동률이어도
  경계에서 중복이나 누락이 생기지 않는다.
- `visitId`를 안 주면 최신 회차가 `selectedVisit`에 펼쳐진다.
- `selectedVisit`은 임장 상세와 **같은 타입**이다. 지연 로딩으로 전환해도 FE가 다루는 모양이 하나다.

---

## 3. 임장 기록

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-visits` | 목록. `visitedAt` 내림차순 |
| GET | `/inspection-visits/{visitId}` | 상세. 매물·문답·사진 포함 |
| POST | `/inspection-visits` | 등록. 항상 `DRAFT`로 생성 |
| PUT | `/inspection-visits/{visitId}` | 기본 정보·태그·사진 수정 |
| PATCH | `/inspection-visits/{visitId}/status` | `COMPLETED` / `DRAFT` 전이 |
| PUT | `/inspection-visits/{visitId}/properties/order` | 매물 순서 일괄 변경 |
| PUT | `/inspection-visits/{visitId}/images/order` | 사진 순서 일괄 변경 |
| DELETE | `/inspection-visits/{visitId}` | 하위 매물·문답·태그·사진 함께 삭제 |

목록 필터(전부 선택): `areaId`, `status`, `revisitIntent`, `tag`(복수 지정 가능, **AND**),
`from`, `to`. 기간은 `2026-09-01T00:00:00` 형태의 ISO local date-time이며 양끝을 포함한다.
여기에 `page`/`size`가 붙고 응답은 `{ items, totalCount, hasNext }` 래퍼다.
`tag`는 이름으로 보내며 **대소문자까지 정확히 일치**해야 한다(자동완성으로 고른 값을 그대로 보낸다).

### 등록 요청

```json
POST /api/inspection-visits
{
  "areaId": null,
  "area": { "name": "성수동", "description": "서울숲 ~ 뚝섬역 주변" },
  "visitedAt": "2026-09-17T14:00:00",
  "memo": "서울숲 접근성 좋음",
  "revisitIntent": "YES",
  "oneLineReview": "직주근접과 분위기는 좋지만 가격이 부담됨",
  "pros": "- 서울숲 접근성\n- 강남 이동 편함",
  "cons": "- 가격대가 높음",
  "tags": ["서울숲", "한강", "직주근접"],
  "files": [
    { "fileAssetId": "file-1", "role": "COVER", "caption": "서울숲 입구" },
    { "fileAssetId": "file-2", "role": "GALLERY", "caption": "뚝섬역 상권" }
  ]
}
```

### 수정·상태 전이 요청

```json
PUT /api/inspection-visits/{visitId}
// 등록 요청과 같은 필드에서 areaId/area만 빠진다. PUT은 전체 교체라 생략 = null이다.
{ "visitedAt": "2026-09-17T14:00:00", "memo": "...", "revisitIntent": "YES",
  "oneLineReview": "...", "pros": "...", "cons": "...", "tags": [...], "files": [...] }

PATCH /api/inspection-visits/{visitId}/status         { "status": "COMPLETED" }
PATCH .../properties/{propertyId}/status              { "status": "COMPLETED" }
PUT   .../properties/{propertyId}
{ "complexName": "트리마제", "name": "101동 1203호 / 84A", "interestLevel": 5,
  "memo": "...", "oneLineReview": "...", "pros": "...", "cons": "...", "tags": [...], "files": [...] }
```

`status` 전이 응답은 각각 **임장 상세**와 **매물 상세**다. 바뀐 상태를 그대로 받아 배지를 갱신한다.

`tags`/`files`는 생략하면 유지, 보내면 통째로 치환이다(§7·§8). 나머지 스칼라는 전체 교체다.

- `areaId` 또는 `area` 중 **하나는 필수**다. `area`를 보내면 지역이 함께 생성된다.
- `visitedAt`은 `DRAFT`에서도 필수다. 목록 정렬 축이기 때문이다.
- `revisitIntent`는 `DRAFT`에서 비워도 되지만 **완료할 때는 필수**다.
- `files`의 `targetType`/`targetId`는 보내지 않아도 된다. 서버가 경로에서 확정한다.

---

## 4. 확인 매물과 문답

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/inspection-visits/{visitId}/properties` | 등록. 활성 질문 문답이 함께 생성된다 |
| GET | `/inspection-properties/{propertyId}` | 상세. `visitId` 없이 부른다 |
| GET | `/inspection-visits/{visitId}/properties/{propertyId}` | 상세. 위와 **같은 타입**을 돌려준다 |
| GET | `/inspection-visits/{visitId}/complex-names` | 단지명 자동완성. `?scope=VISIT|AREA`(기본 `VISIT`) |
| PUT | `/inspection-visits/{visitId}/properties/{propertyId}` | 기본 정보·관심도·태그·사진 수정 |
| PUT | `/inspection-visits/{visitId}/properties/{propertyId}/answers` | 문답 일괄 저장 |
| PATCH | `/inspection-visits/{visitId}/properties/{propertyId}/status` | 상태 전이 |
| DELETE | `/inspection-visits/{visitId}/properties/{propertyId}` | 삭제 |

`complexName`과 `name`은 **둘 다 필수**다. 단지명은 자유 입력이므로 표기가 흔들리면
단지별 그룹핑이 깨진다. 매물 등록 화면에서 `complex-names`를 자동완성 후보로 쓴다.

### 등록 응답 (문답 스냅샷 포함)

```json
POST /api/inspection-visits/INSPECTION_VISIT-0001/properties
{ "complexName": "트리마제", "name": "101동 1203호 / 84A" }
→ 200
{
  "propertyId": "8a71c0e2-...",
  "complexName": "트리마제",
  "name": "101동 1203호 / 84A",
  "interestLevel": null,
  "status": "DRAFT",
  "sortOrder": 1,
  "answers": [
    {
      "questionId": "INSPECTION_QUESTION-0001",
      "questionVersionNo": 2,
      "question": "거실 및 방의 채광은 어떤가?",
      "description": "오후 시간대 기준으로 기록",
      "answerType": "LONG_TEXT",
      "required": true,
      "sortOrder": 1,
      "answered": false,
      "isCurrentVersion": true,
      "questionEnabled": true,
      "textValue": null
    }
  ],
  "incompleteSummary": {
    "unansweredRequiredCount": 1,
    "unansweredRequiredQuestions": [
      { "questionId": "INSPECTION_QUESTION-0001", "question": "거실 및 방의 채광은 어떤가?", "sortOrder": 1 }
    ],
    "missingFields": ["interestLevel"]
  }
}
```

**문답은 매물을 만든 시점에 고정된다.** 이후 관리자가 질문을 추가하거나 문구를 고쳐도
이 매물의 문답 구성과 문구는 그대로다. 그래서 응답의 `question`·`answerType`·`choiceOptions`를
그대로 렌더링하면 되고, 질문 목록 API를 따로 부를 필요가 없다.

### 매물 상세 응답

`GET /inspection-properties/{propertyId}`와 `GET /inspection-visits/{visitId}/properties/{propertyId}`는
**같은 타입**을 돌려준다. 등록 응답에 아래가 더 붙는다.

```json
{
  "propertyId": "8a71c0e2-...",
  "inspectionVisitId": "INSPECTION_VISIT-0003",
  "areaId": "INSPECTION_AREA-0001",
  "areaName": "성수동",
  "visitedAt": "2026-09-17T14:00",
  "complexName": "트리마제",
  "name": "101동 1203호 / 84A",
  "memo": "...", "oneLineReview": "...", "pros": "...", "cons": "...",
  "interestLevel": 5, "status": "DRAFT", "sortOrder": 2,
  "tags": ["남향", "고층"],
  "cover": { "...": "FileBoxItemRdo" },
  "photos": [{ "...": "FileBoxItemRdo" }],
  "prevProperty": { "propertyId": "...", "complexName": "트리마제", "name": "102동 1501호 / 84A", "interestLevel": 4 },
  "nextProperty": null,
  "answers": [ "..." ],
  "incompleteSummary": { "...": "IncompleteSummaryRdo" }
}
```

- `areaId`/`areaName`/`visitedAt`은 breadcrumb용이다
- `prevProperty`/`nextProperty`는 **같은 임장 안에서** 매물 정렬 순서의 앞/뒤 1건이다. 없으면 `null`

### 회차 간 매물 연결 목록

```text
GET /api/inspection-areas/{areaId}/properties?complexName=&name=
→ [ { propertyId, visitId, visitedAt, complexName, name, interestLevel, status } ]
```

한 지역의 **모든 회차** 매물을 평면으로 준다. `visitedAt` 내림차순 → 같은 회차 안에서는
`sortOrder` 오름차순. 문답 스냅샷을 읽지 않는 경량 응답이다.

- `complexName`/`name`은 각각 독립적인 **정확 일치** 필터다. 회차 연결 스트립은 둘 다 넣어
  한 매물의 이력만 받는다
- 저장 시 서버가 `trim` + 연속 공백 1칸으로 정규화하므로, **같은 규칙으로 정규화한 값**을 보낸다
- 페이지 래퍼를 씌우지 않는다. 이 목록은 "이 매물이 회차를 거치며 어떻게 변했나"를 한 번에
  그리는 용도라 일부만 받으면 쓸모가 없다

`isCurrentVersion`과 `questionEnabled`는 **배지 전용**이다. "옛 문구 기준 답변",
"더 이상 쓰지 않는 질문" 같은 표시에만 쓰고, 렌더링 자체는 어떤 경우에도 응답에 담긴
스냅샷 값을 쓴다. 이 둘로 렌더링을 바꾸면 과거 기록이 그때 그대로라는 보장이 깨진다.

### 문답 저장

```json
PUT /api/inspection-visits/INSPECTION_VISIT-0001/properties/8a71c0e2-.../answers
{
  "answers": [
    { "questionId": "INSPECTION_QUESTION-0001", "textValue": "오후에도 상당히 밝았다." },
    { "questionId": "INSPECTION_QUESTION-0004", "selectedCodes": ["SOUTH"] }
  ]
}
```

- 요청에 없는 `questionId`는 그대로 둔다. 부분 저장이 가능하다.
- **값 필드에 `null`을 보내면 그 답변을 지운다.** `{ "questionId": "...", "textValue": null }`이면
  비운 것으로 처리되고 `answered`가 `false`가 된다. 선택형은 `"selectedCodes": []`가 같은 뜻이다.
  단 `COMPLETED` 매물에서 필수 문답을 비우면 `400`이다(§5 자동 동작 ②).
- 타입에 맞는 값 필드만 보낸다. 다른 필드가 섞이면 `400`이다.
  - `TEXT`/`LONG_TEXT` → `textValue`, `BOOLEAN` → `booleanValue`, `NUMBER` → `numberValue`,
    `RATING` → `ratingValue`(1~5), `SINGLE_SELECT`/`MULTI_SELECT` → `selectedCodes`
- `selectedCodes`의 값은 그 문답의 `choiceOptions[].code`에 있어야 한다.

---

## 5. 상태 전이

```text
DRAFT ──(조건 충족)──> COMPLETED ──(언제든)──> DRAFT
```

**매물 완료 조건**: `complexName`, `name`, `interestLevel`(1~5), 그리고 필수 문답이 전부 답변됨.
**임장 완료 조건**: `visitedAt`, `revisitIntent`, 그리고 등록된 매물이 **전부** `COMPLETED`.
매물이 0건인 임장도 완료할 수 있다.

실패하면 `400`과 함께 메시지에 원인이 담긴다.

```json
{ "success": false, "message": "필수 조건을 만족하지 않은 매물이 있습니다. propertyIds=[8a71c0e2-..., 9b02f1a3-...]" }
```

### FE가 알아야 할 자동 동작 두 가지

**① `COMPLETED` 임장에 매물을 추가하거나 매물을 `DRAFT`로 되돌리면 임장도 `DRAFT`로 내려간다.**
에러가 아니라 정상 동작이다. 응답에 바뀐 임장 상태가 담기므로 배지를 갱신한다.

**② 완료된 기록의 필수 항목을 비우는 저장은 `400`으로 거절된다.**
- 매물: 기본 정보 수정과 문답 저장 양쪽 모두. 필수 문답을 비울 수 없다.
- 임장: `PUT /inspection-visits/{id}`에서 `revisitIntent`를 빠뜨리면 거절된다. PUT은 전체
  교체라 **생략 = null**이고, 그러면 완료 조건이 깨지기 때문이다.

고쳐야 하면 먼저 `status`를 `DRAFT`로 되돌린 뒤 수정한다.

### 미완료 요약

`incompleteSummary`는 완료 검증과 **같은 기준**으로 만들어진다. 여기가 전부 비어 있으면
완료 버튼이 반드시 먹는다.

| 필드 | 레벨 | 의미 |
| --- | --- | --- |
| `unansweredRequiredCount` | 매물 | 답 안 한 필수 문답 수 |
| `unansweredRequiredQuestions` | 매물 | 그 문항들의 `{ questionId, question, sortOrder }` |
| `missingFields` | 매물 | `complexName`/`name`/`interestLevel` 중 빈 것 |
| `draftPropertyCount` | 임장 | 이 임장의 `DRAFT` 매물 수 |
| `visitMissingFields` | 임장 | `visitedAt`/`revisitIntent` 중 빈 것 |
| `draftVisitCount` | 지역 | 이 지역의 `DRAFT` 임장 수 |

**목록 응답에는 개수 필드만 담긴다.** 문항 이름 목록은 임장 상세와 매물 상세에만 온다.

---

## 6. 정렬

순서만 바꿀 때는 전용 엔드포인트를 쓴다. `files` 배열 전체를 되돌려보내면 사진 30장짜리
임장에서 순서 하나 바꾸는 데 배열 전체가 오간다.

```json
PUT /api/inspection-visits/{visitId}/properties/order
[ { "propertyId": "8a71...", "sortOrder": 1 }, { "propertyId": "9b02...", "sortOrder": 2 } ]

PUT /api/inspection-visits/{visitId}/images/order
[ { "itemId": "item-1", "sortOrder": 1 }, { "itemId": "item-2", "sortOrder": 2 } ]
```

- 요청에 빠진 항목은 기존 순서를 유지한다. 부분 갱신이 가능하다.
- 사진 정렬은 **임장 사진과 매물 사진을 함께** 처리한다. `targetType`을 보내지 않는다.
- `sortOrder` 중복은 허용한다. 서버가 2차 정렬 키로 순서를 결정적으로 유지한다.
- 정렬 변경은 상태를 건드리지 않는다. `COMPLETED` 임장에서도 된다.

---

## 7. 사진

업로드는 기존 파일 API를 그대로 쓴다. 자세한 규칙은 `docs/file-asset.md`,
축소본 선택은 `docs/image-asset-api.md`를 본다.

```text
1) POST /api/assets/files   (type=inspection)  → fileAssetId 목록
2) 임장/매물 저장 요청의 files 배열에 fileAssetId를 담는다
```

**파일 타입은 반드시 `inspection`이다.** 다른 타입을 연결하면 `400`이다.

### 반드시 지켜야 할 세 가지

**① 한 번에 6장까지만 올린다.** multipart 제한이 파일당 10 MB, 요청 전체 **60 MB**다.
임장 사진은 한 회차에 30장 규모가 되기 쉬우므로 **6장 단위로 나눠 업로드**하고 받은
`fileAssetId`를 모아 한 번에 저장한다. 한꺼번에 올리면 `413`이다.

**② 목록·상세에서는 `variant`를 반드시 지정한다.** 파라미터 없이 부르면 원본이 `302`
리다이렉트로 내려가는데, 서명 URL이라 `no-store`가 붙어 **캐시가 전혀 먹지 않는다.**
사진 30장짜리 상세를 원본으로 그리면 재방문마다 30번을 새로 받는다.

| 위치 | 권장 |
| --- | --- |
| 목록의 `cover`, 지역 `thumbnails` | `home-thumb` (320px) |
| 상세의 `photos` | `home-feature` (960px) |
| 확대·다운로드 | 원본 |

**③ 업로드 진행률을 노출한다.** 파생본 생성이 업로드 요청 안에서 동기로 돈다.
6장짜리 요청 하나가 인코딩 12회 + 스토리지 업로드 18회를 수행하므로 체감이 길다.

### 수정 시 동작

- `files`를 **생략하면** 기존 연결을 유지한다.
- `files`를 **보내면** 그 그룹이 통째로 치환된다. 임장 저장은 임장 사진만, 매물 저장은
  그 매물 사진만 바뀐다. 다른 그룹은 건드리지 않는다.
- 기존 항목을 유지하려면 그 항목의 `id`를 함께 보낸다. `id`를 보낸 항목은 **기존 `sortOrder`가
  유지되고**, `id` 없는 신규 항목만 그룹 말미에 채번된다.
- `role`은 `COVER`(대상별 0 또는 1건)와 `GALLERY`만 쓴다. `COVER`는 필수가 아니다 —
  사진 없는 임장 기록이 가능하다.

---

## 8. 태그

```text
GET /api/inspection-tags?keyword=한&scope=VISIT|PROPERTY
```

사용 빈도 내림차순, 동률이면 이름순으로 내려간다. 자동완성에 쓴다.

`scope`는 **빈도 집계에만** 영향을 준다. `VISIT`이면 임장 연결만, `PROPERTY`면 매물 연결만
세므로 매물 입력창에 임장 전용 태그가 1순위로 뜨지 않는다. 생략하면 둘을 합산한다(기존 동작).
그 scope에서 한 번도 안 쓰인 태그도 이름이 걸리면 결과에 들어오고 `usageCount`만 `0`이다.
**enum이라 대문자로 정확히 보내야 한다.**

임장 태그와 매물 태그는 **같은 풀을 공유**한다. 태그 생성/삭제 API는 없다 —
임장·매물 저장 시 `tags: ["한강", "직주근접"]`처럼 **이름 배열**을 보내면 서버가
정규화(`#` 제거, 앞뒤 공백 제거)한 뒤 없으면 만든다. 태그 ID를 FE가 관리하지 않는다.

- 요청의 태그 목록이 최종 상태다. 빠진 태그는 연결이 끊긴다.
- `tags`를 **생략하면** 기존 연결을 유지한다. 전부 떼려면 `[]`를 보낸다.
- 임장·매물 각각 최대 10개다.
- **연결 순서는 보존되지 않는다.** 응답은 이름 오름차순이라 "먼저 입력한 태그"를 알 수 없다.

---

## 9. 질문 관리 (관리자)

| 메서드 | 경로 | 권한 | 설명 |
| --- | --- | --- | --- |
| GET | `/inspection-questions` | `USER` | 기본은 활성 질문만. `?includeDisabled=true`, `?withAnswerCount=true` |
| GET | `/inspection-questions/{questionId}/versions` | `USER` | 버전 이력. 버전별 `answerCount` 포함 |
| POST | `/inspection-questions` | **`ADMIN`** | 등록 |
| PUT | `/inspection-questions/{questionId}` | **`ADMIN`** | 문구·설명·선택지·단위 수정 → **새 버전** |
| PATCH | `/inspection-questions/{questionId}/policy` | **`ADMIN`** | `required`, `sortOrder` |
| PATCH | `/inspection-questions/{questionId}/status` | **`ADMIN`** | `enabled` 토글 |
| PUT | `/inspection-questions/order` | **`ADMIN`** | 순서 일괄 변경 |

### 요청 스키마

```json
POST /api/inspection-questions
{ "content": "거실 채광은 어떤가?", "description": "오후 기준", "answerType": "LONG_TEXT",
  "required": true, "sortOrder": 1, "choices": [], "unit": null }

PUT /api/inspection-questions/{questionId}            // 문구·설명·선택지·단위. 새 버전이 생긴다
{ "content": "...", "description": "...", "choices": [{ "code": "SOUTH", "label": "남향", "sortOrder": 1 }], "unit": null }

PATCH /api/inspection-questions/{questionId}/policy   { "required": true, "sortOrder": 3 }
PATCH /api/inspection-questions/{questionId}/status   { "enabled": false }
PUT   /api/inspection-questions/order                 [ { "questionId": "...", "sortOrder": 1 } ]
```

`answerType`은 `PUT`에 **없다.** 등록할 때만 정할 수 있다.

### 응답 스키마

`POST`/`PUT`/`PATCH`와 `GET /inspection-questions`가 모두 같은 형태다.

```json
{
  "questionId": "INSPECTION_QUESTION-0001",
  "answerType": "LONG_TEXT",
  "required": true,
  "sortOrder": 1,
  "enabled": true,
  "currentVersionNo": 2,
  "content": "거실 및 방의 채광은 어떤가?",
  "description": "오후 시간대 기준으로 기록",
  "choices": [],
  "unit": null,
  "answerCount": null
}
```

`PUT /inspection-questions/order`만 `204 No Content`다.

```json
GET /api/inspection-questions/{questionId}/versions
→ [ { "questionId": "...", "versionNo": 1, "content": "거실 채광은 어떤가?",
      "description": null, "choices": [], "unit": null, "answerCount": 5, "current": false } ]
```

**버전에는 생성 시각이 없다.** 화면에 날짜를 그려야 하면 지금은 채울 값이 없다.

삭제 API는 없다. 잘못 만든 질문은 `enabled=false`로 내린다.

**타입(`answerType`)은 등록할 때만 정할 수 있고 이후 바꿀 수 없다.** 바꿔야 하면 기존
질문을 미사용 처리하고 새 질문을 만든다. 과거 답변은 그대로 조회된다.

입력 제약:

- 선택지(`choices`)는 `SINGLE_SELECT`/`MULTI_SELECT`에만 쓸 수 있고, 이 두 타입에는 최소 1개가 필요하다.
- 단위(`unit`)는 `NUMBER`에만 쓸 수 있다.
- 선택지 `code`는 질문 안에서 유일해야 한다.

`answerCount`는 **실제로 답이 채워진 건수**다. 매물을 만들면 활성 질문이 전부 빈 항목으로 깔리는데
그건 세지 않는다.

`?withAnswerCount=true`는 전건 스캔이라 느리다. **관리자 화면에서만 켜고 평소에는 끈다.**

두 관리자가 같은 질문을 **정확히 동시에** 고치면 나중 요청이 `409`로 거절된다.
"질문이 이미 수정되었습니다. 새로고침 후 다시 시도하세요" 메시지를 그대로 보여주면 된다.

> **`409`가 모든 충돌을 잡아 주지는 않는다.** 버전은 서버 내부에서만 관리하고 요청에서 받지
> 않으므로, 두 요청이 실제로 겹칠 때만 감지된다. A가 수정 모달을 열어 둔 사이 B가 저장하고
> 그다음 A가 저장하면 **B의 수정이 조용히 덮어써진다.** `409` 처리는 "드물게 나는 재시도 안내"
> 수준으로 붙이고, 충돌이 막힌다고 가정하지 않는다. 임장·매물도 같다
> (`INSPECTION_VISIT_CONFLICT`, `VIEWED_PROPERTY_CONFLICT`).

---

## 10. 에러 코드

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `INSPECTION_AREA_NOT_FOUND` | 400 | 지역 없음 |
| `INSPECTION_AREA_IN_USE` | 409 | 임장 기록이 있는 지역 삭제 |
| `INSPECTION_AREA_DUPLICATED` | 409 | 동일 지역명 존재. 메시지에 기존 `areaId` |
| `INSPECTION_VISIT_NOT_FOUND` | 400 | 임장 없음 |
| `INVALID_INSPECTION_VISIT` | 400 | 임장 입력/완료 조건 위반. 완료된 임장의 필수 항목을 비우는 수정 포함 |
| `VIEWED_PROPERTY_NOT_FOUND` | 400 | 매물 없음, 또는 다른 임장의 매물 |
| `INVALID_VIEWED_PROPERTY` | 400 | 매물 입력/완료 조건 위반 |
| `INSPECTION_QUESTION_NOT_FOUND` | 400 | 질문 없음, 또는 이 매물의 문답에 없는 `questionId` |
| `INVALID_INSPECTION_QUESTION` | 400 | 금지된 질문 수정 |
| `INSPECTION_QUESTION_CONFLICT` | 409 | 질문 동시 수정 |
| `INSPECTION_ANSWER_REQUIRED` | 400 | 필수 문답 미완료. 메시지에 `questionIds` |
| `INVALID_PROPERTY_ANSWER` | 400 | 문답 값이 타입과 안 맞음 |
| `INVALID_INSPECTION_FILE` | 400 | 사진 연결 정보 오류 |
| `INVALID_INSPECTION_ORDER` | 400 | 정렬 대상이 이 임장 소속이 아님 |
| `INSPECTION_VISIT_CONFLICT` | 409 | 임장 동시 저장 |
| `VIEWED_PROPERTY_CONFLICT` | 409 | 매물 동시 저장 |
| `PAYLOAD_TOO_LARGE` | 413 | 업로드 요청이 60 MB 초과 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료·위조 |
| `ACCESS_ROLE_DENIED` | 403 | 권한 부족. `ADMIN` 전용 API를 `USER`가 호출 |

401/403은 기존 도메인과 같은 처리다. 응답 형태도 같다.

```json
{ "success": false, "message": "..." }
```

**`ADMIN` 전용은 질문 관리의 쓰기(`POST`/`PUT`/`PATCH`)뿐이다.** `GET /inspection-questions`와
버전 이력은 `USER`도 부를 수 있다 — 매물을 만들 때 활성 질문을 읽어야 하기 때문이다.
그래서 질문 관리 화면 자체는 `USER`에게도 열리고 **저장할 때만 403**이 난다.
화면 진입을 막으려면 FE가 `POST /users/token` 응답의 `roleList`로 라우트를 가드해야 한다.

입력 길이 상한(초과 시 `400`): 지역명 100자, 지역 설명 300자, 단지명·매물명 각 200자,
한줄평 300자, 메모·장단점 5,000자, 태그 이름 50자, 질문 문구 300자, 질문 설명 500자, 단위 20자.
