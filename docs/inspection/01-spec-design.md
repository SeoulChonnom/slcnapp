# 임장 기록(Inspection) 기능 설계 제안

> **이 문서는 `docs/field_research/implementation_design.md`로 대체되었다.** 아래 내용은 초기 제안이며 현재 구현 기준이 아니다.
> 특히 `InspectionPlace`(단지)는 `InspectionArea`(지역/생활권)로 의미가 바뀌었고, 단지명은 `ViewedProperty.complexName`으로 내려갔다.
>
> 상태: 제안(Proposal). 구현 전 검토용 문서다.
> 배치 기준은 `docs/module.md`, 파일 연동 규칙은 `docs/file-asset.md`를 따른다.

## 1. 목적

임장을 다녀온 뒤 해당 장소의 위치, 사진, 문답, 감상을 기록하고 **매물 간 비교**까지 지원한다.

- 위치: FE가 외부 지도 API로 검색/핀 확정 → BE는 공급자 중립 좌표로 저장
- 사진: 기존 `FileAsset` + `FileBox` 방식 재사용 + 임장 전용 사진 카테고리
- 문답: 질문 마스터를 버전으로 관리하고, 답변은 기록 시점 스냅샷 + 버전 참조로 고정
- 재방문: 장소(Place)와 방문(Visit)을 분리해 같은 매물의 방문 이력을 쌓는다

## 2. 도메인 이름

`inspection`을 도메인/패키지 이름으로 제안한다. 테이블 prefix는 `inspection_`, URL은 `/inspections`, `/inspection-places`, `/inspection-questions`다.

대안은 `estate-visit`(`EstateVisit`), `viewing`이다. 이름은 패키지·테이블·URL에 모두 박히므로 구현 착수 전에 확정한다.

## 3. 요구사항과 범위 밖

### 저장 대상

| 항목 | 소속 | 비고 |
| --- | --- | --- |
| 장소명, 좌표, 주소 | Place | 매물 마스터 |
| 태그 | Place | `재건축`, `신축`, `관심지역` |
| 즐겨찾기 | Place | 후보 관리 |
| 임장일 | Visit | 같은 장소 재방문 |
| 방문 시간대 | Visit | 낮/밤 환경 차이 |
| 전체 메모 | Visit | 정형 문답 외 자유 기록 |
| 한줄평 | Visit | 목록에서 빠른 회상 |
| 장점 / 단점 | Visit | 비교 화면의 축 |
| 다음 방문 메모 | Visit | 재임장 계획 |
| 문답 | Answer | 질문 버전 참조 + 스냅샷 |
| 사진 | FileBox | 카테고리 + 대표 사진 |

### 범위 밖

- 매물 시세·실거래가 연동
- 임장 기록 외부 공유/공개 링크
- 반경 검색, 지도 위 클러스터링 같은 공간 질의
- 사용자별 즐겨찾기 분리(두 사용자가 같은 데이터를 본다)

## 4. 위치 저장 — 외부 지도 API 검토

### 4.1 지도 API는 세 가지 상품이 섞여 있다

하나의 "지도 API"가 아니라 아래 셋을 각각 고른다. 약관과 과금이 서로 다르다.

| 용도 | 카카오 | 네이버 |
| --- | --- | --- |
| 지도 렌더링 | Kakao Maps JS SDK | NCP Maps - Web Dynamic Map |
| 장소(POI) 검색 | Local REST API(키워드/카테고리) | 검색 API(지역) — Maps 상품이 아닌 별도 오픈 API |
| 주소↔좌표 | Local REST API(주소 검색) | NCP Maps - Geocoding / Reverse Geocoding |

확인된 사실:

- 구 AI·NAVER API의 지도 API는 **2026-06-25 제공 종료**되었고, 신규는 NCP `Maps` 상품(VPC 환경, 유료)으로 간다. 무료 제공량도 중단된 상태다.
- NCP `Maps`에는 POI 검색이 없다. 상호명으로 찾으려면 네이버 검색 API(지역)를 따로 붙여야 하고, 이쪽은 결과 건수가 적고(표시 최대 5건) `mapx`/`mapy`가 정수라 **10^7로 나눠야 WGS84 경위도**가 된다.
- 카카오 Local API는 `x`가 경도, `y`가 위도이며 문자열로 내려온다. **순서가 흔히 쓰는 (lat, lng)와 반대**다.

### 4.2 저장 관련 약관 제약 (중요)

카카오 데브톡의 공식 답변 기준으로, 로컬 API 응답은 **실시간 호출이 원칙이고 서버 저장이 금지**된다. 저장이 허용되는 항목은 사용자가 확정한 **장소명 / 장소 ID / `place_url`** 정도이고, **좌표·주소·전화번호·카테고리는 저장 대상이 아니다.**

즉 "검색 결과를 그대로 DB에 넣는" 가장 쉬운 구현이 약관상 가장 위험하다. 최종 해석은 카카오 측 확인이 필요하지만, 설계는 이 제약을 전제로 잡는 편이 안전하다.

### 4.3 저장 모델 3안

**A안 — 공급자 응답 그대로 저장**
구현이 가장 짧다. 카카오 기준 약관 위반 소지가 있고, 공급자를 바꾸면 저장 데이터가 통째로 의미를 잃는다.

**B안 — 최소 참조 + 사용자 확정 좌표**
`provider` + `providerPlaceId` + `placeUrl`만 공급자 데이터로 두고, 좌표와 장소명은 **사용자가 지도에서 확정한 값**으로 취급해 저장한다. 상세 정보가 필요하면 FE가 그때 실시간 호출한다.

