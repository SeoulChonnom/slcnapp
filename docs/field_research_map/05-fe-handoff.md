# 05. FE 전달 — 임장 계획 · 지도 경로 BE 변경

> 작성일: 2026-10-08. BE 브랜치 `feature/field-research-map`(커밋 C1~C11)의 결과를 FE 작업(04 문서 §10.3, FC1~FC6)에 넘긴다.
> **API 계약의 정본은 `docs/field_research/api.md`** 다. 이 문서는 FE가 무엇을 언제 바꿔야 하는지만 정리한다.
> 결정 근거는 04 문서(D1~D14)를 본다.

## 1. 한눈에 보기

| 변경 | FE 영향 | api.md | FE 커밋 |
|---|---|---|---|
| 매물 `name`이 `null`일 수 있다 | **스키마를 넓히지 않으면 화면이 깨진다** | §3, §4 | FC1 |
| 임장 응답에 `completedAt` 추가 | 배지 3종 판정 | §1, §3 | FC2 |
| 지역 집계가 완료 임장 기준으로 바뀜, `plannedVisit` · `plannedVisitCount` 추가 | 지역 목록 "예정/미완료" 표시 | §1, §5 | FC3 |
| 매물 요청·응답에 `location` 추가, 주소 검색 API | 주소 검색 UI, 지도 마커 | §8-2 | FC4 |
| 도보 경로 API | "도보 경로 보기" | §8-3 | FC5 |
| 현재 위치 좌표 변환 API | "추천 순서로 정렬" | §8-4 | FC6 |

기존 필드의 이름이나 타입은 바뀌지 않았다. **추가**이거나 **nullable로 넓어진 것**뿐이다. 단 하나의 예외가 `name`이다(§2).

## 2. 반드시 같은 배포에 넣을 것 (D10)

| BE | FE | 이유 |
|---|---|---|
| C4 (매물명 생략 허용) | **FC1** (매물 `name` nullable 스키마) | C4가 배포된 뒤 누군가 이름 없이 매물을 저장하면, FC1이 없는 FE는 `parseOrThrow`에서 실패해 **임장 상세 · 지역 목록 · 매물 상세가 통째로 깨진다** |

그 밖의 조합은 어느 쪽이 먼저 떠도 깨지지 않는다.
- 새 FE가 보내는 `location`은 옛 BE가 무시한다.
- 새 응답 필드는 옛 FE가 무시한다. zod 스키마가 `strict`가 아니라는 전제다. FC 착수 전에 확인한다.

## 3. 스키마 변경 목록 (zod)

### 3.1 nullable로 넓힐 것 — FC1

매물 `name: z.string()` → `z.string().nullable()`. 04 §10.2 F1에서 찾은 4곳이다.
- `viewedPropertyBriefSchema`: `topProperty`, 이전·다음 매물
- `matchedPropertySchema`
- `areaViewedPropertySchema`
- `viewedPropertyDetailSchema`

화면 표시는 "동·호수 미정"이다. 등록 폼에서 `name`은 선택 입력으로 바꾼다.
완료하려고 하면 BE가 `400 INVALID_VIEWED_PROPERTY`로 거절하고, 이때 `missingFields`에 `name`이 담긴다.

### 3.2 추가할 필드

```ts
// 임장: 목록 항목, 상세, 지역 행의 latestVisit, 지역 상세의 visits[]
completedAt: string | null          // 최초 완료 시각. visitedAt과 같은 형식

// 지역 행(지역 목록 items[], 지역 상세의 area)
plannedVisit: { inspectionVisitId: string; visitedAt: string } | null

// incompleteSummary (모든 요약에 있음. 지역 요약에서만 의미가 있고 나머지는 0)
plannedVisitCount: number

// 매물: 상세, 임장 상세 properties[], 지역 상세 매물 목록
location: {
  bdMgtSn: string
  roadAddress: string | null
  latitude: number                  // WGS84 도
  longitude: number
} | null
```

**날짜 문자열 형식에 주의한다.** `visitedAt`과 `completedAt`은 Java `LocalDateTime.toString()` 형식이다.
초가 0이면 초를 생략한다(`2026-09-17T18:30`). 그렇지 않으면 초까지 나온다(`2026-09-23T09:28:52`).
`completedAt`은 초가 붙어 나올 때가 많으므로 파서가 두 형식을 모두 받아야 한다.

### 3.3 새 응답 타입

```ts
// GET /geo/addresses
type AddressSearch = {
  totalCount: number
  items: Array<{
    roadAddress: string | null; jibunAddress: string | null; buildingName: string | null
    bdMgtSn: string; zipNo: string | null
    coordKey: CoordKey
  }>
}
type CoordKey = { admCd: string; rnMgtSn: string; udrtYn: string; buldMnnm: string; buldSlno: string }

// POST /inspection-visits/{visitId}/walking-route
type WalkingRoute = {
  totalDistance: number             // m
  totalTime: number                 // 초
  stops: Array<{ index: number; propertyIds: string[]; latitude: number; longitude: number }>
  legs: Array<{
    fromStopIndex: number; toStopIndex: number; distance: number; time: number
    path: Array<[number, number]>   // ⚠️ [경도, 위도] 순서 (GeoJSON)
  }>
}

// POST /geo/current-location
type GeoPoint = { latitude: number; longitude: number }
```

