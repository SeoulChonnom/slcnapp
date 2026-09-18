# 임장 기록 API (FE 연동)

context path는 `/api`다. 아래 경로는 그 뒤에 붙는다. 인증은 `X-AUTH-TOKEN` 헤더 또는
`Authorization: Bearer ...`를 쓴다.

설계 근거와 내부 구조는 `docs/field_research/implementation_design.md`를 본다.
이 문서는 **FE가 호출할 때 알아야 하는 것**만 담는다.

---

## 1. 화면과 API의 대응

| 화면 | 호출 |
| --- | --- |
| 지역 목록 | `GET /inspection-areas` |
| 지역 상세(회차 탭) | `GET /inspection-areas/{areaId}` — 회차 목록 + 선택 회차 상세를 한 번에 |
| 임장 목록 | `GET /inspection-visits` |
| 임장 상세 | `GET /inspection-visits/{visitId}` |
| 매물 상세 | `GET /inspection-visits/{visitId}/properties/{propertyId}` |
| 질문 관리(관리자) | `GET/POST/PUT/PATCH /inspection-questions` |

지역 상세 하나로 회차 탭 전환까지 처리된다. 회차를 바꿀 때 `?visitId=`만 갈아끼우면 되고,
회차가 많아 지연 로딩으로 전환할 때는 `?includeProperties=false`로 목록만 받는다.

---

## 2. 임장 지역

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-areas` | 목록 + 회차 집계. `?keyword=` 지역명 부분 일치 |
| GET | `/inspection-areas/{areaId}` | 상세. `?visitId=`, `?includeProperties=`(기본 true) |
| POST | `/inspection-areas` | 등록 |
| PUT | `/inspection-areas/{areaId}` | 수정 |
| DELETE | `/inspection-areas/{areaId}` | 임장 기록이 1건이라도 있으면 `409` |

**지역은 생활권 단위다.** "성수동", "잠실"이 들어가고 "트리마제" 같은 단지명은 매물의
`complexName`으로 내려간다.

동일 지역명이 이미 있으면 `409`와 함께 메시지에 기존 `areaId`가 담긴다. FE는 이걸 잡아
"기존 지역에 임장 추가"를 고르게 한다.

### 지역 목록 응답

```json
[
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
    "incompleteSummary": { "draftVisitCount": 1, "draftPropertyCount": 2, "unansweredRequiredCount": 5 },
    "thumbnails": [{ "id": "...", "fileAssetId": "...", "file": { "...": "FileAssetRdo" } }],
    "totalImageCount": 24
  }
]
```

- `topProperty`는 **지역 전체**에서 고른다. 임장 목록의 `topInterestProperty`는 그 회차 안에서만 고른다.
- `latestVisit`에는 `propertyCount`/`cover`/`incompleteSummary`가 비어 있다. 회차별 값이 필요하면 지역 상세를 부른다.
- 집계 필드는 전부 서버가 조회 시 계산한다. 임장을 등록/삭제하면 다음 조회에 즉시 반영된다.

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

- `visits`는 `visitedAt` 내림차순이고 **50건을 넘으면 최신 50건으로 잘린다.** 이때 `hasMoreVisits: true`가 오므로 `GET /inspection-visits?areaId=`로 이어받는다.
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

- `areaId` 또는 `area` 중 **하나는 필수**다. `area`를 보내면 지역이 함께 생성된다.
- `visitedAt`은 `DRAFT`에서도 필수다. 목록 정렬 축이기 때문이다.
- `revisitIntent`는 `DRAFT`에서 비워도 되지만 **완료할 때는 필수**다.
- `files`의 `targetType`/`targetId`는 보내지 않아도 된다. 서버가 경로에서 확정한다.

---

## 4. 확인 매물과 문답

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/inspection-visits/{visitId}/properties` | 등록. 활성 질문 문답이 함께 생성된다 |
| GET | `/inspection-visits/{visitId}/properties/{propertyId}` | 상세 |
| GET | `/inspection-visits/{visitId}/complex-names` | 이 임장에 이미 쓴 단지명. 자동완성용 |
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