**C안 — 주소/좌표는 공공 API에서 받는다**
도로명주소 API(행정안전부) 또는 VWorld 지오코더(국토교통부)로 주소와 좌표를 얻어 저장하고, 지도 렌더링만 카카오/네이버 SDK로 한다. 저장 제약이 없어 약관상 가장 깨끗하고 비용도 들지 않는다. 상호명 검색은 약하지만, 임장 대상은 아파트·빌라·건물이라 **주소 기반 검색이 오히려 더 잘 맞는다.**

### 4.4 제안

**C안을 기본으로 하고 B안의 참조 필드를 선택 항목으로 남긴다.**

- 검색: 도로명주소 API 또는 VWorld 지오코더 → 주소 + WGS84 좌표 확보
- 확정: 사용자가 지도에서 핀 위치를 조정할 수 있게 하고, **최종 저장 좌표는 사용자가 확정한 핀**으로 본다
- 렌더링: 카카오 Maps JS SDK 권장(무료, 도메인 등록만 필요). 네이버는 유료 전환이 끝났으므로 커플 2인 서비스에는 과하다
- 공급자 참조(`providerPlaceId`, `providerPlaceUrl`)는 nullable로 두고, 딥링크가 필요할 때만 채운다

**BE는 지도 API를 호출하지 않는다.** 서버가 공급자에 묶이지 않으므로 FE가 공급자를 바꿔도 계약이 그대로 유지된다.

### 4.5 좌표 저장 규칙

- 좌표계는 **WGS84 십진도(EPSG:4326)** 하나로 통일한다. 다른 좌표계는 받지 않는다.
- 소수점 6자리면 약 11cm 해상도로 충분하다. 컬럼은 `numeric(10,7)`, 도메인 타입은 `Double`을 쓴다.
- 입력 검증: 위도 `-90 ~ 90`, 경도 `-180 ~ 180`. 둘 중 하나만 오면 `400`.
- 공급자 값 변환은 **FE 책임**이다. 카카오 `x`/`y` 뒤바뀜, 네이버 `mapx`/`mapy` 10^7 나눗셈은 서버로 넘어오기 전에 끝낸다.
- PostGIS는 도입하지 않는다. 반경 검색 요구가 생기면 그때 `earthdistance` 또는 PostGIS를 검토한다.

## 5. 도메인 구조

세 축으로 나눈다.

```text
InspectionPlace (매물/장소)          InspectionQuestion (질문 축)
  - 이름, 좌표, 주소                    - answerType, required, enabled
  - 태그, 즐겨찾기                        |
      |                                  +-- InspectionQuestionVersion (문구 이력)
      | 1:N                              |         - versionNo, content, choices
      v                                  |
Inspection (방문 1회)                    |
  - 임장일, 시간대, 한줄평               |
  - 장단점, 메모, 다음 방문 메모         |
      | 1:N                              |
      v                                  |
InspectionAnswer  ---------------------- +
  - 질문/버전 참조 + 문구 스냅샷 + 타입별 값
      |
      +-- FileBox(ownerType=INSPECTION) : 사진 + 카테고리
```

- `InspectionPlace`와 `Inspection`은 각각 aggregate root다.
- `InspectionAnswer`는 `Inspection`에 종속되지만 **별도 테이블**이다(비교/통계를 질의로 풀기 위해).
- `InspectionQuestion`과 `InspectionQuestionVersion`은 기록과 독립된 생명주기를 가진다.

## 6. 도메인 모델

### 6.1 InspectionPlace

```java
public class InspectionPlace extends DomainEntity {
	private String name;               // 필수. 사용자가 확정한 장소명
	private Double latitude;           // 필수. WGS84
	private Double longitude;          // 필수. WGS84
	private String roadAddress;        // 선택. 도로명주소
	private String detailAddress;      // 선택. 동/호수 등 사용자 입력
	private MapProvider provider;      // 좌표 출처. 기본 MANUAL
	private String providerPlaceId;    // 선택
	private String providerPlaceUrl;   // 선택
	private List<String> tags;         // 재건축, 신축, 관심지역 ...
	private boolean favorite;          // 즐겨찾기(후보 관리)
	private boolean hidden;
}
```

- ID는 `INSPECTION_PLACE-{16진수 4자리}`.
- **같은 매물을 두 번 임장하면 Place 1건 + Visit 2건**이다. 이게 Place/Visit 분리의 목적이다.
- 즐겨찾기와 태그는 Place에 둔다. "이 매물이 후보다", "이 매물은 재건축 대상이다"는 방문 회차가 아니라 매물의 속성이다.
- `visitCount`, `lastVisitedDate`는 저장하지 않고 조회 시 계산한다. 방문 등록/삭제와 동기화가 어긋날 여지를 만들지 않는다.

### 6.2 Inspection (방문 1회)

```java
public class Inspection extends DomainEntity {
	private String placeId;              // InspectionPlace.id
	private LocalDate visitedDate;       // 임장일
	private VisitTimeSlot visitedTimeSlot;  // 방문 시간대
	private LocalTime visitedTime;       // 선택. 정확한 시각
	private String oneLineReview;        // 한줄평
	private String memo;                 // 전체 메모
	private List<String> goodPoints;     // 장점
	private List<String> badPoints;      // 단점
	private String nextVisitMemo;        // 다음 방문 메모
	private boolean hidden;
}
```

- ID는 `INSPECTION-{16진수 4자리}`.
- 장단점을 단일 문자열이 아니라 목록으로 둔다. 비교 화면에서 항목 단위로 나란히 놓기 위해서다. (`TravelReview`는 단일 문자열이지만, 그쪽은 비교 요구가 없다.)
- 같은 `placeId` + 같은 `visitedDate` + 같은 `visitedTimeSlot` 조합은 중복 등록을 막는다(`409`). 실수로 두 번 저장하는 걸 거른다.

```java
public enum VisitTimeSlot { MORNING, AFTERNOON, EVENING, NIGHT }
```

