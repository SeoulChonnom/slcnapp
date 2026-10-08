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
| 주소 검색(매물 위치 · 현재 위치 입력) | `GET /geo/addresses?keyword=` (§8-2) |
| 지도 도보 경로 | `POST /inspection-visits/{visitId}/walking-route` (§8-3) |
| 추천 순서 정렬의 현재 위치 | `POST /geo/current-location` (§8-4) |
| AI 후기 제안(임장/매물 폼) | `POST /inspection-visits/{visitId}/review-suggestion`, `POST /inspection-visits/{visitId}/properties/{propertyId}/review-suggestion` |
| 질문 대분류 관리(관리자) | `GET/POST/PUT/PATCH /inspection-question-categories` |
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
        "completedAt": "2026-09-17T18:30",
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
      "plannedVisit": { "inspectionVisitId": "INSPECTION_VISIT-0007", "visitedAt": "2026-10-12T14:00" },
      "incompleteSummary": { "draftVisitCount": 1, "plannedVisitCount": 1, "draftPropertyCount": 2, "unansweredRequiredCount": 5 },
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
- **계획 임장.** `completedAt`이 없는 임장(한 번도 완료하지 않은 임장)은 "계획"이다.
  방문 횟수(`visitCount`), `firstVisitedAt`/`lastVisitedAt`, `latestVisit`, `topProperty`, 매물 수,
  `sort`, `revisitIntent` 필터, `revisitIntentCounts`, `totals`는 **완료된 적 있는 임장만** 센다.
  `keyword` 검색만 계획 임장의 매물과 태그도 훑는다. 계획만 있는 지역은 `visitCount: 0`,
  `latestVisit: null`이지만 목록에는 나온다
- `plannedVisit`은 계획 임장 중 `visitedAt`이 **가장 이른 1건**이다(동점이면 `visitId` 오름차순). 없으면 `null`.
  지난 날짜일 수 있으므로 FE가 `visitedAt`을 오늘과 비교해 오늘 이후면 "예정", 지났으면 "미완료"로 표시한다.
  여러 건이 있다는 사실은 `incompleteSummary.plannedVisitCount`로 안다
- `completedAt`은 **최초 완료 시각**이다. `visitedAt`과 같은 형식의 문자열이고 한 번도 완료하지 않았으면 `null`이다.
  완료한 뒤 `DRAFT`로 되돌려도, 다시 완료해도 바뀌지 않는다. 임장 목록·상세·지역 상세의 `visits[]`·`latestVisit`에 모두 실린다
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
      "completedAt": "2026-09-17T18:30",
      "oneLineReview": "...",
      "revisitIntent": "YES",
      "status": "DRAFT",
      "tags": ["서울숲"],
      "propertyCount": 3,
      "incompleteSummary": { "draftPropertyCount": 2, "unansweredRequiredCount": 5, "visitMissingFields": [] }
    }
  ],
  "hasMoreVisits": false,
  "visitPageSize": 50,
  "selectedVisit": { "...": "GET /inspection-visits/{visitId}와 같은 타입" }
}
```

- `visits`는 `visitedAt` 내림차순 → `visitId` 오름차순이고, **`visitPageSize`건을 넘으면 잘린다.**
  이때 `hasMoreVisits: true`가 온다
- **이어받을 때는 `visitPageSize`를 그대로 `size`로 넘긴다.** 상수를 박지 않는다

  ```text
  GET /api/inspection-visits?areaId={areaId}&page=1&size={visitPageSize}
  ```

  임장 목록의 `size` 기본값(20)과 `visitPageSize`(현재 50)가 다르므로 `size`를 생략하면
  자른 지점과 어긋난다. 두 조회의 정렬 키가 **같아서**(`visitedAt` 내림차순 → `visitId` 오름차순)
  `visitedAt`이 동률이어도 경계에서 중복이나 누락은 생기지 않는다
- `visitId`를 안 주면 **가장 최근에 완료한 회차**(`completedAt`이 있는 것 중 `visitedAt` 내림차순 → `id` 오름차순 첫 건)가
  `selectedVisit`에 펼쳐진다. 완료한 회차가 하나도 없으면 `plannedVisit`(가장 이른 계획)이 펼쳐진다.
- `visits[]`는 `visitedAt` 내림차순이라 미래의 계획 임장이 첫 행인데 다른 회차가 펼쳐질 수 있다.
  FE는 행 위치가 아니라 **`selectedVisit.inspectionVisitId`로 선택 행을 강조**해야 한다.
- `selectedVisit`은 임장 상세와 **같은 타입**이다. 지연 로딩으로 전환해도 FE가 다루는 모양이 하나다.

---

## 3. 임장 기록

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-visits` | 목록. `visitedAt` 내림차순 → `visitId` 오름차순 |
| GET | `/inspection-visits/{visitId}` | 상세. 매물·문답·사진 포함 |
| POST | `/inspection-visits` | 등록. 항상 `DRAFT`로 생성 |
| PUT | `/inspection-visits/{visitId}` | 기본 정보·태그·사진 수정 |
| PATCH | `/inspection-visits/{visitId}/status` | `COMPLETED` / `DRAFT` 전이 |
| PUT | `/inspection-visits/{visitId}/properties/order` | 매물 순서 일괄 변경 |
| PUT | `/inspection-visits/{visitId}/images/order` | 사진 순서 일괄 변경 |
| POST | `/inspection-visits/{visitId}/walking-route` | 저장된 매물 위치로 도보 경로 조회(§8-3). 본문 없음, 저장 안 함 |
| DELETE | `/inspection-visits/{visitId}` | 하위 매물·문답·태그·사진 함께 삭제 |

