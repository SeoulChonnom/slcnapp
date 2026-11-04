# 내집찾기 — FE 연동 가이드

BE 구현(PR #12, `feature/myseoulhouse`)을 기준으로 FE가 화면을 만들 때 필요한 계약과 표시 규칙을 정리한다. 화면 구성·사용자 흐름의 원본은 `spec.md`이고, 이 문서는 **실제 구현된 응답**과 **구현 중 바뀌거나 정해진 것**을 우선한다. 둘이 다르면 이 문서가 맞다.

## 0. 이번 개선에서 FE가 알아야 할 변경

| 항목 | 내용 | FE 영향 |
| --- | --- | --- |
| 연식 기준 통일 | `buildYear` = 사용승인연도, 없으면 실거래 건축년도. 지도·리스트·정보 카드·검색·비교표·랭킹 6곳이 모두 같은 값 | FE는 받은 값을 그대로 쓴다. 따로 보정하지 않는다 |
| 정비사업 데이터 | 서울시 정비사업 현황(OA-22856)을 관리자가 CSV로 등록 → 야간 배치가 구역 좌표를 구해 반경 500m 단지와 연결 | 비교표 7번 행 `redevelopments`가 `null` / `[]` / 목록 세 가지로 온다 (5장) |
| 관리자 API 2개 추가 | `POST /house/admin/redevelopments/import`, `GET /house/admin/redevelopments/incomplete` | 관리자 화면에 업로드·미완료 목록을 둘 수 있다 (8장) |
| 오류 상세 | 비교 `items` 오류와 CSV 오류는 `errors[]`에 위반 목록이 들어간다 | `errors[].field`·`message`를 그대로 보여 준다 |

## 1. 공통 규칙

- **경로**: `/api` 접두어 없이 `/house/...`. FE는 기존처럼 `VITE_API_URL=/api` 아래로 부른다.
- **인증**: 모든 경로가 로그인 회원 전용(401). `/house/admin/**`는 `ADMIN` 권한(403).
- **응답**: 감싸는 객체 없이 Rdo 본문 그대로. 목록은 `{ items: [...] }`.
- **null**: 값이 없는 필드는 키가 빠지지 않고 `null`로 온다. zod에서는 `.nullable()`로 받는다(`.optional()` 아님).
- **단위·형식**
  - 금액: 만원 정수 (`45000` = 4.5억)
  - 면적 구간: 요청·응답 모두 `B59` / `B84`. URL `cmp`의 `59`/`84`와의 변환은 `compare-param.ts`가 맡는다.
  - 날짜: `yyyy-MM-dd` 문자열
  - 거리: 미터 정수, 직선거리
  - 변동률·용적률: 소수 둘째 자리까지 숫자 (`4.65`)
- **ID**: `complexId`는 `HOUSE_COMPLEX-xxxx` 형식이고 `:`·`,`가 들어가지 않는다.

### 오류 응답

```json
{ "title": "area는 B59 또는 B84여야 합니다.", "status": 400, "code": "VALIDATION_FAILED", "errors": [] }
```

| `code` | HTTP | 언제 | `errors[]` |
| --- | --- | --- | --- |
| `VALIDATION_FAILED` | 400 | 입력 규칙 위반 (아래 표) | 비교 `items`, CSV 등록만 채워짐. 나머지는 `[]`이고 `title`에 사유 |
| `INVALID_PARAMETER` | 400 | enum·숫자 변환 실패 (`area=B60`, `type=TOP`, `swLat=abc`) | `[{ field, code: "TYPE_MISMATCH" }]` |
| `MISSING_PARAMETER` | 400 | 필수 파라미터 없음 (CSV `file`) | `[{ field, code: "REQUIRED" }]` |
| `HOUSE_COMPLEX_NOT_FOUND` | 404 | 정보 카드의 단지가 없음, 관리자 주소 보정 대상 단지가 없음 | — |
| `HOUSE_BUDGET_POLICY_NOT_FOUND` | 503 | 오늘 시행 중인 예산 정책이 없음(시드 누락). 예산 섹션만 오류 상태로 두고 나머지 섹션은 그대로 쓴다 | — |
| `UNAUTHORIZED` | 401 | 로그인 안 됨·토큰 만료 (기존 공통 처리) | — |
| `ACCESS_ROLE_MISSING`, `ACCESS_ROLE_DENIED` | 403 | `/house/admin/**`를 관리자가 아닌 회원이 호출 | — |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 오류 | — |

FE 분기는 `AppError.apiCode`(= `code`)로 한다. 응답 Content-Type은 항상 `application/json`이다.

`title`은 한국어 문장이지만 파라미터 이름이 영문 그대로 들어간다(`area 값이 올바르지 않습니다.`). 대부분은 FE가 입력을 막아서 볼 일이 없다. 사용자에게 보일 수 있는 곳(관리자 CSV 오류 등)만 `title`·`message`를 그대로 쓰고, 일반 화면은 FE 문구를 쓰는 편이 낫다.

**`VALIDATION_FAILED`가 나는 입력** — FE가 미리 막아야 할 것들이다.

| API | 조건 |
| --- | --- |
| 공통 | `area` 없음 또는 `OTHER` / `minPrice`·`maxPrice` 음수 / `minPrice > maxPrice` |
| `/house/complexes` | 영역 4개와 `lawdCd` 중 **정확히 하나**만 / 영역 4개 중 일부만 / `sw ≥ ne` / 한 변 > **0.2도** / `lawdCd`가 서울 25개 구 코드가 아님 |
| `/house/complexes/summary` | `groupBy` 없음 / `DISTRICT`인데 영역 파라미터 있음 / `DONG`인데 영역 없음 또는 한 변 > **1.0도** |
| `/house/complexes/search` | `keyword`가 trim 후 2자 미만 / `size`가 1~20 밖 |
| `/house/rankings` | `type` 없음 |
| `/house/compare` | `items` 0개, 4개 이상, 형식 오류, 중복 (아래 `errors[].code`) |

비교 `items` 오류의 `errors[]`:

| `field` | `code` | 의미 |
| --- | --- | --- |
| `items` | `FORMAT` | 비었음 |
| `items[i]` | `TOO_MANY` | 4번째 이후 항목 |
| `items[i]` | `FORMAT` | `id:B59` 형식이 아니거나 id가 빔 |
| `items[i]` | `INVALID_AREA` | 구간이 B59·B84가 아님 |
| `items[i]` | `DUPLICATED` | 같은 단지·같은 구간 중복 |

## 2. 엔드포인트 요약

| 메서드 | 경로 | 화면 | 파라미터 |
| --- | --- | --- | --- |
| GET | `/house/budget-policy` | 내 예산 | — |
| GET | `/house/meta` | 상단 기준일, 랭킹 하단 | — |
| GET | `/house/complexes` | 지도 단지 레이어, 후보 리스트 | 영역(`swLat`,`swLng`,`neLat`,`neLng`) **또는** `lawdCd`, `area`, `minPrice`?, `maxPrice`? |
| GET | `/house/complexes/summary` | 지도 구·동 묶음 | `groupBy=DISTRICT\|DONG`, `area`, `minPrice`?, `maxPrice`?, 영역(DONG만) |
| GET | `/house/complexes/search` | 비교 단지 검색 | `keyword`, `size`?(기본 10, 최대 20) |
| GET | `/house/complexes/{complexId}` | 정보 카드 | `area` |
| GET | `/house/compare` | 비교표 | `items=id:B59,id:B84` (1~3개) |
| GET | `/house/rankings` | 랭킹 탭 | `type=LEADER\|RISE\|FALL\|VOLUME`, `area` |
| GET | `/house/admin/complexes/incomplete` | 관리자: 보강 실패 단지 | `page`?(1부터), `size`?(기본 20) |
| PUT | `/house/admin/complexes/{complexId}/address-override` | 관리자: 주소 보정 | 본문 `{ roadAddress?, bonbun?, bubun? }` |
| POST | `/house/admin/redevelopments/import` | 관리자: 정비사업 등록 | multipart `file` |
| GET | `/house/admin/redevelopments/incomplete` | 관리자: 좌표 못 찾은 구역 | `page`?, `size`? |

## 3. 공용 타입

**예산 정책** (`GET /house/budget-policy`): 응답 구조는 `spec.md` §10 "예산 계산 규칙" 예시와 같다(코드와 대조 완료). `ltv`는 `{ FIRST_TIME, NON_FIRST }` 객체, `priceCaps`·`brokerage.tiers`는 오름차순이고 마지막 항목의 상한만 `null`이다. 이 문서에는 다시 적지 않는다.

```ts
type AreaBucket = 'B59' | 'B84';

// 최근 실거래가. 6개월 안에도 거래가 없으면 객체 자체가 null
type RecentPrice = {
  priceManwon: number;   // 해제·이상치·원천 삭제 제외 평균
  windowMonths: 3 | 6;   // 3개월 거래가 0건이면 6
  sample: number;        // 평균에 쓴 건수
} | null;
```

**최근 실거래가 표시** (지도·리스트·정보 카드·비교표 공통)

| 응답 | 표시 |
| --- | --- |
| `{ priceManwon: 45000, windowMonths: 3, sample: 5 }` | 4.50억 · 최근 3개월 5건 |
| `{ ..., windowMonths: 6, sample: 2 }` | 4.50억 · 최근 6개월 2건 + "표본 적음" 배지 (`sample < 3`) |
| `null` | "최근 6개월 거래 없음" |

**금액 표기**: 억 단위 자릿수와 반올림은 `spec.md` §11 "금액 표시"(`format-eok.ts`) 표를 따른다. 일반은 소수 둘째 자리, 지도 라벨은 첫째 자리이고, 1억 미만은 "N,NNN만"이다. BE는 만원 정수만 내려 준다.

**연식**: `buildYear`는 `number | null`. null이면 "정보 없음". 비교표만 `ageYears`(서버 기준 올해 − `buildYear`)를 함께 내려 준다. 다른 화면에서 년차가 필요하면 같은 식으로 계산하면 같은 값이 나온다.

## 4. 조회 API 응답

### 4.1 메타

```json
{ "dataAsOf": "2026-11-03", "statsComputedDate": "2026-11-03" }
```

- 둘 다 nullable.
- `dataAsOf: null` → 상단에 "데이터 준비 중".
- `statsComputedDate: null` → 후보 리스트와 랭킹에 "통계 준비 중이에요" 빈 상태. 이 빈 상태는 "조건에 맞는 단지가 없어요"와 구분한다. 이때 구·동 묶음 숫자도 숨긴다.
  - 이 상태에서도 다른 API는 오류 없이 200으로 온다. `/house/complexes`는 `recentPrice`가 모두 null이고, 가격 범위를 주면 `items: []`이다. 랭킹은 `period: null, items: []`이다. 그래서 "빈 결과"만으로는 원인을 알 수 없고, 반드시 `statsComputedDate`로 구분한다.

### 4.2 지도 영역 단지 — `GET /house/complexes`

```json
{
  "total": 34,
  "truncated": false,
  "items": [
    { "complexId": "HOUSE_COMPLEX-1203", "name": "OO아파트", "lawdCd": "11230", "districtName": "동대문구",
      "umdName": "OO동", "latitude": 37.5789, "longitude": 127.0451,
      "recentPrice": { "priceManwon": 45000, "windowMonths": 3, "sample": 5 },
      "buildYear": 1999, "households": 380 }
  ]
}
```

- 좌표가 없는 단지는 빠지므로 `latitude`·`longitude`는 항상 값이 있다.
- `minPrice`·`maxPrice`는 한쪽만 줘도 된다(없는 쪽은 하한 0, 상한 없음). 하나라도 주면 `recentPrice`가 있는 단지만 온다. 둘 다 없으면 `recentPrice: null`인 단지도 오므로 회색 점으로 그린다.
- 최대 300건, 가격 내림차순(가격 없는 단지는 뒤). `truncated: true`면 "{total}개 중 300개 표시 · 지도를 확대하면 모두 볼 수 있어요".
  - `lawdCd` 모드(지도 없는 모드)에도 같은 300건 제한이 있다. 이때는 확대할 수 없으므로 "{total}개 중 300개 표시 · 예산을 입력하면 후보를 좁힐 수 있어요"처럼 문구를 바꾼다.
- `minPrice ≤ 가격 ≤ maxPrice`(양 끝 포함). 예산 결과 P가 있으면 `minPrice` = 0.8P, `maxPrice` = P (만원 정수).
- `households`는 null일 수 있다(건축물대장 보강 전·실패).

### 4.3 구·동 묶음 — `GET /house/complexes/summary`

```json
{
  "groupBy": "DONG",
  "items": [
    { "code": "1130510100", "name": "미아동", "lawdCd": "11305", "districtName": "강북구",
      "count": 12, "centerLat": 37.6131, "centerLng": 127.0298 }
  ]
}
```

- `DISTRICT`는 영역 없이 부르고 항상 25행이 온다. `code`는 5자리다.
- `DONG`의 `code`는 10자리이고, 중심이 영역 안에 있는 동만 온다.
- `count: 0`인 묶음도 온다. 회색 "0" 마커로 그린다.
- `count`는 좌표가 확정된 단지 중 필터(가격 범위)를 통과한 수다.
- 정렬: `count` 내림차순, 같으면 이름순. 후보 리스트 패널에 그대로 쓰면 된다.
- 같은 이름의 동이 여러 구에 있을 수 있으므로 React key와 선택 상태는 `name`이 아니라 `code`로 잡는다.
- `centerLat`·`centerLng`는 필터와 무관한 고정값이라 필터를 바꿔도 마커가 움직이지 않는다. DISTRICT는 구청 좌표, DONG은 그 동 전체 단지 좌표의 평균이다.

### 4.4 단지 검색 — `GET /house/complexes/search`

```json
{ "items": [ { "complexId": "…", "name": "래미안OO", "districtName": "성동구", "umdName": "OO동", "buildYear": 2014 } ] }
```

- 공백을 무시한 부분 일치이고, 단지명 가나다순이다. 앞부분 일치를 먼저 올려 주는 관련도 정렬은 없다.
- 페이지와 `total`이 없다. 흔한 이름("래미안")은 `size`만큼만 오므로, 결과가 `size`개 꽉 차면 "더 입력해 좁혀 보세요" 안내를 붙인다.
- 좌표가 없는 단지도 나온다. 검색 결과는 비교 담기용이라 문제없지만, 검색 결과에서 지도로 이동하는 기능을 넣으면 안 된다(좌표는 이 응답에 없다).
- `keyword` 길이 검사는 앞뒤 공백만 뗀 문자열 기준 2자 이상이다.
- `%`, `_` 같은 문자는 글자 그대로 검색되므로 FE가 이스케이프하지 않는다.
- 300ms 디바운스, 2자 미만이면 호출하지 않는다.

### 4.5 정보 카드 — `GET /house/complexes/{complexId}?area=B59`

```json
{ "complexId": "…", "name": "OO아파트", "districtName": "동대문구", "umdName": "OO동",
  "latitude": 37.5789, "longitude": 127.0451, "buildYear": 1999, "households": 380,
  "recentPrice": { "priceManwon": 45000, "windowMonths": 3, "sample": 5 } }
```

- 랭킹에서 열면 좌표가 null일 수 있다(4.7).
- 그 평형 통계가 없어도 200이고 `recentPrice: null`이다.
- 없는 단지는 404 `HOUSE_COMPLEX_NOT_FOUND`이다. 카드를 닫고 토스트를 띄운다. `area` 검증(400)이 404보다 먼저다.

### 4.6 비교표 — `GET /house/compare?items=c1:B59,c2:B84,c9:B59`

```json
{
  "missingIds": ["c9"],
  "columns": [
    {
      "complexId": "c1", "name": "OO아파트", "districtName": "중구", "umdName": "OO동", "area": "B59",
      "recentPrice": { "priceManwon": 145000, "windowMonths": 3, "sample": 4 },
      "households": 449,
      "buildYear": 2022, "ageYears": 4,
      "areaInfo": { "avgExclusiveArea": 59.95, "bucket": "B59" },
      "far": { "pct": 989.67, "source": "RECAP" },
      "schools": [
        { "level": "E", "name": "OO초등학교", "distanceM": 604 },
        { "level": "M", "name": "OO중학교", "distanceM": 812 },
        { "level": "H", "name": "OO고등학교", "distanceM": 1130 }
      ],
      "redevelopments": [
        { "name": "OO재정비촉진구역", "projectType": "도시정비형 재개발", "stage": "관리처분",
          "distanceM": 116, "sourceUrl": null }
      ],
      "change1y": { "fromManwon": 130000, "toManwon": 145000, "pct": 11.54, "changeManwon": 15000,
                    "sampleFrom": 3, "sampleTo": 4, "sampleStatus": "OK" },
      "change3y": { "fromManwon": null, "toManwon": 145000, "pct": null, "changeManwon": null,
                    "sampleFrom": 0, "sampleTo": 4, "sampleStatus": "INSUFFICIENT" }
    }
  ]
}
```

| 행 | 필드 | null일 때 / 표시 규칙 |
| --- | --- | --- |
| 1 최근 실거래가 | `recentPrice` | 3장 규칙 |
| 2 세대수 | `households` | "정보 없음" |
| 3 연식 | `buildYear`, `ageYears` | "2022년 · 4년차". null이면 "정보 없음" |
| 4 평형 | `areaInfo` | "59.95㎡ 평균 · 59 구간". 객체는 항상 오고, 최근 12개월 거래가 없으면 `avgExclusiveArea`만 null → "59 구간"만 표시 |
| 5 용적률 | `far` | "989.67% · 총괄표제부"(`RECAP`) / "· 표제부"(`TITLE`). null이면 "정보 없음" |
| 6 교육·통학 | `schools` | `level` E→초, M→중, H→고. 학교급별 최대 1개, 없는 급은 빠진다. 빈 배열이면 "정보 없음". 하단에 "직선거리 · 학교 배정 보장 아님" 고정 |
| 7 호재·정비사업 | `redevelopments` | **5장** |
| 8·9 변동 | `change1y`, `change3y` | 아래. 그 평형 통계가 아예 없는 단지(해당 구간 거래 이력 없음)는 객체 자체가 null → "정보 없음" |

열은 `complexId`만으로 구분되지 않는다. 같은 단지를 59·84로 담으면 같은 `complexId` 열이 두 개 오므로, React key는 `complexId + area`로 잡는다.

**변동(`change1y`·`change3y`)**

| `sampleStatus` | 조건 | 표시 |
| --- | --- | --- |
| `OK` | `min(sampleFrom, sampleTo) ≥ 3` | "13.00억 → 14.50억 (+11.54%)" + 작은 글씨로 표본 건수 |
| `LOW` | `min(sampleFrom, sampleTo) = 2` | 위와 같음 + "낮은 표본" 배지 |
| `INSUFFICIENT` | `min(sampleFrom, sampleTo) ≤ 1` | 변동률 대신 "표본 부족". `pct`·`changeManwon`은 null. 가격은 건수가 0인 쪽만 null이고 1건인 쪽은 온다 → 온 가격만 표시 |

- 상승은 파랑, 하락은 빨강이다(한국 금융 관례와 반대이므로 범례를 단다).
- `recentPrice.windowMonths === 6`이면 최근 3개월 거래가 없다는 뜻이고, 두 변동은 모두 `INSUFFICIENT`로 온다. 이때 `change.toManwon`은 null이다. 1행의 가격(6개월 평균)과 변동의 "최근" 가격(3개월 평균)은 출처가 다르므로 섞어 쓰지 않는다.

**`missingIds`**

- 열 순서는 요청 순서다. 없는 단지는 열에서 빠지고 `missingIds`에 들어간다(404 아님, 항상 200).
- `missingIds`는 단지 id 기준이고 중복이 없다. FE는 `cmp`에서 그 id의 항목을 평형과 관계없이 모두 빼고 "찾을 수 없는 단지 N개를 비교에서 뺐어요" 토스트를 띄운다.
- 전부 없으면 `columns: []`이다.

### 4.7 랭킹 — `GET /house/rankings?type=RISE&area=B84`

```json
{
  "type": "RISE", "area": "B84",
  "period": { "from": "2025-08-04", "to": "2026-11-03" },
  "items": [
    { "rank": 1, "complexId": "…", "name": "OO아파트", "lawdCd": "11680", "districtName": "강남구",
      "latitude": 37.49, "longitude": 127.06, "buildYear": 2014, "avgExclusiveArea": 84.9,
      "households": 1466, "value": 12.40, "changeManwon": 26000, "sample": 6, "badge": null }
  ]
}
```

| `type` | `value` 의미 | `sample` 의미 | 표시 | `period` | 행 수 |
| --- | --- | --- | --- | --- | --- |
| `LEADER` | 최근 12개월 평균가(만원) | 12개월 거래 건수 | "25.30억 · 최근 12개월 평균" | 최근 12개월 | 항상 25 (구마다 1) |
| `RISE` | 1년 변동률(%) | min(최근 3개월, 1년 전 3개월) 건수 | "+12.40% (+2.60억)" | 1년 전 3개월 시작 ~ 오늘 | 최대 20 |
| `FALL` | 1년 변동률(%) — 음수 | 같음 | "-8.20% (-1.10억)" | 같음 | 최대 20 |
| `VOLUME` | 최근 3개월 거래 건수 | 최근 3개월 건수 | "37건" | 최근 3개월 | 최대 20 |

- `value`는 JSON 숫자이고 소수 둘째 자리까지 붙어 온다(LEADER `253000.00`, VOLUME `37.00`). zod는 `z.number().nullable()`로 받고, 건수·금액은 정수로 바꿔 쓴다.
- `changeManwon`은 모든 type에서 1년 등락가(만원)이고 계산할 수 없으면 null이다. 화면에는 RISE·FALL에서만 쓰면 된다.
- `period`는 nullable이다. 그 type·평형의 랭킹 행이 하나도 없으면 `period: null, items: []`로 온다. 통계가 안 돌았는지, 조건에 맞는 단지가 없는지(상승 단지 0개 등)는 `meta.statsComputedDate`로 구분한다.

- `badge`
  - `LOW_SAMPLE`: 구별 대장인데 12개월 거래가 3건 미만이라 1건 이상 중 1위를 골랐다. "표본 적음" 배지로 표시한다.
  - `NO_TRADE`: 그 구에 12개월 거래가 없다. `rank`·`lawdCd`·`districtName`은 값이 있고 `sample`은 0이다. `complexId`, `name`, 좌표, `buildYear`, `avgExclusiveArea`, `households`, `value`, `changeManwon`은 null이다. 구 이름(`districtName`)과 "최근 12개월 거래 없음"으로 행을 그리고, 버튼은 비활성화한다.
- 좌표 보강에 실패한 단지도 랭킹에 나온다(`latitude`·`longitude` null). `[지도에서 위치 보기]`는 비활성화하고 "위치 정보 없음"을 표시한다.
- 20개 미만이면 있는 만큼만 보여 준다.
- 하단에 집계 기간(`period`)과 "통계 계산일 {statsComputedDate}"를 표기한다.

## 5. 정비사업 표시 (이번 개선)

`redevelopments`는 세 상태를 구분해야 한다.

| 응답 | 의미 | 표시 |
| --- | --- | --- |
| `null` | 정비사업 동기화가 **완전히 성공한 적이 아직 없다**. 첫 CSV 등록 후 좌표를 구하는 중(PENDING이 남음)인 동안도 여기에 해당한다 | "정보 없음" |
| `[]` | 동기화는 됐고, 반경 500m 안에 연결된 구역이 없다 | "직접 연관 사업 없음" |
| `[…]` | 반경 500m 안의 구역들, 가까운 순 | 구역마다 한 줄: "OO재정비촉진구역 · 도시정비형 재개발 · 관리처분 · 약 116m" |

- `projectType`·`stage`는 **표시용 한국어 문자열**로 온다(enum 코드가 아니다). 값의 범위:
  - `projectType`: `주택정비형 재개발`, `도시정비형 재개발`, `공동주택 재건축`, `아파트지구 재건축`, `단독주택 재건축`
  - `stage`(진행 순): `구역지정`, `추진위`, `조합설립`, `건축심의`, `사업시행`, `관리처분`, `착공`
  - 단계별로 색이나 아이콘을 붙이고 싶으면 위 7개 문자열로 매핑한다. 모르는 값은 기본 스타일로 그대로 보여 준다.
- `sourceUrl`은 지금 항상 `null`이다. 링크 UI는 null이면 숨기는 식으로만 만든다.
- 거리 앞에 "약"을 붙인다. 구역 위치를 대표 지번 한 점으로 잡은 직선거리라 구역 경계와는 차이가 있다.
- 한 단지에 여러 구역이 붙을 수 있다(세운 일대 실데이터 기준 최대 4개). 3개를 넘으면 접기를 권장한다.
- 표 하단 출처: "서울시 정비사업 현황(분기 갱신) 기준".
- 정비사업 정보는 비교표에만 나온다. 지도·카드·랭킹 응답에는 없다.
- 한 번 성공한 뒤로는 `null`로 돌아가지 않는다. 이후 CSV를 다시 올려 일부 구역의 좌표가 PENDING이어도, 좌표가 있는 구역 기준으로 `[]` 또는 목록이 온다.

## 6. 화면별 호출 순서 요약

1. **페이지 진입**: `meta`, `budget-policy`를 병렬로 호출한다(staleTime 길게).
2. **지도 `idle`** (300ms 디바운스): 영역을 소수 셋째 자리로 바깥쪽으로 맞춘 뒤(sw 내림, ne 올림) 레이어를 고른다.
   - 단지: level ≤ 5이고 한 변 ≤ 0.2도 → `complexes`
   - 동 묶음: 한 변 ≤ 1.0도 → `summary?groupBy=DONG`
   - 구 묶음: 그 외 → `summary?groupBy=DISTRICT`(영역 없이)
   - BE 검증 한도와 같으니, 넘으면 반드시 한 단계 위로 올린다. 그렇지 않으면 400이 난다.
3. **카카오 키 없음**: `summary?groupBy=DISTRICT` → 구 선택 → `complexes?lawdCd=`.
4. **마커·리스트 클릭**: `complexes/{id}?area=`.
5. **비교 담기·검색**: `cmp` URL 갱신 → `compare?items=`(cmp 순서 유지, 59→B59 변환).
6. **랭킹 탭**: `rankings?type=&area=`.

react-query 키에는 맞춘 영역, `area`, 가격 범위, 레이어를 모두 넣는다.

## 7. 데이터가 바뀌는 시점

- 모든 데이터는 매일 새벽 야간 배치가 갱신한다(실거래 → 단지 보강 → 학교 → 정비사업 → 통계·랭킹). 낮 동안은 응답이 바뀌지 않으므로 staleTime을 길게 둬도 된다.
- 정비사업 CSV를 올려도 비교표는 **다음 날 배치 후**에 반영된다. 업로드 직후에는 구역 좌표가 없어서 연결이 생기지 않는다. 첫 등록이라면 모든 구역의 좌표 시도가 끝날 때까지(구역 500개 기준 하루 이틀) `redevelopments`가 `null`로 온다.

## 8. 관리자 화면 (선택)

관리자 권한 회원만 쓰는 운영 화면이다. 지금은 Postman 등으로 호출해도 되고, 화면을 만든다면 아래 계약을 따른다.

### 8.1 정비사업 CSV 등록 — `POST /house/admin/redevelopments/import`

- 요청: `multipart/form-data`, 필드 이름 `file`. UTF-8 CSV(BOM 있어도 됨).
- 양식: `docs/myseoulhouse/redevelopment-template.csv`. 머리글은 아래 11개 열이다. FE에서 양식 다운로드 버튼을 만든다면 이 한 줄에 BOM(`\uFEFF`)을 붙여 Blob으로 내려 주면 된다.

  ```text
  CODE,순번,자치구,구역명,지번주소,도로명주소,공공/민간,일반/재촉,사업유형,추진단계,좌표검색주소(선택)
  ```

- 운영 절차 안내 문구는 `redevelopment-import.md`를 따른다(엑셀 A5부터 J열 끝까지 복사 → 양식에 붙여넣기 → "CSV UTF-8"로 저장 → 업로드).
- 파일 전체를 통째로 교체한다. 파일에 없는 구역은 삭제된다.

성공(200):

```json
{ "total": 496, "inserted": 0, "updated": 2, "unchanged": 493, "deleted": 1, "skippedRows": 1, "geocodePending": 3 }
```

| 필드 | 의미 |
| --- | --- |
| `total` | 등록된 구역 수 |
| `inserted` / `updated` / `unchanged` / `deleted` | 이전 등록 대비 변경. 띄어쓰기만 다른 값은 같은 값으로 본다 |
| `skippedRows` | 건너뛴 행(빈 행, 중간에 섞인 머리글 행 등 CODE가 숫자가 아닌 행) |
| `geocodePending` | 좌표를 다음 배치에서 구할 구역 수 |

결과 문구 예: "496개 구역 등록 (추가 0 · 변경 2 · 삭제 1). 좌표는 내일 새벽 배치에서 3곳을 찾습니다."

실패(400 `VALIDATION_FAILED`) — 오류가 하나라도 있으면 **아무것도 저장하지 않는다.**

```json
{
  "title": "정비사업 CSV에 오류가 3건 있어 등록하지 않았습니다.",
  "status": 400, "code": "VALIDATION_FAILED",
  "errors": [
    { "field": "line 7",  "code": "DISTRICT_INVALID",     "message": "자치구가 서울 25개 구 이름이 아닙니다. 자치구=강남" },
    { "field": "line 12", "code": "PROJECT_TYPE_INVALID", "message": "사업유형을 알 수 없습니다. 사업유형=재개발" },
    { "field": "line 30", "code": "CODE_DUPLICATED",      "message": "CODE가 파일 안에서 중복되었습니다. CODE=1203" }
  ]
}
```

- `errors`는 최대 50개다. `title`에 전체 건수가 있다.
- 표로 보여 준다: 위치(`field`) · 사유(`message`). `field`는 두 형식이다: 행 오류는 `"line N"`(파일의 N번째 줄), 파일 전체 오류는 `"file"`.
- `code` 목록:
  - 파일 단위: `TOO_LARGE`(1MB 초과), `ENCODING`(UTF-8 아님), `EMPTY`, `CSV_FORMAT`(따옴표 깨짐 등), `HEADER_MISMATCH`
  - 행 단위: `COLUMN_COUNT`, `CODE_DUPLICATED`, `DISTRICT_INVALID`, `NAME_REQUIRED`, `JIBUN_REQUIRED`, `TOO_LONG`, `PROJECT_TYPE_INVALID`, `STAGE_INVALID`
- `HEADER_MISMATCH`면 서울시 원본 양식이 바뀌었을 수 있다. "양식이 맞지 않습니다. 최신 양식을 받아 다시 붙여넣어 주세요"로 안내한다.
- `file` 파트가 없으면 400 `MISSING_PARAMETER`(`errors[0].field = "file"`)이다.

### 8.2 좌표 못 찾은 구역 — `GET /house/admin/redevelopments/incomplete?page=1&size=20`

```json
{
  "totalCount": 2,
  "items": [
    { "code": "1205", "name": "OO재정비촉진구역", "districtName": "종로구",
      "jibunAddress": "종로구 OO동 85", "roadAddress": null, "geocodeQuery": null,
      "geocodeStatus": "FAILED", "geocodeAttempts": 3 }
  ]
}
```

- `code`는 CSV의 CODE 열 값이다.
- `geocodeStatus`: `PENDING`(다음 배치 대기) 또는 `FAILED`(3회 실패). 구 코드·구역명 순으로 정렬된다.
- `page`는 1부터이고 1 미만이면 400이다. `size`는 없거나 0 이하면 20, 100을 넘으면 100으로 맞춘다.
- 철거가 끝난 착공·관리처분 구역은 주소 DB에 지번이 없어 실패하는 경우가 많다.
- 해결 방법: CSV 11번째 열 `좌표검색주소(선택)`에 구역 근처 건물의 도로명주소를 적어 **파일 전체를 다시 업로드**한다. 화면에 이 안내를 함께 둔다.

### 8.3 보강 실패 단지 — 기존 계약 그대로

- `GET /house/admin/complexes/incomplete` 응답: `{ totalCount, items: [{ complexId, aptSeq, name, districtName, umdName, bonbun, bubun, roadAddress, addressOverride, geocodeStatus, registerStatus, trades12m }] }`.
  - 좌표나 건축물대장 중 하나라도 `FAILED`인 단지가 12개월 거래 많은 순으로 온다.
  - `bonbun`·`bubun`은 앞 0을 뗀 값이다(`"0123"` → `"123"`).
  - `page`·`size` 규칙은 8.2와 같다.
- `PUT …/address-override` 본문: `{ roadAddress?, bonbun?, bubun? }`.
  - 셋 중 하나 이상이 필요하다.
  - `bonbun`·`bubun`은 숫자 1~4자리이고, `bubun`만 보낼 수는 없다.
  - **통째로 교체**한다. 보내지 않은 필드는 기존 보정이 해제된다. 수정 폼은 기존 `addressOverride` 값을 채운 상태로 열고 전체를 보낸다.
  - 응답은 목록 항목과 같은 모양(`IncompleteComplexRdo`) 한 건이다.
  - `roadAddress`나 `bonbun`을 보내면 그 단지의 상태가 `PENDING`으로 바뀌어 목록(FAILED만)에서 빠진다. 실제 재조회는 다음 야간 배치다. 거기서도 실패하면 다시 목록에 나타난다.
  - 없는 `complexId`는 404 `HOUSE_COMPLEX_NOT_FOUND`이다.

## 9. FE 테스트 체크리스트 (추가분)

- zod: 모든 nullable 필드(`recentPrice`, `buildYear`, `ageYears`, `households`, `areaInfo.avgExclusiveArea`, `far`, `change1y`·`change3y`, `redevelopments`, 랭킹 좌표, 메타 날짜)가 null이어도 통과하는지
- 비교표 정비사업 3상태: `null` → "정보 없음", `[]` → "직접 연관 사업 없음", 목록 → 거리순 표시 + "약"
- 모르는 `projectType`·`stage` 문자열이 와도 깨지지 않는지
- `sampleStatus` 3종 표시, `INSUFFICIENT`에서 null 가격 처리
- 랭킹 `NO_TRADE` 행, 좌표 null 단지의 버튼 비활성화, `period: null` 빈 상태
- 비교표 같은 단지 59·84 두 열(key 충돌 없음), `missingIds` id의 모든 평형 제거
- `statsComputedDate: null`일 때 지도·랭킹이 "조건 없음"이 아니라 "통계 준비 중"으로 나오는지
- (관리자 화면을 만들면) CSV 400 응답의 `errors[]` 표 렌더링, 성공 응답 문구