낮/밤 차이가 목적이므로 4구간이면 충분하다. 정확한 시각이 필요하면 `visitedTime`을 선택으로 함께 받는다.

### 6.3 InspectionQuestion / InspectionQuestionVersion

```java
public class InspectionQuestion extends DomainEntity {
	private InspectionAnswerType answerType;  // 생성 후 변경 불가
	private boolean required;
	private int sortOrder;
	private boolean enabled;                  // 수집 여부
	private String currentVersionId;
	private int currentVersionNo;
}

public class InspectionQuestionVersion extends DomainEntity {
	private String questionId;
	private int versionNo;                    // 1부터
	private String content;                   // 질문 문구
	private String description;               // 도움말
	private List<QuestionChoice> choices;     // CHOICE 계열에서만 사용
	private String unit;                      // NUMBER에서만 사용. 예: 만원, m2
}

public class QuestionChoice implements JsonSerializable {
	private String code;   // 불변 식별자
	private String label;  // 표시 문구
}
```

**버전이 올라가는 변경과 올라가지 않는 변경을 구분한다.**

| 변경 | 결과 |
| --- | --- |
| `content`, `description` 수정 | 새 버전 생성(`versionNo + 1`) |
| `choices` 추가·수정·삭제 | 새 버전 생성 |
| `unit` 변경 | 새 버전 생성 |
| `required`, `sortOrder` 변경 | 버전 그대로. 수집 정책이지 질문의 의미가 아니다 |
| `enabled` 토글 | 버전 그대로 |
| `answerType` 변경 | **금지(`400`).** 기존 질문을 미사용 처리하고 새 질문을 만든다 |

`answerType` 변경을 막는 이유는 6.4의 타입별 컬럼 때문이다. `TEXT`로 쌓인 답변과 `SCORE`로 쌓인 답변을 한 질문 아래에서 집계할 방법이 없다.

질문 자체의 물리 삭제 API는 만들지 않는다.

### 6.4 InspectionAnswer

```java
public class InspectionAnswer extends DomainEntity {
	private String inspectionId;
	private String questionId;                // 통계/비교의 축
	private String questionVersionId;         // 답변 시점 버전
	private int questionVersionNo;
	private String questionSnapshot;          // 답변 시점 질문 문구
	private InspectionAnswerType answerType;  // 스냅샷
	private int sortOrder;                    // 답변 시점 노출 순서

	private String textValue;                 // TEXT
	private Integer scoreValue;               // SCORE (1~5)
	private Boolean booleanValue;             // BOOLEAN
	private BigDecimal numberValue;           // NUMBER
	private List<QuestionChoice> choiceValues;// SINGLE_CHOICE / MULTI_CHOICE 스냅샷
}
```

- ID는 **UUID**를 쓴다. `id_sequence`는 상한이 `0xFFFF`(65,535)라 `기록 수 x 질문 수`로 늘어나는 답변에는 맞지 않는다.
- `questionId`는 "어느 축의 답인가", `questionVersionNo`는 "어느 문구에 답했는가", `questionSnapshot`은 "조인 없이 그대로 그린다"를 각각 책임진다. 셋 다 필요하다.
- 값은 타입별 컬럼으로 나눈다. 문자열 하나에 몰아넣으면 "채광 점수 4점 이상" 같은 질의가 불가능하다.

```java
public enum InspectionAnswerType {
	TEXT,           // 자유 서술
	SCORE,          // 1~5 정수
	BOOLEAN,        // 예/아니오
	NUMBER,         // 수치(관리비, 전용면적 등). unit과 함께 쓴다
	SINGLE_CHOICE,  // 단일 선택
	MULTI_CHOICE    // 복수 선택
}
```

비교/통계가 목적이므로 `SCORE`, `BOOLEAN`, `NUMBER`, `CHOICE`가 핵심이다. `TEXT`는 비교 화면에서 나란히 보여주기만 한다.

### 6.5 enum 정리

```java
public enum MapProvider { KAKAO, NAVER, ADDRESS_API, MANUAL }
public enum VisitTimeSlot { MORNING, AFTERNOON, EVENING, NIGHT }
public enum InspectionAnswerType { TEXT, SCORE, BOOLEAN, NUMBER, SINGLE_CHOICE, MULTI_CHOICE }
public enum InspectionPhotoCategory {
	EXTERIOR, ENTRANCE, LIVING_ROOM, KITCHEN, ROOM, BATHROOM,
	BALCONY, VIEW, SUNLIGHT, PARKING, NEIGHBORHOOD, DOCUMENT, ETC
}
```

## 7. 질문을 `enabled`로만 관리하면 생기는 문제

`enabled`는 **"지금 수집할 질문인가"** 하나만 답한다. 아래는 그 플래그로 답할 수 없는 것들이다.

### 7.1 문구 수정이 과거 답변의 의미를 바꾼다

"채광은 어떤가요?"를 "채광과 조망은 어떤가요?"로 고치면 `UPDATE` 한 번으로 끝난다. 그런데 작년 기록은 조망을 고려하지 않고 쓴 답변이다. **답변은 그대로인데 질문만 바뀌어, 읽는 사람이 잘못 해석한다.** `enabled`는 이걸 막지 못한다. 표시 여부만 제어하기 때문이다.

> 답변에 문구 스냅샷만 넣어도 이 문제 자체는 막힌다. 다만 아래 항목들은 스냅샷만으로는 풀리지 않는다.

### 7.2 오타 수정과 의미 변경을 구분할 수 없다

둘 다 같은 `UPDATE`다. 이력이 없으니 "이 질문이 언제 어떻게 바뀌었나"를 되짚을 수 없고, 비교 화면에서 서로 다른 문구의 답변을 묶어도 되는지 판단할 근거가 없다.

### 7.3 미사용 → 재사용 토글이 서로 다른 질문을 한 축에 섞는다