목록 필터(전부 선택): `areaId`, `status`, `revisitIntent`, `tag`(복수 지정 가능, **AND**),
`from`, `to`. 기간은 `2026-09-01T00:00:00` 형태의 ISO local date-time이며 양끝을 포함한다.
여기에 `page`/`size`가 붙고 응답은 `{ items, totalCount, hasNext }` 래퍼다.
`tag`는 이름으로 보내며 **대소문자까지 정확히 일치**해야 한다(자동완성으로 고른 값을 그대로 보낸다).

> **정렬은 `visitedAt` 내림차순 → `visitId` 오름차순이다.** 2차 키가 있어야 같은 지역을 하루에
> 두 번 임장해 `visitedAt`이 동률이어도 페이지 경계에서 중복이나 누락이 생기지 않는다.
> 지역 상세의 `visits`도 **같은 키**를 쓰므로 회차 이어받기(§2)가 안전하다.

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

`complexName`은 필수다. 단지명은 자유 입력이므로 표기가 흔들리면 단지별 그룹핑이 깨진다.
매물 등록 화면에서 `complex-names`를 자동완성 후보로 쓴다.

**`name`(동·호수)은 계획 단계에서는 생략할 수 있다.** 비워서 등록·수정하면 `null`로 저장되고 응답도 `null`이다
(FE는 "미정" 등으로 표시한다). 다만 **매물을 `COMPLETED`로 만들 때는 필수**라, 비어 있으면 완료가 `400`으로 거절되고
`incompleteSummary.missingFields`에 `name`이 담긴다.

**`location`(위치)은 선택이다.** 등록·수정 요청에 `location`을 실을 수 있고, 규칙과 예시는 §8-2에 있다.
PUT은 전체 교체라 **생략하면 위치가 지워진다.** 수정 화면은 상세 응답의 `location`을 그대로 돌려보낸다.

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
      "textValue": null,
      "categoryId": "INSPECTION_QUESTION_CATEGORY-0001",
      "categoryName": "채광·환기",
      "categorySortOrder": 1
    }
  ],
  "incompleteSummary": {
    "unansweredRequiredCount": 1,
    "unansweredRequiredQuestions": [
      { "questionId": "INSPECTION_QUESTION-0001", "question": "거실 및 방의 채광은 어떤가?", "sortOrder": 1,
        "categoryName": "채광·환기" }
    ],
    "missingFields": ["interestLevel"]
  }
}
```

**문답은 매물을 만든 시점에 고정된다.** 이후 관리자가 질문을 추가하거나 문구를 고쳐도
이 매물의 문답 구성과 문구는 그대로다. 그래서 응답의 `question`·`answerType`·`choiceOptions`를
그대로 렌더링하면 되고, 질문 목록 API를 따로 부를 필요가 없다.

**분류도 같은 원칙으로 스냅샷된다.** `answers[].categoryId`/`categoryName`/`categorySortOrder`는
매물을 만든 시점의 질문 분류 값을 그대로 복사한 것이다. 이후 관리자가 분류 이름을 바꾸거나
질문을 다른 분류로 옮겨도 **이미 만들어진 매물의 표시는 바뀌지 않는다.** `unansweredRequiredQuestions[].categoryName`도
같은 스냅샷에서 온다.

세 필드는 **항상 채워져 있다(`null` 아님).** 모든 질문이 분류에 속하고, 배포 전에 만들어진 매물도
DB 이관 때 분류를 채웠다(§12).

**정렬**: `answers`는 **분류.`categorySortOrder` → 답변.`sortOrder` → `questionId`**
순으로 정렬돼 내려온다. 저장된 배열 순서가 아니라 **읽을 때마다 다시 정렬한 값**이다. 응답은
**평면 배열**이고, FE가 `categoryId`로 묶어 분류별 섹션을 그린다.

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
  "location": { "bdMgtSn": "1120011500106850001000001", "roadAddress": "서울특별시 성동구 용답토방길 135", "latitude": 37.5431, "longitude": 127.0553 },
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

- `name`은 계획 단계에서 `null`일 수 있다. `location`은 위치가 없으면 `null`이다(§8-2). 임장 상세의 `properties[]`에도 같은 형태로 실린다
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

실패하면 `400`과 함께 `title`에 원인이 담긴다. 응답 형태는 §11을 따른다.

```json
{ "title": "필수 조건을 만족하지 않은 매물이 있습니다. propertyIds=[8a71c0e2-..., 9b02f1a3-...]",
  "status": 400, "code": "INVALID_INSPECTION_VISIT", "errors": [] }
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
| `unansweredRequiredQuestions` | 매물 | 그 문항들의 `{ questionId, question, sortOrder, categoryName }` |
| `missingFields` | 매물 | `complexName`/`name`/`interestLevel` 중 빈 것 |
| `draftPropertyCount` | 임장 | 이 임장의 `DRAFT` 매물 수 |
| `visitMissingFields` | 임장 | `visitedAt`/`revisitIntent` 중 빈 것 |
| `draftVisitCount` | 지역 | 이 지역에서 **수정 중**(한 번 완료했다가 `DRAFT`로 돌아온)인 임장 수. 계획 임장은 세지 않는다 |
| `plannedVisitCount` | 지역 | 이 지역의 계획 임장(`completedAt` 없음) 수. 지역 요약에서만 채우고 다른 요약에서는 `0` |

지역의 `draftPropertyCount`와 `unansweredRequiredCount`도 계획 임장의 매물은 제외한다.
계획을 "덜 쓴 기록"으로 보이게 하지 않으려는 것이다.

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

## 8-1. AI 후기 제안

임장·매물 폼에서 메모를 쓴 뒤 한줄평·장점·단점·태그를 AI가 제안하게 한다.

```text
POST /api/inspection-visits/{visitId}/review-suggestion
POST /api/inspection-visits/{visitId}/properties/{propertyId}/review-suggestion
```