## 4. 화면별 규칙

### 4.1 배지 (FC2)

| 조건 | 배지 |
|---|---|
| `completedAt == null` | **계획** |
| `completedAt != null` && `status == DRAFT` | **수정 중** |
| `status == COMPLETED` | 없음 |

- 배지는 `status`만 보고 판정하지 않는다. `completedAt`을 함께 본다.
- 기존 완료 임장은 배포 때 SQL로 `completedAt`을 채운다(04 §5.4). 그래서 배포 직후에도 "계획"으로 보이지 않는다.
- 문구 변경(04 §10.2 F5)
  - "작성 중으로 되돌리기" → "완료 해제하고 수정"
  - "아직 작성 중입니다" → "아직 완료 조건이 남았습니다"
  - 등록 2단계의 "방문일" → "예정일"

### 4.2 지역 목록 (FC3)

- `visitCount`, `lastVisitedAt`, 정렬, 재방문 필터, `totals`는 **완료 임장만** 센다.
  계획만 있는 지역은 `visitCount: 0`, `latestVisit: null`로 목록에 나온다.
- `plannedVisit`은 미완료 임장 중 예정일이 가장 이른 1건이다(D14).
  - `visitedAt`이 오늘 이후면 **"예정 MM.DD"**, 지났으면 **"미완료 MM.DD"**로 표시한다.
  - 오늘은 FE가 판정한다(BE는 비교하지 않는다).
- 여러 건이면 `incompleteSummary.plannedVisitCount`로 개수를 보여 줄 수 있다.
- 지역 상세에서 `visitId` 없이 열면 `selectedVisit`에 **가장 최근에 완료한 임장**(`completedAt`이 있는 것 중 `visitedAt` 내림차순, 같으면 `id` 오름차순 첫 건)이 펼쳐진다. 완료 임장이 없으면 `plannedVisit`(가장 이른 계획)이 펼쳐진다. 계획은 회차 목록에 그대로 있다.
- 회차 목록(`visits[]`)은 `visitedAt` 내림차순이라 미래 계획이 첫 행인데 다른 회차가 펼쳐져 있을 수 있다. **첫 행이 아니라 `selectedVisit.inspectionVisitId`로 선택 행을 강조**한다.

### 4.3 매물 위치 (FC4)

```text
주소 입력 → GET /geo/addresses?keyword=…  → 후보 선택
         → 매물 POST/PUT 본문에 location: { bdMgtSn, roadAddress, coordKey } 그대로 실어 보냄
```

- **좌표는 보내지 않는다.** 서버가 행안부에서 구해 저장한다.
- **PUT은 전체 교체다.** 수정 화면은 상세 응답의 `location`을 `{ bdMgtSn, roadAddress }`로 돌려보낸다.
  `bdMgtSn`이 같으면 `coordKey` 없이도 유지된다. 생략하거나 `null`이면 **위치가 지워진다.**
  - `UpdateInspectionPropertyInput`에 `location`을 선택(`?`)이 아니라 **필수 키**(`location: PropertyLocationInput | null`)로 둔다(D9).
- 검색어 오류는 `400 INVALID_ADDRESS_KEYWORD`이고, `title`에 사유가 담겨 오므로 그대로 보여 준다.
  - 예: "주소를 상세히 입력해 주시기 바랍니다", "검색어는 두글자 이상 입력되어야 합니다"
- `keyword`가 공백이거나 `size`가 20을 넘으면 `400 VALIDATION_FAILED`다.
- 주소는 검색되지만 좌표 정보가 없으면 `400 ADDRESS_COORDINATE_NOT_FOUND`다. `title`의 안내를 보여 주고, 사용자가 **위치 없이 저장**할 수 있게 한다(`location: null`).
- 길이 제한: `bdMgtSn` 26자, `roadAddress` 300자(trim 후). 넘으면 `400 VALIDATION_FAILED`다. 검색 결과를 그대로 보내면 넘지 않는다.
- 행안부 장애로 `502`가 나면 매물 저장 전체가 실패한다. 입력값은 폼에 남겨 둔다.
- 지도 하단에 출처를 표기한다: "주소·좌표: 행정안전부". 카카오 로고는 가리지 않는다.

### 4.4 도보 경로 (FC5)