`enabled=false`로 내려둔 질문을 나중에 문구만 바꿔 다시 켜면, 같은 `questionId` 아래에 **의미가 다른 답변이 연속해서 쌓인다.** 렌더링은 스냅샷 덕에 멀쩡해 보이지만 집계는 조용히 오염된다. 비교가 핵심 기능이 되는 순간 이게 가장 위험하다.

### 7.4 "질문 교체"의 연속성이 끊긴다

의미를 크게 바꾸려면 기존 질문을 끄고 새 질문을 만들어야 한다. 그러면 `questionId`가 달라져 **같은 축의 시계열이 두 동강 난다.** 반대로 버전이 있으면 "같은 축, 문구만 바뀜"과 "다른 축"을 설계자가 의도적으로 고를 수 있다.

### 7.5 타입·선택지 변경을 막을 근거가 없다

`TEXT`로 받던 질문을 `SCORE`로 바꾸면 과거 답변을 어떻게 읽을지 정의되지 않는다. 선택지를 지우면 그 선택지를 고른 과거 답변이 고아가 된다. 버전 개념이 없으면 이런 변경을 "허용/금지"로 판정할 기준 자체가 없다.

### 7.6 무해한 변경까지 같은 무게로 취급된다

`sortOrder`나 `required`처럼 과거 데이터에 아무 영향이 없는 변경도, 이력이 없으면 "질문이 바뀌었다"는 사실만 남아 불필요하게 의심하게 된다.

### 7.7 결론

`enabled`는 **수집 여부**, 버전은 **문구 이력**, 스냅샷은 **표시 안정성**으로 역할을 나눈다. 셋 다 유지하고, `enabled`는 그대로 쓰되 단독으로 쓰지 않는다.

| 관심사 | 담당 |
| --- | --- |
| 지금 이 질문을 물어볼 것인가 | `InspectionQuestion.enabled` |
| 이 질문은 어떻게 바뀌어 왔나 | `InspectionQuestionVersion` |
| 과거 기록 화면이 그때 그대로인가 | `InspectionAnswer.questionSnapshot` |
| 여러 기록을 같은 축으로 비교 가능한가 | `InspectionAnswer.questionId` + `questionVersionNo` |

## 8. 등록·수정 정책

### 장소 등록

- `POST /inspection-places`로 먼저 만들거나, 방문 등록 요청에 `place` 객체를 인라인으로 실어 함께 생성한다(`placeId`가 없을 때).
- 좌표가 같고 이름이 같은 Place가 이미 있으면 **경고 없이 새로 만들지 않는다.** 응답에 기존 후보를 담아 `409`로 돌려주고, FE가 "기존 장소에 방문 추가"를 선택하게 한다.
- 방문 기록이 하나라도 있는 Place는 삭제할 수 없다(`409`). 숨김(`hidden`)만 허용한다.

### 방문 등록 (`POST /inspections`)

1. `placeId`가 있으면 존재 검증, 없으면 `place`로 Place를 새로 만든다. 둘 다 없으면 `400`.
2. `enabled=true`인 질문 전체와 각 질문의 현재 버전을 조회한다.
3. `required=true` 질문 중 답변이 없으면 `400`. 응답 메시지에 누락된 `questionId` 목록을 담는다.
4. 요청의 `questionId`가 없거나 `enabled=false`면 `400`.
5. `answerType`별 값 검증
   - `TEXT`: 공백 불가
   - `SCORE`: 1~5 정수
   - `BOOLEAN`: null 불가
   - `NUMBER`: 숫자, 음수 허용 여부는 질문별로 두지 않고 일괄 허용
   - `SINGLE_CHOICE`: 현재 버전의 `choices` 안에 있는 `code` 1개
   - `MULTI_CHOICE`: 현재 버전의 `choices` 안에 있는 `code` 1개 이상
6. 통과한 답변에 `questionVersionId`, `questionVersionNo`, `questionSnapshot`, `answerType`, `sortOrder`, 선택지 라벨을 스냅샷으로 복사해 저장한다.

활성 질문이 0개면 등록을 막는다(`400`). "문답을 저장해야만 등록된다"는 규칙이 우회되지 않게 한다.

### 방문 수정 (`PUT /inspections/{inspectionId}`)

- 답변은 **기존 답변 집합만** 대상이다. 요청의 `questionId`는 기존 답변에 있어야 하고, 바뀌는 값은 타입별 값뿐이다.
- 그 사이 추가된 질문이나 올라간 버전은 과거 기록에 끼워 넣지 않는다. 스냅샷과 버전 참조도 그대로 둔다.
- `answers`를 생략하면 기존 답변을 유지한다.
- 장단점·메모·사진은 요청 기준으로 덮어쓴다(`files`는 여행과 동일하게 "보내면 그 목록이 최종 상태, 생략하면 유지").

### 삭제 (`DELETE /inspections/{inspectionId}`)

방문 기록, 그 답변, `FileBox` 문서를 지운다. Place는 남는다. 실제 파일과 `file_asset` 문서도 남긴다(기존 정책 동일).

## 9. 사진 저장과 카테고리

업로드 절차는 기존과 같다. FE가 `POST /api/assets/file`로 올리고 응답의 `fileId`를 `files` 배열에 담아 보낸다.

공통 enum에 값을 추가한다.

- `FileType`에 `INSPECTION("inspection")`
- `FileConstant.AVAILABLE_PATH`에 `inspection` 추가 — **빼먹으면 업로드가 `FilePathInvalidException`으로 떨어진다.**
- `FileBoxOwnerType`에 `INSPECTION`
- `FileBoxTargetType`에 `INSPECTION`

### 카테고리를 어디에 둘 것인가

`FileBoxItem`에는 `role`(COVER/GALLERY)과 `caption`은 있어도 카테고리 개념이 없다. 세 가지 선택지가 있다.