```json
// 요청: 폼에 입력 중인 값(저장 전이어도 된다). memo 필수, pros 선택(이미 쓴 장점을 참고로 넘김)
{ "memo": "한강이 보이고 조용하다. 주차 자리가 부족해 보였다.", "pros": "한강뷰" }

// 200 응답
{
  "oneLineReview": "한강이 보이는 조용한 동네지만 주차가 아쉽다.",
  "pros": "- 한강이 보임\n- 조용함",
  "cons": "- 주차 자리가 부족해 보임",
  "tags": ["한강뷰", "조용함"]
}
```

- **제안만 한다. 서버는 아무것도 저장하지 않는다.** FE가 응답으로 폼을 채우고, 사용자가 고친 뒤
  기존 `PUT`으로 저장한다. 응답을 그대로 저장해도 길이 상한(한줄평 300자, 장점·단점 각 5,000자, 태그
  10개·50자)을 넘지 않게 서버가 다듬어 내린다.
- 요청은 **저장된 값이 아니라 본문의 `memo`/`pros`** 를 쓴다. **`memo`는 필수**(공백만이어도 안 된다)이고
  `pros`는 선택이다. `pros`는 사용자가 이미 쓴 장점을 AI에게 참고로 넘기는 값으로, 응답의 `pros`는
  그 내용을 유지하면서(표현은 다듬을 수 있다) 메모·답변에 근거가 있는 다른 장점을 덧붙인 결과다.
  메모가 비어 있으면 `pros`가 있어도 `400`이다.
- **`400` `VALIDATION_FAILED`**: 본문 검증 실패. `title`은 첫 번째 위반 메시지이고 `errors[]`에 필드별로 담긴다.
  FE는 `errors[].field`/`code`로 분기한다(임장·매물 제안 모두 같은 형태).

  ```json
  { "code": "VALIDATION_FAILED", "title": "메모를 입력해야 제안할 수 있습니다.",
    "errors": [ { "field": "memo", "code": "NOT_BLANK", "message": "메모를 입력해야 제안할 수 있습니다." } ] }
  ```

  | field | code | 조건 |
  | --- | --- | --- |
  | `memo` | `NOT_BLANK` | 없음·빈 문자열·공백 |
  | `memo` | `SIZE` | 5,000자 초과 |
  | `pros` | `SIZE` | 5,000자 초과 |

  본문 자체가 없거나 JSON이 아니면 `400` `INVALID_REQUEST_BODY`다.
- 매물 제안은 추가로 그 매물에 **저장된** 문답 중 답한 항목을 근거로 쓴다. 문답을 방금 고쳤다면
  먼저 `PUT .../answers`로 저장한 뒤 호출한다. 다른 임장의 매물이면 `VIEWED_PROPERTY_NOT_FOUND`다.
- `tags`는 기존 태그 풀(사용 빈도 상위 30개)에서 우선 고르고, 맞는 것이 없을 때만 새 이름을 만든다.
  응답 태그가 아직 없는 이름이어도 저장 시 §8 규칙대로 만들어진다.
- 근거가 없는 필드는 `null`이 아니라 `""`(문자열) 또는 `[]`(태그)다. 장점·단점이 `""`이면 폼의 해당
  칸을 건드리지 않는 것이 자연스럽다.
- **`503` `REVIEW_SUGGESTION_UNAVAILABLE`**: API 키가 설정되지 않았거나 AI 호출이 실패·지연
  (시도당 기본 15초, 폴백 포함 최악 약 30초)된 경우다. 폼 입력은 그대로 두고 "지금은 제안을 받을 수 없습니다" 정도로 안내한 뒤
  사용자가 직접 쓰게 한다. `code`는 항상 같고 `title`만 원인에 따라 둘로 갈린다 — 일시적 실패
  (호출 한도·서버 오류·네트워크·지연·응답 해석 불가)는 "후기 제안을 지금은 사용할 수 없습니다. 잠시 후
  다시 시도하세요.", 설정 문제(키 미설정, 잘못된 키·모델)는 "AI 후기 제안 설정에 문제가 있어 사용할 수
  없습니다. 관리자에게 문의하세요."이다. FE는 분기하지 말고 `title`을 그대로 보여 주면 된다. 재시도는 사용자 동작으로만 한다. 호출 한 번이 수 초 걸릴 수 있으니
  버튼에 로딩 상태를 둔다.

서버 설정(배포 담당자용): `SLCN_GEMINI_API_KEY`(비어 있으면 기능 꺼짐, 항상 503),
`SLCN_GEMINI_MODEL`(기본 `gemini-3.5-flash-lite`),
`SLCN_GEMINI_FALLBACK_MODEL`(기본 `gemini-3.1-flash-lite`. 주 모델이 429/5xx/타임아웃/응답 해석 불가로 실패할 때만 한 번 더 시도, 비우면 폴백 없음),
`SLCN_GEMINI_TIMEOUT_SECONDS`(기본 `15`, 시도당 제한 시간이라 폴백까지 쓰면 최악 약 2배),
`SLCN_GEMINI_THINKING_LEVEL`(기본 `LOW`. `MINIMAL`/`LOW`/`MEDIUM`/`HIGH`, 비우면 보내지 않음. 잘못된 값은 기동 실패).

---

## 8-2. 주소 검색과 매물 위치

지도에 매물을 찍으려면 매물에 위치(`location`)를 붙인다. 좌표는 **FE가 보내지 않는다.** FE는 주소 검색 결과를
고르기만 하고, 서버가 행안부 좌표제공 API로 좌표를 구해 WGS84(위도·경도)로 저장한다.
저장되는 좌표의 출처가 항상 행안부라는 뜻이다. 단말 GPS는 쓰지 않는다.

### 주소 검색

```text
GET /api/geo/addresses?keyword=성수 트리마제&page=1&size=10
```

- `keyword`는 필수(공백 제외 1자 이상). `page`는 1부터(기본 1), `size`는 1~20(기본 10). 어기면 `400` `VALIDATION_FAILED`
- 행안부 도로명주소 검색을 그대로 프록시한다. 아무것도 저장하지 않는다