- 기본 화면은 번호 마커 + 직선 연결선 + 추정 시간이다. 추정 시간은 `직선 × 1.3 ÷ 4.5km/h`다.
- "도보 경로 보기"를 누를 때만 `POST .../walking-route`를 호출한다. 경로는 화면 세션 동안 메모리에만 캐시한다.
- 응답의 `stops`는 서버가 이미 정리한 결과다. 위치 없는 매물은 빠지고, 바로 이어지는 같은 좌표는 하나로 합쳐진다. FE는 `stops[].propertyIds`로 마커에 "2·3" 같은 번호를 붙인다.
- 구간(`legs`)은 보통 인접한 두 지점 사이지만, 제공자가 경로를 나눠 주지 않으면 한 구간이 여러 지점에 걸친다(`toStopIndex - fromStopIndex > 1`). `path`를 그대로 그리고 `stops.length - 1`개라고 가정하지 않는다.
- `legs[].path`가 비어 있으면 그 구간만 두 지점을 잇는 직선으로 그린다.
- `legs[].path`는 **[경도, 위도]** 다. 카카오 SDK에는 `new kakao.maps.LatLng(p[1], p[0])`로 넘긴다.
- 오류(`429`, `502`, `503`)가 나면 직선 표시를 유지하고 토스트만 띄운다. 빈 경로(`stops: []`)는 오류가 아니다.
- 지난 임장의 경로는 "화면 순서대로 걸었을 때의 현재 기준 추천 경로"라고 짧게 안내한다.

### 4.5 추천 순서로 정렬 (FC6)

- 현재 위치도 §4.3과 같은 주소 검색으로 고른 뒤 `POST /geo/current-location`에 `coordKey`를 보낸다. 받은 좌표는 **메모리에만** 둔다(서버도 저장·로그하지 않는다).
- **"현재 위치 → 첫 매물" 구간은 직선으로만 그린다(D13).** 도보 경로 API는 저장된 매물 좌표만 쓴다.
- 순서 계산(`route-order.ts`)은 FE 순수 함수다. 적용은 기존 `PUT /properties/order`로 한다.
- 완료 임장에서는 버튼을 숨긴다.

## 5. 오류 코드 요약

| HTTP | `code` | FE 처리 |
|---|---|---|
| 400 | `INVALID_ADDRESS_KEYWORD` | `title`을 검색창 아래에 표시 |
| 400 | `ADDRESS_COORDINATE_NOT_FOUND` | `title` 안내를 보여 주고 위치 없이 저장하게 한다 |
| 400 | `VALIDATION_FAILED` | 입력 확인 안내 (`location`/`coordKey` 위반은 `errors` 없이 `title`만) |
| 400 | `INVALID_VIEWED_PROPERTY` | 매물 완료 조건 미충족(`name` 포함) |
| 400 | `INSPECTION_VISIT_NOT_FOUND` | 임장 없음 (이 서비스는 404가 아니라 400) |
| 429 | `WALKING_ROUTE_QUOTA_EXCEEDED` | 직선 유지 + "오늘 경로 조회 한도를 넘었습니다" |
| 502 | `ADDRESS_LOOKUP_FAILED` / `WALKING_ROUTE_FAILED` | 잠시 후 다시 시도 |
| 503 | `ADDRESS_LOOKUP_UNAVAILABLE` / `WALKING_ROUTE_UNAVAILABLE` | 기능 꺼짐(키 미설정). 주소 입력 · 경로 버튼을 비활성화해도 된다 |

## 6. 개발 환경

- 로컬 BE는 키 없이도 뜬다. 이때 주소 검색, 위치 저장, 경로, 현재 위치 API는 모두 `503`이다.
  위치 없이 하는 매물 등록과 그 밖의 기능은 그대로 쓸 수 있다.
- 실제 연동을 붙이려면 BE 환경변수를 설정한다. 키는 BE에만 둔다.
  - 행안부: `SLCN_JUSO_SEARCH_KEY`, `SLCN_JUSO_COORD_KEY`
  - 카카오: `SLCN_KAKAO_REST_KEY`
- FE에는 카카오 **JavaScript 키**(`VITE_KAKAO_MAP_JS_KEY`, 도메인 제한)만 둔다.

## 7. BE 검증 상태와 남은 확인

| 항목 | 상태 |
|---|---|
| 지역 집계 · 정렬 · 필터 · `plannedVisit` · 미완료 요약 | 로컬 PostgreSQL에서 기대값과 대조 완료 |
| 매물명 생략 · 완료 차단 | 실제 호출로 확인 |
| 위치 판정 규칙(유지 · 재조회 · 400 · 502 시 미저장 · 삭제) | 행안부 **모의 서버**로 실제 호출 확인 |
| 도보 경로 분할 · 병합 · 오류 매핑 · 키 비노출 | 카카오 **모의 서버**로 실제 호출 확인 |
| 행안부 실제 응답 필드명 · 단지명 검색 품질 · 좌표 API | ⏳ **실제 키 발급 후 확인** (좌표 키 승인 대기) |
| 카카오 실제 응답 · 429 동작 | ⏳ 실제 키 확인 필요 |

실제 키로 확인하다가 필드명이 다르면 BE 어댑터만 고치고 API 계약은 유지한다.