| 안 | 방식 | 평가 |
| --- | --- | --- |
| A | `FileBoxItemRole`에 `LIVING_ROOM` 등을 추가 | 공유 enum이 도메인 전용 값으로 오염된다. `role`은 "대표/일반"이라는 다른 축이다 |
| B | `targetId`에 카테고리 코드를 넣는다 | 스키마 변경이 없지만 `targetId`의 의미(대상 식별자)가 깨진다 |
| C | `FileBoxItem`에 nullable `category` 필드 추가 | **권장.** 축이 분리되고, MongoDB 문서라 마이그레이션이 필요 없다 |

**C안을 제안한다.** 영향 범위는 `FileBoxItem`, `FileBoxItemCdo/Udo/Rdo`, `FileBoxMapper`, `FileBoxDoc`, `FileBoxDocMapper`와 각 테스트다. 기존 문서에는 `category`가 없으므로 null로 읽히고, Travel/Trip은 검증에서 이 필드를 무시한다.

### 규칙

- `targetType`은 `INSPECTION` 하나이고 `targetId`는 비운다.
- `role`은 `COVER`(0 또는 1건)와 `GALLERY`(N건)만 허용한다.
- `category`는 `InspectionPhotoCategory` 값이어야 하고, 생략하면 `ETC`로 저장한다.
- 파일 타입은 `inspection`이어야 한다. 다른 타입이면 `400`.
- 같은 `role` + `fileAssetId` 조합은 중복될 수 없다.
- `sortOrder`가 0 이하이면 카테고리 그룹별로 자동 채번한다.
- 목록 응답의 대표 이미지는 `COVER` → 없으면 `sortOrder`가 가장 앞선 `GALLERY` → 둘 다 없으면 `null`이다. 사진 없이도 기록을 남길 수 있게 `COVER`를 필수로 두지 않는다(여행과 다른 점).
- 상세 응답의 `photos`는 카테고리별로 묶어서 내려준다.

## 10. 임장 비교

`GET /inspections/compare?inspectionIds=INSPECTION-0001,INSPECTION-0004`

- 최소 2건, 최대 4건. 벗어나면 `400`.
- 응답은 **행이 질문, 열이 기록**인 행렬이다.
- 행 매칭 기준은 `questionId`다. 버전이 달라도 같은 행으로 묶고, 헤더는 최신 버전 문구를, 각 셀은 그 답변이 쓰인 `questionVersionNo`를 함께 담는다. 버전이 섞이면 행에 `mixedVersion: true`를 표시해 FE가 안내할 수 있게 한다.
- 어떤 기록에만 있는 질문은 나머지 열을 `null`로 채운다.
- 문답 외에 `한줄평`, `장점`, `단점`, `방문 시간대`, `임장일`, `태그`, `즐겨찾기`도 같은 행렬에 포함한다.
- `SCORE`/`NUMBER` 행은 최대·최소값에 `best`, `worst` 플래그를 붙인다. 정렬 판단을 FE가 다시 하지 않게 한다.

비교는 조회 전용 조합이므로 `InspectionCompareQueryFlow`에 둔다.

## 11. API 계약

### 장소

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-places` | 목록. `?favorite=true`, `?tag=재건축` 필터 |
| GET | `/inspection-places/{placeId}` | 상세 + 방문 이력 요약 |
| POST | `/inspection-places` | 등록 |
| PUT | `/inspection-places/{placeId}` | 수정 |
| PATCH | `/inspection-places/{placeId}/favorite` | 즐겨찾기 토글 |
| DELETE | `/inspection-places/{placeId}` | 삭제. 방문 기록이 있으면 `409` |

### 방문 기록

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspections` | 목록. `visitedDate` 내림차순. `?placeId=` 필터 |
| GET | `/inspections/{inspectionId}` | 상세 |
| GET | `/inspections/compare` | 비교 |
| POST | `/inspections` | 등록 |
| PUT | `/inspections/{inspectionId}` | 전체 수정 |
| DELETE | `/inspections/{inspectionId}` | 삭제 |