```json
{
  "totalCount": 12,
  "items": [
    {
      "roadAddress": "서울특별시 성동구 용답토방길 135",
      "jibunAddress": "서울특별시 성동구 성수동1가 685-1",
      "buildingName": "트리마제",
      "bdMgtSn": "1120011500106850001000001",
      "zipNo": "04773",
      "coordKey": { "admCd": "1120011500", "rnMgtSn": "112003115001", "udrtYn": "0", "buldMnnm": "135", "buldSlno": "0" }
    }
  ]
}
```

- `bdMgtSn`은 행안부 건물관리번호다. **같은 건물인지는 이 값으로 판단한다.**
- `coordKey`는 좌표를 구하는 데 필요한 식별값이다. 후보를 골랐을 때 그대로 `location`에 실어 보낸다.
- 검색어가 행안부에 거절되면(두 글자 미만, 특수문자·SQL 예약어 포함 등) `400` `INVALID_ADDRESS_KEYWORD`이고 `title`에 사유가 담긴다
- 역 이름 같은 장소명으로 검색이 되는지는 보장하지 않는다. "가까운 건물이나 주소를 입력하세요"라고 안내한다

### 매물에 위치 저장

`POST /inspection-visits/{visitId}/properties`와 `PUT .../properties/{propertyId}`의 본문에 `location`을 실을 수 있다.

```json
"location": {
  "bdMgtSn": "1120011500106850001000001",
  "roadAddress": "서울특별시 성동구 용답토방길 135",
  "coordKey": { "admCd": "1120011500", "rnMgtSn": "112003115001", "udrtYn": "0", "buldMnnm": "135", "buldSlno": "0" }
}
```

서버 판정 규칙. **요청에 좌표를 받지 않는다.**

| 요청 `location` | 서버 동작 | 응답 |
| --- | --- | --- |
| `null` 또는 생략 | 위치를 지운다(등록이면 위치 없이 등록) | `location: null` |
| `bdMgtSn`이 저장된 값과 같다 | **저장된 좌표를 유지**한다. 행안부를 다시 부르지 않는다. `coordKey`는 필요 없다 | 기존 `location` |
| `bdMgtSn`이 다르거나 저장된 값이 없다 | `coordKey`로 행안부 좌표를 조회해 WGS84로 변환해 저장한다 | 새 `location` |
| 위 경우에 `bdMgtSn`이 비었거나, 새로 조회해야 하는데 `coordKey`가 없거나 필드가 비었다 | 아무것도 저장하지 않는다 | `400` `VALIDATION_FAILED` |
| `bdMgtSn`이 26자를 넘거나 `roadAddress`가 300자를 넘는다(trim 후 기준) | 행안부를 부르기 전에 거절하고 아무것도 저장하지 않는다 | `400` `VALIDATION_FAILED` |
| 주소는 검색되지만 행안부가 좌표 정보를 주지 않는다 | 아무것도 저장하지 않는다. FE는 "위치 없이 저장" 안내를 보여 준다 | `400` `ADDRESS_COORDINATE_NOT_FOUND` |
| 행안부 설정이 없다(키 미설정·승인 안 됨) | 아무것도 저장하지 않는다 | `503` `ADDRESS_LOOKUP_UNAVAILABLE` |
| 행안부 호출 실패(타임아웃·5xx·응답 해석 불가) | **매물 저장 전체를 실패**시킨다. 고른 위치가 조용히 사라지지 않게 한다 | `502` `ADDRESS_LOOKUP_FAILED` |

- 위치는 **PUT 전체 교체**의 일부다. 수정 화면은 상세 응답의 `location`을 그대로 돌려보내야 위치가 유지된다.
  (`bdMgtSn`이 같으면 외부 호출이 없으니 메모만 고치는 수정의 비용은 늘지 않는다.)
- 상태 전이(`PATCH .../status`), 문답 저장(`PUT .../answers`), 순서 변경은 위치를 건드리지 않는다.
- 응답의 `location`은 `{ bdMgtSn, roadAddress, latitude, longitude }`이다. 행안부 원본 좌표(UTM-K)는 내려가지 않는다.
  `latitude`/`longitude`는 도 단위 WGS84다.

---

## 8-3. 도보 경로

```text
POST /api/inspection-visits/{visitId}/walking-route        // 본문 없음
```

저장된 매물 위치를 **임장 상세와 같은 순서**로 이어 카카오 도보 경로를 조회한다. 서버는 응답을 저장하거나 캐시하지 않는다.
외부 유료 호출이라 브라우저나 프록시가 미리 불러오지 않도록 `GET`이 아니라 `POST`다.
"도보 경로 보기"를 눌렀을 때 지연 조회한다.

처리 규칙:
- 순서는 임장 상세의 `properties[]`와 같다(`sortOrder`, 서버 2차 키). FE가 따로 정렬하지 않는다
- 위치가 없는 매물은 건너뛴다
- **바로 이어지는** 같은 좌표(같은 단지의 여러 매물)는 한 지점(`stop`)으로 합치고 `propertyIds`에 모두 담는다.
  떨어져서 다시 나오는 좌표(A → B → A)는 합치지 않고 돌아오는 경로로 이어 준다
- 지점이 2개 미만이면 외부 호출 없이 **빈 경로**(`0`, `0`, 빈 배열)를 돌려준다
- 지점이 7개를 넘으면 앞 구간의 끝 지점을 다음 구간의 시작 지점으로 겹쳐 나눠 순서대로 호출하고 하나로 합쳐 돌려준다.
  호출 수는 `ceil((지점 수 - 1) / 6)`이다. 하나라도 실패하면 **부분 결과 없이 전체가 실패**한다