**② `COMPLETED` 매물의 필수 문답을 비우는 저장은 `400`으로 거절된다.** 기본 정보 수정과
문답 저장 양쪽 모두 해당한다. 고쳐야 하면 먼저 `status`를 `DRAFT`로 되돌린 뒤 수정한다.

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
GET /api/inspection-tags?keyword=한
```

사용 빈도 내림차순, 동률이면 이름순으로 내려간다. 자동완성에 쓴다.

임장 태그와 매물 태그는 **같은 풀을 공유**한다. 태그 생성/삭제 API는 없다 —
임장·매물 저장 시 `tags: ["한강", "직주근접"]`처럼 **이름 배열**을 보내면 서버가
정규화(`#` 제거, 앞뒤 공백 제거)한 뒤 없으면 만든다. 태그 ID를 FE가 관리하지 않는다.

- 요청의 태그 목록이 최종 상태다. 빠진 태그는 연결이 끊긴다.
- `tags`를 **생략하면** 기존 연결을 유지한다. 전부 떼려면 `[]`를 보낸다.
- 임장·매물 각각 최대 10개다.

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

삭제 API는 없다. 잘못 만든 질문은 `enabled=false`로 내린다.

**타입(`answerType`)은 등록할 때만 정할 수 있고 이후 바꿀 수 없다.** 바꿔야 하면 기존
질문을 미사용 처리하고 새 질문을 만든다. 과거 답변은 그대로 조회된다.

입력 제약:

- 선택지(`choices`)는 `SINGLE_SELECT`/`MULTI_SELECT`에만 쓸 수 있고, 이 두 타입에는 최소 1개가 필요하다.
- 단위(`unit`)는 `NUMBER`에만 쓸 수 있다.
- 선택지 `code`는 질문 안에서 유일해야 한다.

`?withAnswerCount=true`는 전건 스캔이라 느리다. **관리자 화면에서만 켜고 평소에는 끈다.**

두 관리자가 같은 질문을 동시에 고치면 나중 요청이 `409`로 거절된다. "질문이 이미
수정되었습니다. 새로고침 후 다시 시도하세요" 메시지를 그대로 보여주면 된다.

---

## 10. 에러 코드

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `INSPECTION_AREA_NOT_FOUND` | 400 | 지역 없음 |
| `INSPECTION_AREA_IN_USE` | 409 | 임장 기록이 있는 지역 삭제 |
| `INSPECTION_AREA_DUPLICATED` | 409 | 동일 지역명 존재. 메시지에 기존 `areaId` |
| `INSPECTION_VISIT_NOT_FOUND` | 400 | 임장 없음 |
| `INVALID_INSPECTION_VISIT` | 400 | 임장 입력/완료 조건 위반 |
| `VIEWED_PROPERTY_NOT_FOUND` | 400 | 매물 없음, 또는 다른 임장의 매물 |
| `INVALID_VIEWED_PROPERTY` | 400 | 매물 입력/완료 조건 위반 |
| `INSPECTION_QUESTION_NOT_FOUND` | 400 | 질문 없음, 또는 이 매물의 문답에 없는 `questionId` |
| `INVALID_INSPECTION_QUESTION` | 400 | 금지된 질문 수정 |
| `INSPECTION_QUESTION_CONFLICT` | 409 | 질문 동시 수정 |
| `INSPECTION_ANSWER_REQUIRED` | 400 | 필수 문답 미완료. 메시지에 `questionIds` |
| `INVALID_PROPERTY_ANSWER` | 400 | 문답 값이 타입과 안 맞음 |
| `INVALID_INSPECTION_FILE` | 400 | 사진 연결 정보 오류 |
| `INVALID_INSPECTION_ORDER` | 400 | 정렬 대상이 이 임장 소속이 아님 |
| `PAYLOAD_TOO_LARGE` | 413 | 업로드 요청이 60 MB 초과 |