### 문답 질문

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-questions` | 기본은 `enabled=true`만. `?includeDisabled=true`로 전체 |
| GET | `/inspection-questions/{questionId}/versions` | 버전 이력 |
| POST | `/inspection-questions` | 등록. `answerType` 확정 |
| PUT | `/inspection-questions/{questionId}` | 문구/선택지 수정 → 새 버전 |
| PATCH | `/inspection-questions/{questionId}/policy` | `required`, `sortOrder` 변경. 버전 유지 |
| PATCH | `/inspection-questions/{questionId}/status` | 미사용/재사용 전환 |

권한은 기본 정책(`anyRequest().hasAuthority("USER")`)을 따른다. 사용자가 둘뿐이라 질문 관리도 사용자가 직접 하는 편이 자연스럽다. 관리자 전용으로 바꾸려면 `SecurityConfiguration`에 `/inspection-questions` 쓰기 경로 matcher를 추가한다.

### 등록 요청 예시

```json
{
  "placeId": null,
  "place": {
    "name": "래미안 e편한세상",
    "latitude": 37.5065,
    "longitude": 127.0536,
    "roadAddress": "서울 강남구 테헤란로 123",
    "detailAddress": "103동 1502호",
    "provider": "ADDRESS_API",
    "tags": ["신축", "관심지역"],
    "favorite": true
  },
  "visitedDate": "2026-09-14",
  "visitedTimeSlot": "EVENING",
  "visitedTime": "19:30",
  "oneLineReview": "밤에도 단지 조명이 밝고 조용함",
  "memo": "역에서 도보 8분. 관리사무소 응대 좋음",
  "goodPoints": ["남향 채광", "관리 상태 양호"],
  "badPoints": ["주차 공간 부족", "방음 약함"],
  "nextVisitMemo": "낮에 다시 와서 채광 확인, 주차장 혼잡도 보기",
  "answers": [
    { "questionId": "INSPECTION_QUESTION-0001", "textValue": "남향. 오후 3시에도 거실까지 들어옴" },
    { "questionId": "INSPECTION_QUESTION-0002", "scoreValue": 4 },
    { "questionId": "INSPECTION_QUESTION-0003", "booleanValue": true },
    { "questionId": "INSPECTION_QUESTION-0004", "numberValue": 18.5 },
    { "questionId": "INSPECTION_QUESTION-0005", "choiceCodes": ["SOUTH"] }
  ],
  "files": [
    { "fileAssetId": "file-1", "targetType": "INSPECTION", "role": "COVER", "category": "EXTERIOR" },
    { "fileAssetId": "file-2", "targetType": "INSPECTION", "role": "GALLERY", "category": "LIVING_ROOM", "caption": "거실 채광" }
  ]
}
```

### 상세 응답 예시 (요약)

```json
{
  "inspectionId": "INSPECTION-0001",
  "place": {
    "placeId": "INSPECTION_PLACE-0001",
    "name": "래미안 e편한세상",
    "latitude": 37.5065,
    "longitude": 127.0536,
    "roadAddress": "서울 강남구 테헤란로 123",
    "tags": ["신축", "관심지역"],
    "favorite": true,
    "visitCount": 2,
    "lastVisitedDate": "2026-09-14"
  },
  "visitedDate": "2026-09-14",
  "visitedTimeSlot": "EVENING",
  "oneLineReview": "밤에도 단지 조명이 밝고 조용함",
  "goodPoints": ["남향 채광", "관리 상태 양호"],
  "badPoints": ["주차 공간 부족", "방음 약함"],
  "nextVisitMemo": "낮에 다시 와서 채광 확인",
  "answers": [
    {
      "answerId": "0f2c...",
      "questionId": "INSPECTION_QUESTION-0001",
      "questionVersionNo": 2,
      "question": "채광은 어떤가요?",
      "answerType": "TEXT",
      "sortOrder": 1,
      "textValue": "남향. 오후 3시에도 거실까지 들어옴"
    },
    {
      "answerId": "8a71...",
      "questionId": "INSPECTION_QUESTION-0002",
      "questionVersionNo": 1,
      "question": "소음 수준은?",
      "answerType": "SCORE",
      "sortOrder": 2,
      "scoreValue": 4
    }
  ],
  "cover": { "fileAssetId": "file-1", "category": "EXTERIOR" },
  "photosByCategory": {
    "EXTERIOR": [ { "id": "item-1", "fileAssetId": "file-1", "role": "COVER" } ],
    "LIVING_ROOM": [ { "id": "item-2", "fileAssetId": "file-2", "caption": "거실 채광" } ]
  }
}
```

`answers[].question`은 스냅샷이므로 질문 마스터가 바뀌어도 이 응답은 그대로다. `questionVersionNo`가 함께 있어 "이 답변은 2번 문구에 대한 답"임을 알 수 있다.

## 12. 모듈 배치

```text
slcn-spec/.../spec/inspection/
├── constant/InspectionConstant.java
├── entity/{Inspection,InspectionPlace,InspectionQuestion,
│           InspectionQuestionVersion,InspectionAnswer}.java
├── entity/vo/{QuestionChoice,MapProvider,VisitTimeSlot,
│              InspectionAnswerType,InspectionPhotoCategory}.java
├── facade/{InspectionFacade,InspectionPlaceFacade,InspectionQuestionFacade}.java
├── facade/sdo/...(Cdo/Udo/Rdo)
└── mapper/{InspectionMapper,InspectionPlaceMapper,InspectionQuestionMapper}.java