- 외부로는 좌표만 나간다. 임장 ID, 사용자 정보, 주소는 보내지 않는다
- **현재 위치 → 첫 매물 구간은 이 API로 그릴 수 없다.** 저장된 좌표만 쓰기 때문이다. 그 구간은 FE가 직선과 추정 시간으로 표시한다

```json
{
  "totalDistance": 1280,
  "totalTime": 960,
  "stops": [
    { "index": 0, "propertyIds": ["8a71c0e2-...", "5b02d9f1-..."], "latitude": 37.5431, "longitude": 127.0553 },
    { "index": 1, "propertyIds": ["c4417a10-..."], "latitude": 37.5472, "longitude": 127.0415 }
  ],
  "legs": [
    {
      "fromStopIndex": 0,
      "toStopIndex": 1,
      "distance": 1280,
      "time": 960,
      "path": [[127.0553, 37.5431], [127.0551, 37.5435], [127.0415, 37.5472]]
    }
  ]
}
```

| 필드 | 의미 |
| --- | --- |
| `totalDistance` | 전체 거리(m). 구간 합 |
| `totalTime` | 전체 도보 시간(초). 구간 합 |
| `stops[]` | 경로가 지나는 지점. `index`는 0부터 |
| `legs[]` | 보통 인접한 두 지점 사이의 구간(`stops.length - 1`개). 제공자가 경로를 나눠 주지 않으면 한 구간이 여러 지점에 걸친다(`toStopIndex - fromStopIndex > 1`). FE는 `path`를 그대로 그린다 |
| `legs[].path` | 비어 있을 수 있다. 비어 있으면 그 구간은 FE가 두 지점을 잇는 직선으로 그린다. **`[경도, 위도]` 순서**(GeoJSON과 같다)의 점 목록. 위도·경도 순이 아니다. 카카오 지도 SDK에 넘길 때 `new LatLng(위도, 경도)`로 뒤집는다 |

오류. FE는 어느 경우든 **직선 표시를 유지**하고 알림만 띄운다.

| HTTP | `code` | 언제 |
| --- | --- | --- |
| `400` | `INSPECTION_VISIT_NOT_FOUND` | 임장 없음(이 서비스의 기존 규칙상 404가 아니라 400이다) |
| `429` | `WALKING_ROUTE_QUOTA_EXCEEDED` | 카카오 일일 한도 초과. 과금되지 않고 차단될 뿐이며, 다음 날 다시 된다 |
| `502` | `WALKING_ROUTE_FAILED` | 타임아웃, 카카오 5xx, 응답 해석 불가, 카카오가 경로를 못 찾음(같은 지점·너무 먼 거리 등) |
| `503` | `WALKING_ROUTE_UNAVAILABLE` | 카카오 REST 키 미설정 또는 거절됨. 관리자가 고쳐야 한다 |

---

## 8-4. 현재 위치 좌표 변환

"추천 순서로 정렬"에서 현재 위치를 **주소로 직접 입력**받기 위한 API다. GPS는 쓰지 않는다.
사용자는 §8-2의 주소 검색 UI로 후보를 고르고, 그 `coordKey`를 보낸다.

```json
POST /api/geo/current-location
{ "coordKey": { "admCd": "1120011500", "rnMgtSn": "112003115001", "udrtYn": "0", "buldMnnm": "135", "buldSlno": "0" } }

// 200
{ "latitude": 37.5431, "longitude": 127.0553 }
```

- **서버는 현재 위치를 저장하지 않고 로그에도 남기지 않는다.** 좌표만 구해 돌려주고 버린다. FE도 이 화면을 쓰는 동안 메모리에만 둔다
- `coordKey`가 없거나 다섯 필드 중 하나라도 비어 있으면 `400` `VALIDATION_FAILED`. 본문이 없거나 JSON이 아니면 `400` `INVALID_REQUEST_BODY`
- 행안부 설정이 없으면 `503` `ADDRESS_LOOKUP_UNAVAILABLE`, 조회 실패는 `502` `ADDRESS_LOOKUP_FAILED`
- 주소는 검색되지만 좌표 정보가 없으면 `400` `ADDRESS_COORDINATE_NOT_FOUND`(§8-2)

---

## 9. 질문 대분류 관리 (관리자)

임장 질문을 묶는 상위 분류다. 아직 정식 명칭이 없어 코드와 문서에서는 "대분류"라 부른다.
1단계에는 중분류가 없다 — 분류 밑에 바로 질문이 붙는다.

| 메서드 | 경로 | 권한 | 설명 |
| --- | --- | --- | --- |
| GET | `/inspection-question-categories` | `USER` | 목록. `sortOrder` → `categoryId` 순서. 기본은 활성 분류만. `?includeDisabled=true` |
| POST | `/inspection-question-categories` | **`ADMIN`** | 등록 |
| PUT | `/inspection-question-categories/{categoryId}` | **`ADMIN`** | 이름 변경 |
| PATCH | `/inspection-question-categories/{categoryId}/status` | **`ADMIN`** | `enabled` 토글 |
| PUT | `/inspection-question-categories/order` | **`ADMIN`** | 순서 일괄 변경 |

조회(`GET`)가 `USER`에게도 열려 있는 이유는 질문 관리와 같다 — 매물 문답 화면이 분류별 섹션을
그리려면 일반 사용자도 분류 목록을 읽어야 한다.

### 요청/응답 스키마

```json
POST /api/inspection-question-categories
{ "name": "채광·환기", "sortOrder": 1 }
→ 200
{ "categoryId": "INSPECTION_QUESTION_CATEGORY-0001", "name": "채광·환기",
  "sortOrder": 1, "enabled": true, "enabledQuestionCount": 0 }

PUT   /api/inspection-question-categories/{categoryId}         { "name": "채광·환기·냄새" }
PATCH /api/inspection-question-categories/{categoryId}/status  { "enabled": false }
PUT   /api/inspection-question-categories/order                [ { "categoryId": "...", "sortOrder": 1 } ]
```

`POST`/`PUT`/`PATCH`와 `GET`이 모두 같은 `InspectionQuestionCategoryRdo` 형태를 돌려준다.
`PUT .../order`만 `204 No Content`다.

```json
GET /api/inspection-question-categories?includeDisabled=false
→ [
  { "categoryId": "INSPECTION_QUESTION_CATEGORY-0001", "name": "채광·환기",
    "sortOrder": 1, "enabled": true, "enabledQuestionCount": 4 }
]
```

`enabledQuestionCount`는 이 분류에 속한 **활성** 질문 수다. 목록 조회가 항상 집계해서 함께
내려준다 — 관리 화면이 "비활성화 가능한가"를 이 값으로 미리 판단한다.

### 규칙

- `name`은 필수다. 앞뒤 공백을 제거하고 최대 50자다. **비활성 분류까지 포함해 전체에서**
  중복을 금지한다(자기 자신과의 충돌은 예외 — 변경 없는 저장은 통과한다).
- 중복이면 `409 INSPECTION_QUESTION_CATEGORY_DUPLICATED`다. 응답에 기존 분류의 id는 싣지 않는다.
  "비활성인 같은 이름 분류를 다시 켤까요?" 같은 흐름은 `GET ?includeDisabled=true` 목록에서 같은
  이름(앞뒤 공백 제거)을 찾아 만든다. 분류는 몇 개뿐이라 목록 조회로 충분하다.
- 등록 시 `sortOrder`가 0 이하면 맨 뒤(`max+1`)로 자동 채번한다.
- 비활성화(`{ "enabled": false }`)는 그 분류에 **활성 질문이 0개일 때만** 허용한다. 하나라도
  있으면 `409`다.
- 물리 삭제는 없다. 잘못 만든 분류도 `enabled=false`로만 내린다.
- `PUT .../order`도 질문 정렬과 같은 규칙이다 — 요청에 빠진 분류는 기존 순서를 유지하고,
  존재하지 않는 분류가 섞이면 요청 전체가 `400`으로 막힌다.

---

## 10. 질문 관리 (관리자)

| 메서드 | 경로 | 권한 | 설명 |
| --- | --- | --- | --- |
| GET | `/inspection-questions` | `USER` | 기본은 활성 질문만. `?includeDisabled=true`, `?withAnswerCount=true` |
| GET | `/inspection-questions/{questionId}/versions` | `USER` | 버전 이력. 버전별 `answerCount` 포함 |
| POST | `/inspection-questions` | **`ADMIN`** | 등록 |
| PUT | `/inspection-questions/{questionId}` | **`ADMIN`** | 문구·설명·선택지·단위 수정 → **새 버전** |
| PATCH | `/inspection-questions/{questionId}/policy` | **`ADMIN`** | `required`, `sortOrder`(분류 안에서의 순서) |
| PATCH | `/inspection-questions/{questionId}/status` | **`ADMIN`** | `enabled` 토글 |
| PATCH | `/inspection-questions/{questionId}/category` | **`ADMIN`** | 분류 이동 |
| PUT | `/inspection-questions/order` | **`ADMIN`** | 순서 일괄 변경 |

### 요청 스키마

```json
POST /api/inspection-questions
{ "content": "거실 채광은 어떤가?", "description": "오후 기준", "answerType": "LONG_TEXT",
  "required": true, "sortOrder": 1, "choices": [], "unit": null,
  "categoryId": "INSPECTION_QUESTION_CATEGORY-0001" }

PUT /api/inspection-questions/{questionId}            // 문구·설명·선택지·단위. 새 버전이 생긴다
{ "content": "...", "description": "...", "choices": [{ "code": "SOUTH", "label": "남향", "sortOrder": 1 }], "unit": null }

PATCH /api/inspection-questions/{questionId}/policy    { "required": true, "sortOrder": 3 }
PATCH /api/inspection-questions/{questionId}/status    { "enabled": false }
PATCH /api/inspection-questions/{questionId}/category  { "categoryId": "INSPECTION_QUESTION_CATEGORY-0002" }
PUT   /api/inspection-questions/order                  [ { "questionId": "...", "sortOrder": 1 } ]
```

`answerType`은 `PUT`에 **없다.** 등록할 때만 정할 수 있다.

**모든 질문은 분류에 속한다.** 등록할 때 `categoryId`가 필수이고, 분류를 바꾸는 것은
`PATCH .../category` 전용 API로만 한다 — `PUT`(문구 수정)과 `PATCH .../policy`는 `categoryId`를
받지 않는다.

등록(`POST`)과 이동(`PATCH .../category`)의 분류 검증은 같다. FE는 `code`로 원인을 가른다.

| 경우 | 응답 |
| --- | --- |
| `categoryId` 누락·빈 문자열 | `400 INSPECTION_QUESTION_CATEGORY_REQUIRED` |
| 없는 분류 | `400 INSPECTION_QUESTION_CATEGORY_NOT_FOUND` |
| 비활성 분류 | `400 INSPECTION_QUESTION_CATEGORY_DISABLED` |

- `PATCH .../category`는 지정한 분류의 **맨 뒤**(`max(sortOrder)+1`)로 옮긴다. 이미 그 분류에
  있으면 아무것도 바꾸지 않고 현재 상태를 반환한다. **비활성 질문도 옮길 수 있다**(대상 분류만
  활성이면 된다). **버전을 올리지 않는다** — `required`, `sortOrder`처럼 수집 정책 성격이라서다.
- `sortOrder`는 **분류 안에서의 순서**다. `POST`에서 0 이하로 보내면 그 분류의 맨 뒤로 채번된다.
  `PATCH .../policy`는 자동 채번하지 않고 **보낸 값을 그대로 저장한다.**