slcn-aggregate/.../aggregate/inspection/
├── exception/{InspectionNotFoundException,InspectionPlaceNotFoundException,
│              InspectionPlaceInUseException,InspectionQuestionNotFoundException,
│              InspectionAnswerRequiredException,InvalidInspectionAnswerException,
│              InvalidInspectionPlaceException,DuplicateInspectionVisitException}.java
├── logic/{InspectionLogic,InspectionPlaceLogic,InspectionQuestionLogic,
│          InspectionAnswerLogic}.java
└── store/
    ├── {InspectionStore,InspectionPlaceStore,InspectionQuestionStore,
    │     InspectionQuestionVersionStore,InspectionAnswerStore}.java
    ├── jpo/{InspectionJpo,InspectionPlaceJpo,InspectionQuestionJpo,
    │         InspectionQuestionVersionJpo,InspectionAnswerJpo}.java
    ├── jpo/converter/QuestionChoiceListConverter.java
    ├── mapper/*JpoMapper.java
    └── repository/*Repository.java

slcn-aggregate/.../aggregate/flow/inspection/
├── InspectionFlow.java              # 등록/수정: Place + Visit + Answer + FileBox
└── InspectionCompareQueryFlow.java  # 비교 조회 조합

slcn-rest/.../rest/inspection/
├── InspectionResource.java
├── InspectionPlaceResource.java
└── InspectionQuestionResource.java
```

### `StringListConverter`를 공통으로 올린다

`StringListConverter`는 내용은 도메인 중립인데 지금 `aggregate.travel.store.jpo.converter`에 있다. 태그와 장단점에 그대로 필요하므로, 임장 JPO가 travel 패키지를 import하게 두지 말고 **`aggregate/common/store/converter/`로 옮기고 travel이 새 위치를 참조**하게 한다. 이동 자체는 컴파일 단위 변경이라 동작에 영향이 없지만, `TravelJpo`와 기존 테스트를 함께 고쳐야 한다.

### Flow를 도입하는 이유

등록 한 번이 Place 생성/조회 + Visit 저장 + 활성 질문·버전 조회 + 답변 검증·스냅샷 + FileBox 동기화를 묶는다. `docs/module.md` 기준으로 이런 복합 커맨드는 `Flow`가 맡는다.

`TravelLogic`은 이미 492줄로 전역 규칙(파일 800줄 / 함수 50줄)에 근접해 있다. 같은 모양을 반복하지 말고 신규 도메인은 처음부터 `Flow`로 시작한다. 단순 조회는 `Resource -> Logic` 직접 호출을 유지한다.

## 13. 영속성

### `slcn.inspection_place`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | `INSPECTION_PLACE-0001` |
| `name` | varchar | not null |
| `latitude` / `longitude` | numeric(10,7) | not null |
| `road_address` / `detail_address` | varchar | |
| `map_provider` | varchar | `@Enumerated(EnumType.STRING)` |
| `provider_place_id` / `provider_place_url` | varchar | |
| `tags` | text | JSON. `StringListConverter`(공통으로 이동) |
| `favorite` | boolean | |
| `hidden` | boolean | |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_place_hidden_favorite (hidden, favorite)`

### `slcn.inspection`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | `INSPECTION-0001` |
| `place_id` | varchar | not null |
| `visited_date` | date | not null |
| `visited_time_slot` | varchar | `@Enumerated(EnumType.STRING)` |
| `visited_time` | time | nullable |
| `one_line_review` | varchar | |
| `memo` | text | |
| `good_points` / `bad_points` | text | JSON. `StringListConverter` |
| `next_visit_memo` | text | |
| `hidden` | boolean | |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_place_visited (place_id, visited_date)`, `idx_inspection_hidden_visited (hidden, visited_date)`

### `slcn.inspection_answer`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | UUID |
| `inspection_id` | varchar | not null |
| `question_id` | varchar | not null |
| `question_version_id` | varchar | not null |
| `question_version_no` | int | |
| `question_snapshot` | text | not null |
| `answer_type` | varchar | `@Enumerated(EnumType.STRING)` |
| `sort_order` | int | |
| `text_value` | text | |
| `score_value` | int | |
| `boolean_value` | boolean | |
| `number_value` | numeric(15,4) | |
| `choice_values` | text | JSON. `QuestionChoiceListConverter` |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_answer_inspection (inspection_id, sort_order)`, `idx_inspection_answer_question (question_id)`

### `slcn.inspection_question`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | `INSPECTION_QUESTION-0001` |
| `answer_type` | varchar | 생성 후 변경 불가 |
| `required` | boolean | |
| `sort_order` | int | |
| `enabled` | boolean | 미사용 처리 플래그 |
| `current_version_id` | varchar | |
| `current_version_no` | int | |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_question_enabled_sort (enabled, sort_order)`

### `slcn.inspection_question_version`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | UUID |
| `question_id` | varchar | not null |
| `version_no` | int | not null |
| `content` | varchar | not null |
| `description` | varchar | |
| `choices` | text | JSON |
| `unit` | varchar | |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_question_version (question_id, version_no)` — 유니크

### 왜 답변만 별도 테이블인가

`Travel`은 `TravelDay`를 JSON 컬럼에 넣는다. 하지만 답변은 **질문 단위 질의가 기능 요구사항**이다(비교, "채광 4점 이상만", 질문별 추이). JSON TEXT에 넣으면 이 질의가 전부 애플리케이션 메모리 필터가 되고, 질문 축 인덱스를 만들 수 없다.

반대로 장단점·태그처럼 항상 부모와 함께 읽고 끝나는 값은 JSON 컬럼으로 둔다. 기준은 **"이 값만 따로 질의할 일이 있는가"** 다.

### ddl-auto 주의

`ddl-auto=update`라 신규 테이블 5개는 자동 생성된다. 기존 테이블을 건드리지 않아 위험은 낮지만 두 가지를 지킨다.

- enum 컬럼에 `@Enumerated(EnumType.STRING)`을 반드시 명시한다. 빠뜨리면 ordinal 정수로 굳는다.
- 유니크 인덱스(`inspection_question_version`)는 `ddl-auto`가 만들어주지 않는 경우가 있으므로 생성 여부를 실제로 확인한다.

## 14. 공통 코드 변경 체크리스트

- [ ] `SequenceName`에 `INSPECTION`, `INSPECTION_PLACE`, `INSPECTION_QUESTION` 추가
- [ ] **`slcn.id_sequence`에 시드 행 INSERT** — 행이 없으면 등록이 `ID NOT EXIST`로 실패한다
  ```sql
  INSERT INTO slcn.id_sequence (name, last_id) VALUES ('INSPECTION', '0000');
  INSERT INTO slcn.id_sequence (name, last_id) VALUES ('INSPECTION_PLACE', '0000');
  INSERT INTO slcn.id_sequence (name, last_id) VALUES ('INSPECTION_QUESTION', '0000');
  ```
  (`IdSequence`는 `name`, `last_id` 두 컬럼이다)
- [ ] 답변과 질문 버전 ID는 **UUID**로 생성한다. `id_sequence`는 `0xFFFF`(65,535) 상한이라 답변 수를 감당하지 못한다
- [ ] `FileType`에 `INSPECTION("inspection")` 추가
- [ ] `FileConstant.AVAILABLE_PATH`에 `inspection` 추가
- [ ] `FileBoxOwnerType`, `FileBoxTargetType`에 `INSPECTION` 추가
- [ ] `FileBoxItem`과 `FileBoxItemCdo/Udo/Rdo`에 nullable `category` 추가 + `FileBoxMapper`, `FileBoxDoc`, `FileBoxDocMapper` 반영
- [ ] `StringListConverter`를 `aggregate/common/store/converter/`로 이동하고 `TravelJpo` 참조 수정
- [ ] `InspectionConstant`에 에러 메시지 상수 정의
- [ ] `ErrorCode` 추가
  - `INSPECTION_NOT_FOUND` (400)
  - `INSPECTION_PLACE_NOT_FOUND` (400)
  - `INSPECTION_PLACE_IN_USE` (409)
  - `INSPECTION_QUESTION_NOT_FOUND` (400)
  - `INSPECTION_ANSWER_REQUIRED` (400)
  - `INVALID_INSPECTION_ANSWER` (400)
  - `INVALID_INSPECTION_PLACE` (400)
  - `DUPLICATE_INSPECTION_VISIT` (409)
- [ ] 업로드 디렉터리 `inspection/` 경로 확인(오브젝트 스토리지 전환 상태 포함)
- [ ] 질문 관리 API를 관리자 전용으로 갈 경우 `SecurityConfiguration` matcher 추가

## 15. 테스트 계획

| 대상 | 테스트 |
| --- | --- |
| `InspectionFlowTest` | 필수 질문 누락 시 400 / 비활성 질문 답변 시 400 / 활성 질문 0개면 400 / 등록 시 버전·스냅샷 복사 확인 |
| `InspectionFlowTest` | **질문 문구를 수정해 버전이 올라간 뒤에도 기존 기록 응답이 그대로인지** (요구사항 회귀 테스트) |
| `InspectionFlowTest` | 질문 미사용 → 문구 변경 → 재사용 시 과거 답변의 `questionVersionNo`가 유지되는지 |
| `InspectionFlowTest` | `placeId` 인라인 생성, 같은 좌표·이름 중복 시 409 |
| `InspectionQuestionLogicTest` | `content` 수정 시 버전 증가 / `sortOrder`·`required` 수정 시 버전 유지 / `answerType` 변경 시 400 |
| `InspectionAnswerLogicTest` | 타입별 값 검증(SCORE 0·1·5·6, TEXT 공백, CHOICE 미등록 code, MULTI 빈 배열) |
| `InspectionCompareQueryFlowTest` | 2건 미만·4건 초과 400 / 버전 섞임 표시 / best·worst 플래그 / 한쪽에만 있는 질문 null 채움 |
| `InspectionLogicTest` | 좌표 범위 검증, 같은 날·같은 시간대 중복 방문 409, 수정 시 기존 답변 집합 밖 questionId 거부 |
| `InspectionPlaceLogicTest` | 즐겨찾기 토글, 태그 필터, 방문 있는 Place 삭제 409 |
| `*JpoMapperTest` | 타입별 답변 컬럼 왕복, 태그·장단점 JSON 왕복, 선택지 JSON 왕복 |
| `FileBoxMapperTest` | `category` 추가 후 기존 문서(category 없음)가 null로 읽히는지 |
| `Inspection*ResourceTest` | 엔드포인트 상태 코드, 인증 필요 |
| `Inspection*ResourceJsonContractTest` | 요청/응답 JSON 필드 계약 |

## 16. 구현 순서 (PR 분할 제안)

1. **공통 enum·계약** — `FileType`, `FileConstant`, `FileBoxOwnerType/TargetType`, `FileBoxItem.category`, `ErrorCode`
2. **spec** — 엔티티/VO/enum/facade/sdo/mapper
3. **aggregate(질문)** — Question + Version JPO/Store/Logic, 버전 증가 규칙, 시드 SQL
4. **aggregate(장소)** — Place JPO/Store/Logic
5. **aggregate(방문·답변)** — Inspection + Answer JPO/Store/Logic + `InspectionFlow`
6. **aggregate(비교)** — `InspectionCompareQueryFlow`
7. **rest** — 세 Resource
8. **문서** — `docs/inspection-feature.md`(FE 연동용), `docs/file-asset.md`에 `INSPECTION` owner/target/category 추가

각 단계마다 `./gradlew :{module}:test`, 마지막에 `./gradlew test`를 돌린다.

## 17. 확인이 필요한 결정 사항

1. **도메인 이름** — `inspection`으로 확정해도 되는지(대안: `estate-visit`, `viewing`)
2. **지도 공급자** — 4.4 제안(주소 API 저장 + 카카오 SDK 렌더링)을 따를지, 카카오 로컬 API 응답을 그대로 저장하는 A안을 감수할지
3. **사진 카테고리 위치** — 9절 C안(`FileBoxItem.category` 추가)이 공유 계약 변경을 감수할 만한지
4. **종합 평점** — 한줄평과 별개로 방문별 5점 평점을 둘지(비교 화면의 기본 정렬 축이 된다)
5. **장단점 형태** — 목록(`List<String>`)인지 `TravelReview`처럼 단일 문자열인지
6. **중복 방문 차단** — 같은 날·같은 시간대 재등록을 409로 막는 게 맞는지
7. **질문 관리 권한** — 사용자(USER) 허용인지 관리자 전용인지
8. **비교 대상 상한** — 4건이 적절한지

## 참고

- [카카오 데브톡 — 로컬 API 결과에서 장소ID·장소명·place_url만 저장하는 방식이 허용되는지 문의](https://devtalk.kakao.com/t/api-id-place-url/151265)
- [카카오 데브톡 — Local API 검색 결과 중 사용자가 선택한 단일 장소 정보 저장 가능 범위 문의](https://devtalk.kakao.com/t/local-api/149619)
- [카카오 로컬 API 개발 가이드](https://developers.kakao.com/docs/ko/local/dev-guide)
- [NCP Maps 개요 (Dynamic/Static Map, Geocoding, Directions)](https://api.ncloud-docs.com/docs/application-maps-overview)
- [AI NAVER API ▶ 지도 API 서비스 제공 종료 안내 (2026.06.25)](https://www.ncloud.com/v2/support/notice/all/2158)
- [네이버 지도 API v3 — Geocoder 서브모듈](https://navermaps.github.io/maps.js.ncp/docs/tutorial-Geocoder-Geocoding.html)