- 비활성 분류에 속한 질문은 `PATCH .../status`로 다시 활성화할 수 없다
  (`400 INSPECTION_QUESTION_REACTIVATION_BLOCKED`) — 이 검증이 없으면 "활성 질문이 있는 분류는
  비활성화 불가" 규칙을 질문 쪽에서 우회할 수 있다.

**`PUT .../order`는 분류를 검증하지 않는다.** 여러 분류의 질문을 섞어 보내도 허용되고, 받은
`sortOrder`를 그대로 저장한다. `sortOrder`는 같은 분류 안에서만 비교되므로 섞어 보내도 결과가
깨지지는 않지만, **한 요청에는 한 분류의 질문만** 보내길 권한다. 이 API로 분류를 바꿀 수는 없다.

**분류를 넘나드는 드래그는 지원하지 않는다.** 다른 분류의 원하는 위치로 옮기려면
`PATCH .../category`(맨 뒤로 이동) 다음 `PUT .../order`를 불러야 하는데, 두 호출은 한 트랜잭션이
아니어서 두 번째가 실패하면 질문이 새 분류의 맨 뒤에 남는다. 데이터가 깨지지는 않고 다시 끌어
놓으면 되지만, UI는 **분류 이동은 별도 선택, 순서 변경은 분류 안에서만**으로 나눠 이 상황을
만들지 않는다.

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
  "answerCount": null,
  "categoryId": "INSPECTION_QUESTION_CATEGORY-0001",
  "categoryName": "채광·환기",
  "categorySortOrder": 1
}
```

`categoryId`/`categoryName`/`categorySortOrder`는 항상 채워져 있다(`null` 아님).

`PUT /inspection-questions/order`만 `204 No Content`다.

`GET /inspection-questions` 목록은 **분류.`sortOrder` → 질문.`sortOrder` → `questionId`** 순으로
정렬된다.

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

## 11. 에러 코드

모든 에러는 같은 형태다. 비즈니스 예외, 입력 검증, 401/403/404/405/415가 모두 이 본문으로 나간다.
`Content-Type`은 `Accept`와 상관없이 항상 `application/json`이다. 모양은 Spring `ProblemDetail`과
비슷하지만 RFC 9457 문서가 아니므로 `application/problem+json`으로 내려가지 않는다.

```json
{
  "title": "질문 분류는 필수입니다.",
  "status": 400,
  "code": "INSPECTION_QUESTION_CATEGORY_REQUIRED",
  "errors": []
}
```

| 필드 | 의미 |
| --- | --- |
| `title` | 사람이 읽는 문장. 그대로 보여 줘도 된다. **파싱하지 않는다** — 문구는 바뀔 수 있다 |
| `status` | HTTP 상태와 같은 값 |
| `code` | 원인 식별자. **FE는 이 값으로 분기한다.** 아래 표의 코드 이름이 그대로 들어간다 |
| `errors` | 필드 단위 검증 실패 목록. 그 밖의 에러는 **빈 배열**이다(`null`이 아니다) |

`errors`의 각 항목은 `{ "field": "quiz.title", "code": "NOT_BLANK", "message": "..." }`이다.
`code`는 위반 종류를 대문자 스네이크 케이스로 적은 것이다(`NOT_BLANK`, `SIZE`, `TYPE_MISMATCH`,
`REQUIRED` 등). `title`에는 첫 번째 항목의 `message`가 들어간다.

**공통 코드**

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 본문·파라미터 바인딩 검증 실패. `errors`에 필드 목록. 주소 검색 파라미터와 `location`/`coordKey` 위반은 `errors` 없이 `title`만 담긴다 |
| `INVALID_REQUEST_BODY` | 400 | 본문을 읽을 수 없음(JSON 문법 오류, 없는 enum 값 등) |
| `MISSING_PARAMETER` | 400 | 필수 쿼리 파라미터 누락. `errors`에 파라미터 이름 |
| `INVALID_PARAMETER` | 400 | 쿼리 파라미터 타입 불일치. `errors`에 파라미터 이름 |
| `BAD_REQUEST` | 400 | 그 밖의 잘못된 입력 |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료·위조 |
| `ACCESS_ROLE_DENIED` | 403 | 권한 부족. `ADMIN` 전용 API를 `USER`가 호출 |
| `NOT_FOUND` | 404 | 없는 경로 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `PAYLOAD_TOO_LARGE` | 413 | 업로드 요청이 60 MB 초과 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 파일·미디어 타입 |
| `INTERNAL_SERVER_ERROR` | 500 | 처리하지 못한 서버 오류 |

**임장 코드**

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `INSPECTION_AREA_NOT_FOUND` | 400 | 지역 없음 |
| `INSPECTION_AREA_IN_USE` | 409 | 임장 기록이 있는 지역 삭제 |
| `INSPECTION_AREA_DUPLICATED` | 409 | 동일 지역명 존재. `title`에 기존 `areaId` |
| `INSPECTION_VISIT_NOT_FOUND` | 400 | 임장 없음 |
| `INVALID_INSPECTION_VISIT` | 400 | 임장 입력/완료 조건 위반. 완료된 임장의 필수 항목을 비우는 수정 포함 |
| `VIEWED_PROPERTY_NOT_FOUND` | 400 | 매물 없음, 또는 다른 임장의 매물 |
| `INVALID_VIEWED_PROPERTY` | 400 | 매물 입력/완료 조건 위반. `name`이 비어 있는 매물의 완료 포함 |
| `INSPECTION_QUESTION_NOT_FOUND` | 400 | 질문 없음, 또는 이 매물의 문답에 없는 `questionId` |
| `INVALID_INSPECTION_QUESTION` | 400 | 질문 입력 위반(문구·설명·선택지·단위, 정렬 요청의 없는 질문) |
| `INSPECTION_QUESTION_CATEGORY_REQUIRED` | 400 | 질문 등록·이동에 `categoryId` 누락 |
| `INSPECTION_QUESTION_CATEGORY_DISABLED` | 400 | 비활성 분류로 질문 등록·이동 |
| `INSPECTION_QUESTION_REACTIVATION_BLOCKED` | 400 | 비활성 분류에 속한 질문을 다시 활성화 |
| `INSPECTION_QUESTION_CONFLICT` | 409 | 질문 동시 수정 |
| `INSPECTION_QUESTION_CATEGORY_NOT_FOUND` | 400 | 분류 없음 |
| `INVALID_INSPECTION_QUESTION_CATEGORY` | 400 | 분류 이름 누락/50자 초과, 정렬 요청에 존재하지 않는 분류 포함 |
| `INSPECTION_QUESTION_CATEGORY_DUPLICATED` | 409 | 동일 분류명 존재(비활성 분류 포함). 기존 id는 싣지 않는다(§9) |
| `INSPECTION_QUESTION_CATEGORY_IN_USE` | 409 | 활성 질문이 있는 분류를 비활성화 |
| `INSPECTION_QUESTION_CATEGORY_CONFLICT` | 409 | 분류 동시 수정 |
| `INSPECTION_ANSWER_REQUIRED` | 400 | 필수 문답 미완료. `title`에 `questionIds` |
| `INVALID_PROPERTY_ANSWER` | 400 | 문답 값이 타입과 안 맞음 |
| `INVALID_INSPECTION_FILE` | 400 | 사진 연결 정보 오류 |
| `INVALID_INSPECTION_ORDER` | 400 | 정렬 대상이 이 임장 소속이 아님 |
| `REVIEW_SUGGESTION_UNAVAILABLE` | 503 | AI 후기 제안 불가: API 키 미설정, 호출 실패·타임아웃, 응답 해석 실패(§8-1) |
| `INVALID_ADDRESS_KEYWORD` | 400 | 행안부가 주소 검색어를 거절(두 글자 미만, 특수문자·SQL 예약어 등). `title`에 사유(§8-2) |
| `ADDRESS_COORDINATE_NOT_FOUND` | 400 | 주소는 검색되지만 행안부에 좌표 정보가 없음. 위치 없이 저장하도록 안내(§8-2, §8-4) |
| `ADDRESS_LOOKUP_UNAVAILABLE` | 503 | 주소 검색·좌표 조회 불가: 행안부 키 미설정·승인 안 됨·만료. 관리자가 고쳐야 한다(§8-2) |
| `ADDRESS_LOOKUP_FAILED` | 502 | 행안부 호출 실패: 타임아웃, 5xx, 응답 해석 불가(§8-2) |
| `WALKING_ROUTE_UNAVAILABLE` | 503 | 도보 경로 불가: 카카오 REST 키 미설정 또는 거절(§8-3) |
| `WALKING_ROUTE_QUOTA_EXCEEDED` | 429 | 카카오 일일 한도 초과(§8-3) |
| `WALKING_ROUTE_FAILED` | 502 | 도보 경로 호출 실패 또는 경로 없음(§8-3) |
| `INSPECTION_VISIT_CONFLICT` | 409 | 임장 동시 저장 |
| `VIEWED_PROPERTY_CONFLICT` | 409 | 매물 동시 저장 |

**`ADMIN` 전용은 질문 관리·질문 대분류 관리의 쓰기(`POST`/`PUT`/`PATCH`)뿐이다.**
`GET /inspection-questions`, `GET /inspection-question-categories`, 버전 이력은 `USER`도 부를 수
있다 — 매물을 만들 때 활성 질문과 분류를 읽어야 하기 때문이다. 그래서 두 관리 화면 모두
`USER`에게 열리고 **저장할 때만 403**이 난다. 화면 진입을 막으려면 FE가 `POST /users/token`
응답의 `roleList`로 라우트를 가드해야 한다.

분류도 질문과 같은 낙관적 잠금을 쓴다 — 두 관리자가 같은 분류를 정확히 동시에 고치면 나중
요청이 `INSPECTION_QUESTION_CATEGORY_CONFLICT`(409)로 거절된다. 위 §10의 `409`에 대한 주석
("드물게 나는 재시도 안내" 수준, 실제 겹침만 감지)이 그대로 적용된다.

입력 길이 상한(초과 시 `400`): 지역명 100자, 지역 설명 300자, 단지명·매물명 각 200자,
한줄평 300자, 메모·장단점 5,000자, 태그 이름 50자, 질문 문구 300자, 질문 설명 500자, 단위 20자,
**질문 분류명 50자**.

---

## 12. 운영: 대분류 도입 DB 이관

FE가 알 필요는 없다. 배포 담당자가 참고한다.

새 버전은 **모든 질문과 모든 답변 스냅샷에 분류가 있다**고 가정한다(§4, §10). 그래서 대분류가 없던
DB에 그대로 배포하면 안 되고, 먼저 `docs/field_research/question_category_migration.sql`을 실행한다.

1. 앱을 내린다. 이관 도중 질문·매물이 새로 생기면 분류 없는 행이 다시 섞인다.
2. 스크립트 머리의 사전 조회로 기존 질문을 확인하고, 분류 목록과 질문→분류 대응표를 채운다.
   비활성 질문도 빠짐없이 지정한다.
3. 스크립트를 실행한다. 한 트랜잭션이라, 분류를 지정하지 않은 질문이나 분류를 채우지 못한
   답변이 남으면 예외로 멈추고 **아무것도 바뀌지 않는다.**
4. 새 버전을 배포한다.

스크립트가 하는 일: 분류 테이블 생성 → 분류 등록과 `id_sequence` 맞춤 → 질문에 `category_id` 지정 →
`NOT NULL`과 분류 FK 적용 → 기존 매물 `answers` JSON의 각 답변에 `categoryId`/`categoryName`/
`categorySortOrder` 추가. 이미 분류가 있는 답변은 건드리지 않고, 배열 순서도 바꾸지 않는다.
