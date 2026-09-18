# 임장 기록 기능 상세 구현 설계

> 상태: 구현 설계(Implementation Design). `requirements_specification.md`를 코드 배치 수준까지 내린 문서다.
> 선행 문서: `docs/field_research/requirements_specification.md`(요구사항 원본), `docs/module.md`(배치 규칙), `docs/architecture.md`(전체 구조), `docs/file-asset.md`(파일 연동 규칙)
> 이 문서는 `docs/inspection/01-spec-design.md`(2026-09 제안)를 **대체**한다. 차이는 부록 A에 정리한다.
> 기준 요구사항: 2026-09-17 개정본(총 49개 비즈니스 규칙). 개정 전 판본과의 차이 및 설계 변경은 부록 B에 정리한다.

**인용 표기 규약**: `[요구사항 §N]`은 요구사항 명세서의 절, `본문 §N`은 이 문서의 절을 가리킨다.

---

## 1. 문서 목적과 범위

### 1.1 목적

요구사항 명세서의 49개 비즈니스 규칙을 이 저장소의 5모듈 구조(`slcn-boot -> slcn-rest -> slcn-auth -> slcn-aggregate -> slcn-spec`)에 배치하고, 그대로 구현에 착수할 수 있는 수준의 클래스·테이블·엔드포인트·검증 규칙·PR 분할을 정의한다.

### 1.2 범위

| 구분 | 내용 |
| --- | --- |
| 포함 | 임장 지역 / 임장 기록 / 확인 매물 / 문답(질문·버전·답변) / 태그 / 사진 연동 / 상태 전이 / 목록·상세 조회 / 질문 관리 API |
| 제외 | 매물 비교[요구사항 §43], 동일 지역 재임장 비교[요구사항 §44], `VisitedComplex` 단지 엔티티[요구사항 §45], 매물 구조화 필드[요구사항 §46], 지도 연동[요구사항 §47] — 확장 지점만 남긴다 |

### 1.3 전제

- 현재 서비스는 사용자별 데이터 분리를 하지 않는다(`Travel`, `Trip` 모두 소유자 컬럼이 없다). 임장도 동일하게 **전역 공유 데이터**로 설계한다. 사용자 스코프가 필요해지면 전 도메인 공통 과제로 따로 다룬다.
- 인가는 기본 정책(`anyRequest().hasAuthority("USER")`)을 따르되, **질문 관리 API의 쓰기 경로는 `ADMIN` 전용**이다(본문 §10.4). 조회는 `USER`로 남긴다 — 사용자가 문답을 작성하려면 질문 목록을 읽어야 하기 때문이다.
- `ddl-auto=update`이므로 신규 테이블 7개는 자동 생성된다. 기존 테이블은 건드리지 않는다.
- 날짜/시간 기준 타임존은 `Asia/Seoul`이다.

---

## 2. 요구사항 → 설계 결정 요약

| # | 요구사항 | 설계 결정 | 근거 |
| --- | --- | --- | --- |
| 1 | §2 도메인 3계층(지역-임장-매물) | `InspectionArea` / `InspectionVisit` / `ViewedProperty` 3개 테이블 분리 | 재임장·다중 매물이 모두 1:N. JSON 내장으로는 매물 단위 질의가 불가능 |
| 2 | §3.1 임장 지역은 **생활권 단위** | `InspectionArea` = 성수동·잠실 수준. 개별 단지가 아니다 | §49-1. 단지는 매물의 속성(`complexName`)으로 내려간다 |
| 3 | §13 단지/건물명 | `ViewedProperty.complexName` **필수 문자열 컬럼**. 별도 엔티티 없음 | §49-6, §49-7. 확장 경로는 본문 §3.4.1 |
| 4 | §17 문답은 매물 기준 | `ViewedProperty`에 **내장된 `PropertyAnswer` VO 목록**(`viewed_property.answers` JSON 컬럼). `InspectionVisit`은 답변을 직접 갖지 않음 | §49-8. 답변은 매물 밖에서 참조되지도, 매물과 다른 생명주기를 갖지도 않는다. 본문 §3.6 |
| 5 | §20 질문 변경 이력 보존 | `InspectionQuestion`에 **내장된 `QuestionVersion` VO 목록**(`inspection_question.versions` JSON 컬럼) | §49-12. 요구사항 §20의 Question → Version 트리를 그대로 표현한다. 버전은 질문 수정의 부산물이며 독립 조작 대상이 아니다. 본문 §3.5 |
| 6 | §24 작성 중 질문 변경 차단 | **매물 생성 시점에 활성 질문 전체를 `PropertyAnswer` 빈 VO로 미리 생성(materialize)**하고 버전·문구·필수여부·정렬순서·단위를 스냅샷으로 고정 | 요구사항 §24가 제시한 두 선택지 중 "`ViewedProperty` 생성 시 적용되는 `QuestionVersion` 목록 Snapshot"을 문자 그대로 구현한다. 본문 §5 |
| 7 | §25 태그 공통 관리 | `InspectionTag` 마스터 + `inspection_visit_tag` / `viewed_property_tag` 연결 테이블 | 요구사항 도메인 다이어그램 그대로. §42 태그 필터를 인덱스 조인으로 처리 |
| 8 | §29 기존 이미지 저장 방식 재사용 | **신규 이미지 테이블을 만들지 않고 기존 `FileBox`(MongoDB) 재사용.** `ownerType=INSPECTION_VISIT`, `targetType=INSPECTION_VISIT` / `VIEWED_PROPERTY` | `FileBox.items`가 이미 `fileAssetId` / `caption` / `sortOrder`를 갖는다. §30·§31 저장 정보와 1:1 대응. 본문 §7 |
| 9 | §10·§16 DRAFT/COMPLETED | 두 엔티티가 공유하는 `InspectionStatus` enum. 완료 검증은 상태 전이 시점에만 수행 | 본문 §4 |
| 10 | §28 장단점 자유 텍스트 | `pros` / `cons` 를 `TEXT` 컬럼 단일 문자열로 저장 | 요구사항 §28 명시. 별도 엔티티 분리 안 함 |
| 11 | §15 관심도 1~5 | `interestLevel` `Integer`(nullable). DRAFT에서는 null 허용, COMPLETED 전이 시 필수 | §10.1 DRAFT 저장 요구와 §37 완료 조건을 동시에 만족 |
| 12 | §5 위도/경도 미저장 | `InspectionArea`에 좌표 컬럼을 두지 않음 | §49-21. 확장 시 컬럼 2개 추가로 끝나도록 나머지 설계를 좌표에 의존시키지 않음 |

---

## 3. 도메인 모델

패키지: `com.seoulchonnom.spec.inspection`

### 3.1 엔티티 개요

```text
InspectionArea ──1:N──> InspectionVisit ──1:N──> ViewedProperty
   (성수동)                (2026-09-17 14:00)      (트리마제 / 101동 1203호)
                                 │                        │            │
                                 │ N:M                    │ N:M        │ 내장(JSON 컬럼)
                                 ▼                        ▼            ▼
                           InspectionTag           InspectionTag   PropertyAnswer[]  ← VO
                              (inspection_tag 마스터 공유)              │
                                                                       │ questionId = 통계·비교의 축
                                                                       ▼
                                                              InspectionQuestion
                                                                       │ 내장(JSON 컬럼)
                                                                       ▼
                                                              QuestionVersion[]  ← VO

FileBox(ownerType=INSPECTION_VISIT, ownerId=visitId)
  ├─ item(targetType=INSPECTION_VISIT,  targetId=null)        → 임장 사진
  └─ item(targetType=VIEWED_PROPERTY,   targetId=propertyId)  → 매물 사진
```

**엔티티 5종**: `InspectionArea`, `InspectionVisit`, `ViewedProperty`, `InspectionQuestion`, `InspectionTag`
**VO 3종**: `PropertyAnswer`, `QuestionVersion`, `QuestionChoice`

Aggregate root는 `InspectionArea`, `InspectionVisit`, `InspectionQuestion` 셋이다.

#### 3.1.1 무엇을 엔티티로 두고 무엇을 VO로 두는가

세 가지를 묻는다. **바깥에서 ID로 참조되는가 / 자기만의 생명주기가 있는가 / 자기 필드로 부모를 가로지르는 질의가 있는가.** 셋 다 아니면 VO다.

| 모델 | 외부 ID 참조 | 독립 생명주기 | 횡단 질의 | 판정 |
| --- | --- | --- | --- | --- |
| `InspectionArea` | `inspection_visit.area_id` | 등록·수정·삭제 API | 지역명 검색[요구사항 §42] | **엔티티** |
| `InspectionVisit` | `viewed_property.inspection_visit_id`, `FileBox.ownerId` | 등록·수정·삭제·상태 전이 | 기간·재방문 의사·태그[요구사항 §42] | **엔티티** |
| `ViewedProperty` | `FileBoxItem.targetId`, `viewed_property_tag.viewed_property_id` | 등록·수정·삭제·상태·정렬 API | 관심도·단지명[요구사항 §42] | **엔티티** |
| `InspectionQuestion` | `PropertyAnswer.questionId` | ADMIN 등록·수정·활성화 | `enabled` 필터, 축별 집계 | **엔티티** |
| `InspectionTag` | 연결 테이블 2종 | get-or-create, 자동완성 | 사용 빈도 정렬 | **엔티티** |
| `PropertyAnswer` | **없음.** 답변 ID를 참조하는 곳이 하나도 없고, 저장 API도 `answerId`가 아니라 `questionId`로 대상을 찾는다 | **없음.** 매물과 함께 생겨 매물과 함께 죽는다 | **없음.** 요구사항 §42의 검색 축에 문답 값이 없다 | **VO** |
| `QuestionVersion` | 답변이 `questionVersionNo`(값)로만 가리킨다 | **없음.** 질문 문구 수정의 부산물이고, 만들어진 뒤 수정도 삭제도 되지 않는다 | **없음** | **VO** |

`ViewedProperty`가 VO가 아닌 이유를 분명히 해 둔다. 매물 단위 정렬·부분 수정·관심도 필터[요구사항 §42]가 기능 요구이고, 무엇보다 **`FileBoxItem.targetId`가 매물 ID를 가리킨다.** 사진이 바깥에서 매물을 참조하므로 매물은 안정적인 식별자를 가진 엔티티여야 한다.

이 저장소에는 같은 판단의 선례가 있다. `Travel`은 엔티티이고 `TravelDay`·`TravelPlace`는 VO로 `travel.days` **TEXT 컬럼에 JSON으로** 저장된다(`TravelJpo.java:38`). `TravelPlace.placeKey`는 그 JSON 안의 UUID인데 `FileBoxItem.targetId`로 쓰인다. 임장의 `PropertyAnswer`는 그보다도 참조가 적다.

### 3.2 InspectionArea

```java
public class InspectionArea extends DomainEntity {
	private String name;            // 필수. 지역명. 예: 성수동, 잠실, 마곡
	private String description;     // 선택. 임장 범위 설명. 예: 서울숲 ~ 뚝섬역 주변
	private boolean hidden;         // 비활성화(요구사항 §34.1 확장 대비). 기본 false
}
```

- ID: `INSPECTION_AREA-{4자리 16진수}` (`IdGeneratorLogic`)
- **개별 단지가 아니라 지역/생활권이다**[요구사항 §3.1, §49-1]. "마포래미안푸르지오"가 아니라 "성수동"이 들어간다. 단지명은 매물의 `complexName`으로 내려간다.
- 좌표와 외부 지도 Place ID는 저장하지 않는다[요구사항 §5, §49-21]. 식별은 `name` + `description`.
- 지역명이 같은 지역이 이미 있으면 신규 생성을 막고 `409`와 함께 기존 후보를 응답에 담는다. FE가 "기존 지역에 임장 추가"를 고르게 한다. 지역명은 "성수동" 수준이라 실질적 동명이 드물고, 중복 생성되면 같은 생활권의 재임장 이력이 두 갈래로 갈라진다. 판정 기준은 본문 §16 결정 항목 9.
- `visitCount`, `lastVisitedAt`은 **저장하지 않고 조회 시 집계**한다. 임장 등록/삭제와 동기화가 어긋날 여지를 만들지 않는다.

### 3.3 InspectionVisit

```java
public class InspectionVisit extends DomainEntity {
	private String areaId;                  // 필수. InspectionArea 참조
	private LocalDateTime visitedAt;        // 필수[요구사항 §7.1]. 날짜+시간 단일 값[요구사항 §9]
	private String memo;                    // 선택, 자유 형식
	private RevisitIntent revisitIntent;    // DRAFT에서는 null 허용, COMPLETED 시 필수
	private String oneLineReview;           // 선택
	private String pros;                    // 선택
	private String cons;                    // 선택
	private InspectionStatus status;        // 필수, 기본 DRAFT
}
```

- ID: `INSPECTION_VISIT-{4자리 16진수}`
- `visitedAt`은 DRAFT에서도 필수다. 임장 작성 시작 흐름[요구사항 §35]에서 지역 다음으로 입력되는 값이고, 목록 정렬 축이기 때문이다.
- `revisitIntent`는 요구사항 §7.1에서 필수지만 §10.1 DRAFT는 부분 저장을 허용해야 한다. **저장 제약이 아니라 완료 조건으로 옮긴다.**
- `revisitIntent`가 `InspectionArea`가 아니라 여기 있는 이유: 요구사항 §8이 "지역 자체의 속성이 아니라 특정 임장 시점에서 사용자가 내린 판단"이라고 명시한다. 같은 성수동이라도 9월 임장은 `YES`, 11월 임장은 `MAYBE`일 수 있다.
- `visitTimeSlot`은 두지 않는다[요구사항 §9]. 화면 표기(요일/오전·오후)는 `visitedAt`에서 FE가 파생한다.

### 3.4 ViewedProperty

```java
public class ViewedProperty extends DomainEntity {
	private String inspectionVisitId;   // 필수
	private String complexName;         // 필수. 단지/건물명 [요구사항 §13]
	private String name;                // 필수. 매물명 [요구사항 §14 자유 입력]
	private String memo;                // 선택
	private String oneLineReview;       // 선택
	private String pros;                // 선택
	private String cons;                // 선택
	private Integer interestLevel;      // 1~5. DRAFT에서는 null 허용
	private InspectionStatus status;    // 필수, 기본 DRAFT
	private int sortOrder;              // 요구사항 §33 임장 내 표시 순서

	private List<PropertyAnswer> answers;   // 내장 VO 목록. 본문 §3.6
	private int requiredAnswerCount;        // 파생값. 본문 §3.6.1
	private int unansweredRequiredCount;    // 파생값. 본문 §3.6.1
}
```

- ID: **UUID**. `id_sequence`는 상한이 `0xFFFF`(65,535)라 `임장 수 × 매물 수`로 늘어나는 값에 맞지 않는다.
- 문답은 이 엔티티 안에 산다(본문 §3.6). 매물을 지우면 답변도 함께 사라지고, 별도 삭제 단계가 없다.
- `complexName`과 `name`은 **둘 다 필수**다[요구사항 §12, §37-1, §37-2]. 임장 지역이 생활권 단위로 넓어지면서 "어느 단지의 매물인가"가 매물 식별에 필수가 되었다.
- 동/호수/평형을 분리하지 않는다[요구사항 §14, §46]. 확장 시 `name`은 표시용으로 남기고 구조화 컬럼을 추가한다.
- `sortOrder`는 생성 시 `현재 최대값 + 1`로 자동 채번하고, 별도 정렬 변경 API로 일괄 갱신한다[요구사항 §33].

#### 3.4.1 `complexName`을 문자열로 두는 것의 의미와 확장 경로

요구사항 §45는 향후 `VisitedComplex` 엔티티로 분리될 수 있음을 명시한다.

```text
현재:   InspectionArea → InspectionVisit → ViewedProperty(complexName: String)
확장 후: InspectionArea → InspectionVisit → VisitedComplex → ViewedProperty(visitedComplexId)
```

이 확장이 나중에 가능하려면 **지금 `complexName`에 의존하는 설계를 만들지 않아야 한다.** 구체적으로:

- `complexName`에 유니크 제약을 걸지 않는다. 같은 임장에 같은 단지의 매물이 여러 건 있는 것이 정상이다[요구사항 §41, §49-23].
- `complexName`을 정규화해 별도 마스터로 승격시키지 않는다. 지금 마스터를 만들면 `VisitedComplex` 도입 시 두 개의 단지 개념이 공존하게 된다.
- 단지 단위 메모·평가·사진을 `ViewedProperty`에 억지로 넣지 않는다. 그것이 필요해지는 시점이 곧 `VisitedComplex` 도입 시점이다.
- 화면의 단지별 그룹핑[요구사항 §41]은 **서버가 아니라 FE가** 한다. 본문 §11.2 참조.

확장 시 실제 작업은 `visited_complex` 테이블 추가 + `viewed_property.visited_complex_id` 컬럼 추가 + 기존 `complex_name` 값으로 단지 행 생성하는 마이그레이션 한 번이다.

#### 3.4.2 단지명 입력 편의

한 임장에서 같은 단지의 매물을 여러 건 등록하는 것이 기본 시나리오다[요구사항 §3.3, §41]. `complexName`이 자유 입력이므로 "트리마제"와 "트리마제 " 같은 표기 흔들림이 생기면 §41의 그룹핑이 깨진다. 최소 방어:

- 저장 시 `trim()` 후 연속 공백을 하나로 줄인다.
- 매물 등록 화면에서 **해당 임장에 이미 등록된 단지명 목록**을 자동완성 후보로 내려준다(본문 §10.3 `GET .../complex-names`).

전역 단지명 마스터를 만들지 않는 이유는 §3.4.1과 같다. 후보는 "이 임장 안에서 이미 쓴 이름"으로 충분하다.

### 3.5 InspectionQuestion / QuestionVersion

```java
public class InspectionQuestion extends DomainEntity {
	private QuestionAnswerType answerType;  // 생성 후 변경 불가
	private boolean required;               // 요구사항 §22
	private int sortOrder;                  // 요구사항 §23
	private boolean enabled;                // 요구사항 §19
	private List<QuestionVersion> versions; // 1번부터. 추가만 되고 수정·삭제되지 않는다
	private int currentVersionNo;           // versions의 마지막 번호
}

public class QuestionVersion implements JsonSerializable {   // VO
	private int versionNo;                  // 1부터
	private String content;                 // 질문 문구
	private String description;             // 도움말
	private List<QuestionChoice> choices;   // SELECT 계열에서만 사용
	private String unit;                    // NUMBER에서만 사용. 예: 만원, m2
}

public class QuestionChoice implements JsonSerializable {    // VO
	private String code;    // 불변 식별자. 답변이 참조한다
	private String label;   // 표시 문구
	private int sortOrder;
}
```

- `InspectionQuestion` ID: `INSPECTION_QUESTION-{4자리 16진수}`.
- **`QuestionVersion`은 별도 테이블이 아니라 `inspection_question.versions` JSON 컬럼이다.** 요구사항 §20이 그리는 `Question ├─ v1 ├─ v2 └─ v3` 트리를 그대로 표현한다. 버전은 질문당 한 자릿수로 늘고, 조회는 항상 "이 질문의 전체 이력"이라 부모와 함께 읽는 것이 자연스럽다.
- 버전 번호는 `versions.size() + 1`로 채번한다. 별도 시퀀스도, `uk_inspection_question_version` 유니크 제약도 필요 없다 — 같은 행 안의 리스트라 `EntityJpo`의 `@Version` 낙관적 잠금이 동시 채번을 막는다(본문 §4.6).
- 답변은 버전을 **ID가 아니라 `questionVersionNo`(값)으로** 가리킨다. 버전에 행 ID가 없기 때문이고, 어차피 답변은 문구 자체를 스냅샷으로 들고 있어 버전을 되짚을 일이 없다.
- 질문 물리 삭제 API는 만들지 않는다. 요구사항 §18·§49-20·§34.4가 금지하는 것은 엄밀히 **"기존 답변이 연결된"** 질문의 물리 삭제지만, 답변 0건인 질문만 골라 지우는 API를 따로 두는 실익이 없어 **삭제 경로 자체를 만들지 않는 쪽으로 단순화한다.** 잘못 만든 질문은 `enabled=false`로 내린다.

**버전이 올라가는 변경과 올라가지 않는 변경을 구분한다.**

| 변경 | 결과 | 이유 |
| --- | --- | --- |
| `content`, `description` 수정 | 새 버전 추가 (`versionNo + 1`) | 문구가 바뀌면 과거 답변의 의미가 달라진다[요구사항 §20] |
| `choices` 추가·수정·삭제 | 새 버전 추가 | 선택지를 지우면 그 선택지를 고른 과거 답변이 고아가 된다 |
| `unit` 변경 | 새 버전 추가 | 수치의 의미가 바뀐다 |
| `required` 변경 | 버전 유지 | 수집 정책이지 질문의 의미가 아니다. 진행 중 매물에는 스냅샷이 이미 고정되어 있다 |
| `sortOrder` 변경 | 버전 유지 | 표시 순서일 뿐 과거 데이터에 영향 없음 |
| `enabled` 토글 | 버전 유지 | "지금 물어볼 질문인가"만 답한다[요구사항 §19] |
| `answerType` 변경 | **금지(`400`)** | 아래 단락 참조 |

기존 버전은 **절대 수정하지 않는다.** 리스트에 추가만 한다. 이것이 요구사항 §20을 지키는 최소 규칙이고, JSON 컬럼이라 DB가 대신 막아주지 않으므로 `InspectionQuestionLogic`이 이 불변식을 강제한다(본문 §14.1 테스트 대상).

요구사항 §18은 관리자 기능으로 "질문 타입 설정"을 나열한다. 이 설계는 그것을 **생성 시점의 설정**으로 좁히고 생성 후 변경은 막는다. 타입별 값 필드가 분리되어 있어(본문 §3.6) `TEXT`로 쌓인 답변과 `RATING`으로 쌓인 답변을 한 질문 아래에서 읽을 방법이 없기 때문이다. 타입을 바꿔야 하면 **기존 질문을 미사용 처리하고 새 질문을 만든다** — 과거 답변은 그대로 조회되고(요구사항 §19) 새 축이 생긴다. 요구사항 §20("질문 내용 변경으로 기존 기록이 변경되어서는 안 된다")을 지키려면 이 제약이 불가피하다.

관심사 분리:

| 관심사 | 담당 |
| --- | --- |
| 지금 이 질문을 물어볼 것인가 | `InspectionQuestion.enabled` |
| 이 질문은 어떻게 바뀌어 왔나 | `InspectionQuestion.versions` |
| 과거 기록 화면이 그때 그대로인가 | `PropertyAnswer`의 문구 스냅샷 |
| 여러 기록을 같은 축으로 묶을 수 있는가 | `PropertyAnswer.questionId` + `questionVersionNo` |

### 3.6 PropertyAnswer (VO)

`ViewedProperty`에 내장된다. 별도 테이블도, 행 ID도 없다.

```java
public class ViewedProperty extends DomainEntity {
	// ... 본문 §3.4의 필드 ...
	private List<PropertyAnswer> answers;   // viewed_property.answers JSON 컬럼
	private int requiredAnswerCount;        // 파생값. 아래 참조
	private int unansweredRequiredCount;    // 파생값. 아래 참조
}

public class PropertyAnswer implements JsonSerializable {   // VO
	private String questionId;              // 통계·비교의 축. 리스트 안에서 유일하다
	private int questionVersionNo;          // 답변 시점 버전 번호

	private String questionContent;         // 스냅샷: 답변 시점 문구
	private String questionDescription;     // 스냅샷
	private QuestionAnswerType answerType;  // 스냅샷
	private boolean required;               // 스냅샷: 완료 검증 기준
	private int sortOrder;                  // 스냅샷: 표시 순서
	private String unit;                    // 스냅샷: NUMBER의 단위. 예: 만원, m2
	private List<QuestionChoice> choiceOptions; // 스냅샷: SELECT 계열 선택지

	private String textValue;               // TEXT, LONG_TEXT
	private Boolean booleanValue;           // BOOLEAN
	private BigDecimal numberValue;         // NUMBER
	private Integer ratingValue;            // RATING (1~5)
	private List<String> selectedCodes;     // SINGLE_SELECT, MULTI_SELECT

	private boolean answered;               // 파생값
}
```

- **`questionId`가 리스트 안의 식별자다.** 매물 하나에 같은 질문이 두 번 들어가지 않으므로 별도 ID가 필요 없고, 답변 저장 API도 이미 `questionId`로 대상을 찾는다(본문 §10.6).
- `questionId`는 "어느 축의 답인가", `questionVersionNo`는 "어느 문구에 답했는가", `questionContent`는 "질문 마스터를 읽지 않고 그대로 그린다"를 각각 책임진다. 셋 다 필요하다.
- 값은 타입별 필드로 나눈다. 문자열 하나에 몰면 타입별 검증(본문 §5.5)과 FE 위젯 선택이 불가능해진다.
- `required`를 답변에 스냅샷으로 복사하는 것이 요구사항 §24의 핵심이다. 완료 검증이 **질문 마스터의 현재 상태를 보지 않는다.**
- `unit`을 스냅샷에 포함한다. 빠뜨리면 `NUMBER` 답변이 `3.5`만 남고 "만원"인지 "m2"인지 잃는다. 단위 변경은 새 버전을 만드는 변경이므로(본문 §3.5) 마스터를 되짚어 복원할 수도 없다 — 요구사항 §20을 지키려면 값 옆에 있어야 한다.

`answered` 판정 규칙(저장 시 계산):

| answerType | answered = true 조건 |
| --- | --- |
| `TEXT`, `LONG_TEXT` | `textValue`에 공백이 아닌 문자가 있음 |
| `BOOLEAN` | `booleanValue != null` |
| `NUMBER` | `numberValue != null` |
| `RATING` | `ratingValue != null` (1~5) |
| `SINGLE_SELECT` | `selectedCodes`의 크기가 정확히 1 |
| `MULTI_SELECT` | `selectedCodes`가 비어 있지 않음 |

#### 3.6.1 파생 카운트를 매물 행에 저장하는 이유

`requiredAnswerCount`와 `unansweredRequiredCount`는 `answers`를 순회하면 언제든 다시 구할 수 있는 값이다. 그럼에도 **매물 행의 정수 컬럼으로 저장한다.**

답변이 별도 테이블이었다면 목록 화면의 미완료 집계(본문 §10.9)를 `GROUP BY`로 받을 수 있었다. JSON 컬럼에서는 그 길이 막힌다. 저장하지 않으면 지역 목록 한 번에 **모든 매물의 `answers` JSON을 읽어 파싱해야 한다** — 목록이 답변 본문 조회로 바뀐다.

정수 두 개를 저장하면 목록 질의는 이렇게 끝난다.

```sql
SELECT inspection_visit_id,
       count(*)                          AS property_count,
       sum(unanswered_required_count)    AS unanswered_required_count,
       count(*) FILTER (WHERE status = 'DRAFT') AS draft_property_count
  FROM slcn.viewed_property
 WHERE inspection_visit_id IN (...)
 GROUP BY inspection_visit_id
```

`answers` 컬럼을 **한 바이트도 읽지 않는다.** 답변이 테이블이던 설계보다 오히려 싸다(테이블 조인 1회가 사라진다).

두 값은 `answers`가 바뀌는 모든 경로에서 함께 갱신된다. 경로는 매물 생성(스냅샷 생성)과 `PUT .../answers` 둘뿐이고, 둘 다 `ViewedPropertyFlow`를 지나므로 갱신 지점이 한 곳이다. 같은 행 안의 값이라 `answers`와 카운트가 서로 어긋난 채 커밋될 수 없다 — 답변이 별도 테이블이었다면 불가능했던 보장이다.

#### 3.6.2 매물 행이 무거워지는 것에 대한 대비

질문 수십 개의 문구·설명·선택지를 스냅샷으로 들고 있으므로 `answers` JSON은 매물 한 건당 수 KB가 된다. **목록·집계 질의는 이 컬럼을 읽으면 안 된다.**

- 매물 요약이 필요한 모든 조회는 `ViewedPropertySummaryPdo` projection으로 필요한 컬럼만 읽는다(본문 §11.1). `answers`는 projection에 넣지 않는다.
- `answers` 전체를 읽는 것은 매물 상세와 임장/지역 상세뿐이다. 이때는 어차피 화면에 문답을 그려야 한다.

`TravelLogic.getTravels()`는 목록에서 `days` JSON을 전부 읽는다. 임장은 처음부터 projection으로 간다.

### 3.7 InspectionTag

```java
public class InspectionTag extends DomainEntity {
	private String name;    // 정규화된 태그명. 유니크
}
```

- ID: UUID.
- `name`은 `trim()` 후 저장하고 앞의 `#`은 제거한다. 대소문자는 있는 그대로 두되 유니크 판정은 원문 기준으로 한다(한글 태그가 대부분이므로 케이스 폴딩이 이득보다 혼란이 크다).
- 연결은 `inspection_visit_tag`, `viewed_property_tag` 두 테이블이 담당한다.
- 임장 태그(`#한강`, `#직주근접`, `#교통혼잡` — 지역·생활권 특성)[요구사항 §26]와 매물 태그(`#남향`, `#고층` — 개별 매물 특성)[요구사항 §27]는 **같은 마스터 풀을 공유**한다[요구사항 §25 "태그 자체는 공통으로 관리"]. 용도 구분 필드는 두지 않는다 — 본문 §16 결정 항목 3 참조.

### 3.8 VO / enum

```java
public enum RevisitIntent { YES, MAYBE, NO }                 // 요구사항 §8
public enum InspectionStatus { DRAFT, COMPLETED }            // 요구사항 §10, §16 공용
public enum QuestionAnswerType {                             // 요구사항 §21
	TEXT, LONG_TEXT, BOOLEAN, SINGLE_SELECT, MULTI_SELECT, NUMBER, RATING
}
```

값 객체 3종은 전부 `JsonSerializable`을 구현하고 JSON 컬럼에 실린다.

| VO | 사는 곳 | 저장 |
| --- | --- | --- |
| `PropertyAnswer` | `ViewedProperty.answers` | `viewed_property.answers` TEXT (JSON) |
| `QuestionVersion` | `InspectionQuestion.versions` | `inspection_question.versions` TEXT (JSON) |
| `QuestionChoice` | `QuestionVersion.choices`, `PropertyAnswer.choiceOptions` | 위 두 컬럼 안에 중첩 |

- `InspectionStatus`는 임장과 매물이 공유한다. 두 상태 집합이 완전히 같고[요구사항 §10, §16], 한쪽만 값이 늘어날 때 분리하면 된다.
- `VisitTimeSlot { MORNING, AFTERNOON, EVENING, NIGHT }`은 **초기 범위에서 만들지 않는다**[요구사항 §9]. 필요해지면 `InspectionVisit`에 nullable 컬럼 하나를 더하는 변경으로 끝난다.

---

## 4. 상태 전이와 검증 규칙

### 4.1 상태 머신

```text
[매물]
  생성 ──> DRAFT ──(completeProperty: 요구사항 §37 검증 통과)──> COMPLETED
             ▲                                                      │
             └────────────────(reopenProperty: 명시 요청)────────────┘

[임장]
  생성 ──> DRAFT ──(completeVisit: 요구사항 §36 검증 통과)──> COMPLETED
             ▲                                                  │
             ├──────────────(reopenVisit: 명시 요청)─────────────┘
             └──────────────(COMPLETED 임장에 매물 추가/수정 시 자동 복귀)
```

### 4.2 매물 완료 조건 [요구사항 §37]

`PATCH /inspection-visits/{visitId}/properties/{propertyId}/status` → `COMPLETED`

1. `complexName`에 공백이 아닌 값이 있다.
2. `name`에 공백이 아닌 값이 있다.
3. `interestLevel`이 존재한다.
4. `interestLevel`이 1~5 범위다.
5. `unansweredRequiredCount == 0`이다. 이 값은 매물 행 자신의 컬럼이므로 다른 테이블을 읽지 않는다(본문 §3.6.1).

실패 시 `400 INSPECTION_ANSWER_REQUIRED`(5번) 또는 `400 INVALID_VIEWED_PROPERTY`(1~4번). 응답 메시지에 미답변 `questionId` 목록을 담아 FE가 해당 문항으로 스크롤할 수 있게 한다.

### 4.3 임장 완료 조건 [요구사항 §36]

`PATCH /inspection-visits/{visitId}/status` → `COMPLETED`

1. `areaId`가 유효한 지역을 가리킨다.
2. `visitedAt`이 존재한다.
3. `revisitIntent`가 존재한다.
4. 이 임장에 속한 모든 `ViewedProperty`의 `status`가 `COMPLETED`다.

**매물 0건 임장도 완료할 수 있다.** 요구사항 §36이 "임장에는 매물이 존재하지 않아도 된다"고 단정한다("성수동 상권 및 생활권만 확인하고 돌아온 임장"). 4번 조건은 "매물이 있다면 전부 완료"로 읽는다.

요구사항 §36-4·§36-6의 "완료 대상 매물"이라는 표현은 **완료 대상 여부를 표시하는 필드가 존재하지 않으므로 "등록된 모든 매물"로 해석한다.** 이 해석이 요구사항 §35 등록 Flow("추가 매물 존재? NO → 임장 완료")와 일치한다. 다른 해석(참고용 DRAFT 매물을 남긴 채 임장 완료)을 택하려면 필드가 하나 필요하다 — 본문 §16 결정 항목 8.

실패 시 `400 INVALID_INSPECTION_VISIT`, 메시지에 미완료 매물 ID 목록을 담는다.

### 4.4 완료 이후 수정 정책

요구사항에 명시가 없어 아래 규칙을 정한다. 근거는 "COMPLETED는 검증을 통과한 상태"라는 불변식을 깨지 않는 것이다.

| 상황 | 동작 |
| --- | --- |
| `COMPLETED` 매물의 기본 정보 수정 (`PUT .../properties/{id}`) | 저장은 허용하되 **저장 직후 요구사항 §37을 재검증**한다. 위반하면 `400`으로 거절하고 아무것도 저장하지 않는다 |
| `COMPLETED` 매물의 **문답 저장** (`PUT .../properties/{id}/answers`) | **동일하게 요구사항 §37을 재검증한다.** 필수 문항을 비우는 저장은 `400`으로 거절한다 |
| `COMPLETED` 매물을 고치려는데 필수 문답을 비워야 하는 경우 | FE가 먼저 `status`를 `DRAFT`로 되돌린 뒤 수정한다 |
| `COMPLETED` 임장에 매물 추가 | 새 매물은 `DRAFT`이므로 §4.3-4를 깬다. **임장을 자동으로 `DRAFT`로 되돌린다.** 응답에 바뀐 임장 상태를 담아 FE가 배지를 갱신하게 한다 |
| `COMPLETED` 임장의 매물을 `DRAFT`로 되돌림 | 위와 동일하게 임장도 `DRAFT`로 자동 복귀 |
| `COMPLETED` 임장에서 마지막 미완료 매물이 삭제됨 | 임장 상태는 그대로 둔다. 조건을 깨지 않기 때문 |

자동 복귀를 택한 이유: 여기서 `409`를 던지면 사용자가 "매물을 추가하려면 먼저 임장을 미완료로 바꾸세요"라는 무의미한 단계를 밟아야 한다. 상태는 검증 결과의 표현이므로 조건이 깨지면 표현이 따라가는 편이 맞다.

**불변식을 깨뜨릴 수 있는 경로를 빠짐없이 막는다.** 요구사항 §37의 조건 5(필수 문답)는 오직 답변 값으로만 깨지는데, 답변은 매물 기본 정보와 **다른 엔드포인트**(`PUT .../answers`)로 저장된다. 이 경로에 재검증을 걸지 않으면 다음이 성립한다.

```text
매물 P = COMPLETED (필수 문답 전부 답변됨)
  ↓ PUT .../answers 로 필수 문항의 textValue를 "" 로 저장
answers[i].answered     = false 로 갱신됨
viewed_property.status  = COMPLETED 그대로          ← 불변식 붕괴
inspection_visit.status = COMPLETED 그대로          ← "모든 매물 COMPLETED"가 필드값만 보면 여전히 참
```

이 상태는 어떤 화면에서도 경고가 뜨지 않고 영구히 남는다. 따라서 **`COMPLETED` 매물의 상태를 바꿀 수 있는 모든 쓰기 경로(기본 정보 PUT, 문답 PUT)가 같은 재검증을 공유한다.** `ViewedPropertyFlow`에 `revalidateIfCompleted(propertyId)` 하나를 두고 두 경로가 호출하는 형태로 구현해, 검증 로직이 갈라지지 않게 한다.

### 4.5 DRAFT에서 허용하는 저장 [요구사항 §10.1, §16.1]

DRAFT 상태에서는 아래 어떤 조합도 `400` 없이 저장된다.

- 매물 0건 / 일부 매물만 등록
- 필수 문답 미답변
- `interestLevel` 미입력
- `revisitIntent` 미입력
- 사진 0건

DRAFT에서도 거절하는 것: `visitedAt` 누락, 존재하지 않는 `areaId`, `complexName`·`name` 공백, 범위를 벗어난 `interestLevel`(0, 6 등), 스냅샷에 없는 `questionId`로 온 답변, 타입에 맞지 않는 값.

즉 **"덜 채운 것"은 허용하고 "틀린 것"은 거절한다.**

`complexName`과 `name`을 DRAFT에서도 요구하는 이유: 매물 생성 API는 이 두 값을 받아야 목록에 표시할 이름이 생긴다. 요구사항 §35의 매물 작성 흐름에서도 둘 다 첫 단계다.

### 4.6 동시성과 잠금

이 설계에는 **"읽어서 검증하고 그 결과로 쓰는"(check-then-act) 지점이 네 군데** 있다. 외래키 제약을 걸지 않으므로(본문 §8.6) DB가 대신 막아주지 않는다. 각각의 보호 방법을 여기서 한 번 정한다.

| # | 지점 | 레이스 시나리오 | 보호 |
| --- | --- | --- | --- |
| 1 | 지역 중복 검사 → 생성 | 같은 요청을 더블클릭하면 두 요청이 "성수동 없음"을 동시에 확인하고 둘 다 INSERT. **이 설계가 막으려던 "재임장 이력이 두 갈래로 갈라지는" 결함이 그대로 재현된다** | `inspection_area.name`에 **유니크 인덱스**(`uk_inspection_area_name`)를 걸고, INSERT의 제약 위반을 잡아 `409 INSPECTION_AREA_DUPLICATED` + 기존 후보 응답으로 변환한다 |
| 2 | 지역 삭제 전 "임장 0건" 검사 → 삭제 | T1이 `count == 0`을 확인한 직후 T2가 그 지역으로 임장을 등록하고, T1이 지역을 지운다. `inspection_visit.area_id`가 없는 지역을 가리켜 **상세 조회(본문 §11.2-2)가 깨진다** | 삭제 트랜잭션에서 지역 행에 `SELECT ... FOR UPDATE`를 걸고 그 안에서 검사·삭제를 수행한다. 임장 생성 시의 `areaId` 검증도 같은 행을 `FOR SHARE`로 읽어 두 트랜잭션이 직렬화되게 한다 |
| 3 | 매물 완료 검증 → `status = COMPLETED` | T1이 완료 조건을 읽고 통과 판정한 뒤, 커밋 전에 T2가 `PUT .../answers`로 필수 답변을 비우고 먼저 커밋한다 | **문답과 상태가 같은 행에 있으므로 `EntityJpo`의 `@Version` 낙관적 잠금이 그대로 막는다.** T1이 읽은 `entityVersion`으로 UPDATE가 나가고, T2가 먼저 커밋했으면 0건 갱신으로 `OptimisticLockingFailureException`이 난다. 명시적 잠금이 필요 없다 — 답변을 매물 안에 둔 결정(본문 §3.6)이 이 레이스를 구조적으로 없앤다 |
| 4 | 태그 get-or-create | 두 사용자가 동시에 신규 태그 `#한강`으로 저장하면, 나중 요청이 `uk_inspection_tag_name` 위반으로 **임장 등록 트랜잭션 전체가 실패**한다 | 유니크 위반(`DataIntegrityViolationException`)을 잡아 **재조회 후 기존 행을 재사용**한다. 이 경우는 사용자 오류가 아니라 정상 흐름이므로 에러를 노출하지 않는다 |

질문 문구 수정도 마찬가지다. 버전 목록이 `inspection_question` **같은 행 안의 JSON**이므로 두 관리자가 동시에 같은 질문을 고치면 `@Version`이 나중 요청을 막는다. 유니크 제약으로 뒤늦게 걸리는 것이 아니라 갱신 시점에 바로 걸린다. 이때 `OptimisticLockingFailureException`을 **`409 INSPECTION_QUESTION_CONFLICT`로 매핑하고 "질문이 이미 수정되었습니다. 새로고침 후 다시 시도하세요"** 메시지를 돌려준다. 500으로 새어나가면 관리자가 원인을 알 수 없다.

`EntityJpo`는 `@Version entityVersion`을 가지고 있고 `@MappedSuperclass`이므로 **모든 JPO에 낙관적 잠금이 자동으로 걸린다.** 이것이 보호하는 범위는 "같은 행의 동시 갱신"뿐이다. 그래서 하위 데이터를 부모 행 안에 두는 결정이 동시성 측면에서 이득이 된다 — 위 표에서 3번이 사라지고 질문 버전 충돌이 자동으로 처리되는 것이 그 결과다. 남은 1·2·4번은 서로 다른 행을 오가는 검사라 여전히 명시적 보호가 필요하다.

---

## 5. 문답 스냅샷 설계 [요구사항 §24 핵심]

### 5.1 선택한 방식

**매물 생성 시점에 활성 질문 전체를 `PropertyAnswer` 빈 VO로 만들어 매물 안에 넣는다(materialization).**

```text
POST /inspection-visits/{visitId}/properties
        ↓
InspectionQuestion where enabled = true order by sortOrder  조회 (1회)
        ↓
각 질문의 currentVersion을 versions 리스트에서 꺼낸다 (같은 행이라 추가 조회 없음)
        ↓
질문 수만큼 PropertyAnswer VO 생성
  - questionId, questionVersionNo
  - questionContent, questionDescription, answerType, required, sortOrder, unit, choiceOptions  ← 전부 스냅샷
  - 값 필드는 전부 null, answered = false
        ↓
ViewedProperty 저장 (DRAFT) — 매물과 문답이 한 번의 INSERT로 함께 들어간다
        ↓
응답: 매물 + 빈 문답 목록(화면에 그대로 렌더링 가능)
```

이후 답변 저장(`PUT .../answers`)은 **리스트 안 항목의 값 필드만 갱신**한다. 항목을 추가하거나 제거하지 않는다.

질문 마스터 조회가 **1회**로 끝나는 점을 짚어 둔다. 버전이 별도 테이블이던 때는 질문 조회 후 현재 버전을 다시 읽어야 했다.

### 5.2 이 방식이 요구사항을 만족하는 방법

| 요구사항 | 만족 방식 |
| --- | --- |
| §24 작성 시작 후 질문이 추가돼도 강제하지 않음 | 매물 생성 시점에 리스트가 확정된다. 이후 추가된 질문은 이 매물의 리스트에 없으므로 화면에도 검증에도 등장하지 않는다 |
| §20 질문 문구 수정이 기존 기록에 영향 없음 | `questionContent`가 복사되어 있다. 마스터를 고쳐도 과거 항목은 그대로 |
| §19 미사용 질문도 기존 기록에서는 조회 가능 | 항목이 이미 존재하므로 `enabled=false`와 무관하게 조회된다 |
| §22 필수 문답 검증 | `required`가 항목에 스냅샷되어 있어 마스터 변경과 독립적으로 검증된다 |
| §49-11 과거 기록 동일 복원 | 항목 하나로 질문 문구·타입·단위·선택지·순서가 모두 복원된다. 조회 한 번으로 렌더링 가능 |

### 5.3 별도 `QuestionnaireVersion` 엔티티를 두지 않는 이유

요구사항 §24와 §48은 두 가지를 열어 두었다.

```text
QuestionnaireVersion
또는
ViewedProperty 생성 시 적용되는 QuestionVersion 목록 Snapshot
```

**후자를 문자 그대로 구현한다.** 매물이 자기 안에 `QuestionVersion` 목록의 스냅샷을 들고, 거기에 답변 값이 붙어 있는 형태다. 전자를 택하면 테이블 2개(`questionnaire_version`, `questionnaire_question`)와 "질문이 하나 바뀔 때마다 새 세트 버전을 만들지, 기존 세트를 수정할지" 판단 규칙이 추가로 필요하다.

답변을 별도 테이블(`property_answer`)로 두는 중간안도 검토했고, 그쪽을 택하지 않았다. 답변은 **바깥에서 참조되지 않고, 매물과 생명주기가 같고, 자기 값으로 매물을 가로지르는 질의가 요구사항에 없다**(본문 §3.1.1). 세 조건이 모두 아닌 데이터를 테이블로 올리면 얻는 것 없이 다음을 잃는다.

| 잃는 것 | 내용 |
| --- | --- |
| 원자성 | 답변 쓰기와 매물 상태 갱신이 두 테이블에 걸쳐 일어나 완료 검증에 명시적 행 잠금이 필요해진다(본문 §4.6-3) |
| 조회 1회 | 상세 화면에서 답변 조회가 별도 왕복으로 남는다 |
| 삭제 단계 | 임장·매물 삭제마다 답변 삭제 단계가 하나 더 붙는다 |
| 비정규화 컬럼 | 임장 단위 삭제·집계를 위해 `inspection_visit_id`를 답변에 복사해야 한다 |

트레이드오프는 정직하게 적어 둔다.

| 잃는 것 | 영향 | 판단 |
| --- | --- | --- |
| 답변 값으로 인덱스 검색 | `content`가 TEXT JSON이라 "채광 4점 이상" 류 질의는 전건 스캔이다 | **요구사항 §42의 검색 축에 문답 값이 없다.** 임장 기준은 지역명·기간·재방문 의사·태그, 매물 기준은 단지명·매물명·관심도·태그다. 요구되지 않는 능력을 위해 구조를 무겁게 하지 않는다 |
| 버전별 `answerCount`(FE 요구 #5) | 질문별·버전별 답변 수를 세려면 `viewed_property` 전건을 읽어 JSON을 파싱해야 한다 | **느려진다.** 관리자 화면 저빈도 조회이고 매물이 수천 건 규모라 허용한다. 본문 §11.5에 처리 방법과 전환 임계값을 둔다 |
| 매물 행 크기 | 매물당 수 KB | projection으로 분리한다(본문 §3.6.2) |

**되돌릴 수 있는 결정이다.** 문답 값 검색이 실제로 요구되면 `answers` JSON을 `property_answer` 테이블로 펼치는 마이그레이션 한 번이면 된다. 스냅샷 필드가 이미 전부 들어 있어 변환에 외부 정보가 필요 없다.

### 5.4 활성 질문이 0개일 때

매물 생성은 **허용한다.** 문답 없이 단지명·매물명·관심도·사진만 기록하는 것이 요구사항상 불가능하지 않다(요구사항 §37 완료 조건에 "모든 필수 질문에 대한 답변"만 있고 질문 존재 자체는 요구하지 않는다). 이 경우 필수 문답 검증은 자동으로 통과한다.

### 5.5 타입별 값 검증

답변 저장 시 스냅샷된 `answerType` 기준으로 검증한다. 질문 마스터의 현재 타입을 보지 않는다.

| answerType | 검증 |
| --- | --- |
| `TEXT` | 길이 상한 500자. 공백만 있으면 미답변으로 처리(에러 아님) |
| `LONG_TEXT` | 길이 상한 5,000자 |
| `BOOLEAN` | `true` / `false` / `null`만 허용 |
| `NUMBER` | `numberValue` 외 다른 값 컬럼이 오면 `400`. 정밀도 `numeric(15,4)`. 음수 허용 |
| `RATING` | 1~5 정수. 벗어나면 `400` |
| `SINGLE_SELECT` | `selectedCodes` 크기 0 또는 1. 값은 `choiceOptions`의 `code`에 존재해야 함 |
| `MULTI_SELECT` | `selectedCodes` 전부 `choiceOptions`의 `code`에 존재해야 함. 중복 불가 |

공통: 요청의 `questionId`가 이 매물의 답변 행에 없으면 `400 INSPECTION_QUESTION_NOT_FOUND`. 타입에 맞지 않는 값 컬럼이 함께 오면 `400 INVALID_PROPERTY_ANSWER`.

---

## 6. 태그 설계 [요구사항 §25~§27]

### 6.1 구조

```text
inspection_tag          (id, name UNIQUE)
inspection_visit_tag    (id, inspection_visit_id, tag_id)   UNIQUE(inspection_visit_id, tag_id)
viewed_property_tag     (id, viewed_property_id, tag_id)    UNIQUE(viewed_property_id, tag_id)
```

연결 테이블에 복합 기본키 대신 **UUID 단일 PK + 유니크 인덱스**를 쓴다. 이 저장소의 모든 JPO가 `EntityJpo`(단일 String `@Id`)를 상속하므로 `@IdClass`/`@EmbeddedId`를 도입하면 이 테이블들만 다른 모양이 된다.

### 6.2 저장 동작

- 임장/매물 저장 요청은 `tags: ["한강", "직주근접"]`처럼 **이름 배열**을 받는다. 태그 ID를 FE가 관리하지 않는다.
- 서버가 이름을 정규화(`trim`, 선행 `#` 제거)한 뒤 `inspection_tag`에서 찾고 없으면 생성한다(get-or-create). **유니크 위반이 나면 에러로 올리지 않고 재조회해 기존 행을 재사용한다**(본문 §4.6-4). 동시에 같은 신규 태그를 쓰는 것은 정상 흐름이지 사용자 오류가 아니다.
- 요청의 태그 목록이 최종 상태다. 빠진 태그는 연결이 끊긴다. 요청에서 `tags`를 생략하면 기존 연결을 유지한다(여행 `files` 정책과 동일한 규칙).
- 태그 개수 상한: 임장 10개, 매물 10개(`TravelLogic.MAX_TAG_COUNT`와 동일 기준). 초과 시 `400`.
- 어디에도 연결되지 않은 태그는 마스터에 남긴다. 자동완성 후보로 계속 쓰인다.

### 6.3 조회

- `GET /inspection-tags?keyword=한` — 자동완성용. `name LIKE 'keyword%'`, 사용 빈도 내림차순.
- 임장 목록 태그 필터는 `inspection_visit_tag` 조인으로 처리한다. 매물 검색[요구사항 §42]도 동일.

---

## 7. 사진 설계 [요구사항 §29~§32]

### 7.1 기존 `FileBox` 재사용

요구사항 §30·§31은 `InspectionVisitImage` / `ViewedPropertyImage` 테이블을 예시로 들지만, 저장 정보(`imageId`, `caption`, `sortOrder`, 소유자 참조)가 이미 있는 `FileBoxItem`과 완전히 같다. §29가 "기존 서비스에서 사용 중인 이미지 저장 API 및 이미지 저장 구조를 재사용한다"고 명시하므로 **신규 테이블을 만들지 않는다.**

```text
FileBox
  ownerType = INSPECTION_VISIT
  ownerId   = {visitId}
  items[]
    ├─ { targetType: INSPECTION_VISIT, targetId: null,        role: COVER|GALLERY, caption, sortOrder }
    └─ { targetType: VIEWED_PROPERTY,  targetId: {propertyId}, role: COVER|GALLERY, caption, sortOrder }
```

임장 1건당 FileBox 문서 1건이며, 매물 사진도 같은 문서 안에 `targetId`로 구분해 담는다. 임장 삭제 시 문서 하나를 지우면 하위 사진 연결이 전부 정리된다[요구사항 §34.2].

### 7.2 규칙

- 파일 타입은 `inspection`이어야 한다. 다르면 `400`.
- `role`은 `COVER`(대상별 0 또는 1건)와 `GALLERY`(N건)만 허용한다.
- **`COVER`를 필수로 두지 않는다.** 사진 없는 임장 기록이 가능해야 한다(여행과 다른 점).
- `sortOrder`가 0 이하이면 자동 채번한다[요구사항 §32: ID/생성일 정렬 금지]. 단 **기존 `id`를 가진 항목은 기존 `sortOrder`를 유지**하고, `id`가 없는 신규 항목만 그룹(`targetType`+`targetId`+`role`) 말미에 채번한다. 이 단서가 없으면 전용 정렬 엔드포인트(본문 §10.10)로 방금 맞춘 순서를, 같은 화면의 저장 버튼이 캐시된 `files` 배열을 되보내면서 조용히 덮어쓴다.
- 같은 `targetType`/`targetId`/`role`/`fileAssetId` 조합은 중복될 수 없다.
- `targetType=VIEWED_PROPERTY`인 항목의 `targetId`는 **해당 임장에 실제로 존재하는 매물 ID**여야 한다. 아니면 `400`.
- 매물 삭제 시 그 매물의 `targetId`를 가진 항목만 FileBox에서 제거한다[요구사항 §34.3].

### 7.3 부분 동기화 규칙

임장과 매물이 각각 다른 화면에서 편집되므로, 파일 동기화는 **대상 그룹 단위 치환**으로 동작한다.

| 요청 | 동작 |
| --- | --- |
| 임장 `POST`/`PUT`의 `files` | `targetType=INSPECTION_VISIT` 항목만 요청 내용으로 치환. 매물 항목은 건드리지 않는다 |
| 매물 `POST`/`PUT`의 `files` | `targetType=VIEWED_PROPERTY AND targetId={해당 매물}` 항목만 치환. 다른 매물과 임장 항목은 유지 |
| `files` 생략 | 해당 그룹의 기존 연결 유지 |

요청 본문의 `targetType`/`targetId`는 서버가 경로에서 확정해 채운다. FE가 잘못 보낼 여지를 없앤다.

### 7.4 이 결정이 요구하는 공통 변경

- `FileType`에 `INSPECTION("inspection")` 추가
- `FileConstant.AVAILABLE_PATH`에 `inspection` 추가 — **빠뜨리면 업로드가 `FilePathInvalidException`으로 떨어진다**
- `FileBoxOwnerType`에 `INSPECTION_VISIT` 추가
- `FileBoxTargetType`에 `INSPECTION_VISIT`, `VIEWED_PROPERTY` 추가

`FileBoxItem`에 필드를 추가하지 않는다. 요구사항에 사진 카테고리 개념이 없으므로, 이전 제안(`01-spec-design.md` §9 C안)의 `category` 필드는 필요 없다. 공유 계약 변경을 그만큼 줄인다.

이 네 항목은 오브젝트 스토리지 전환(`feat/object-storage-migration` 머지) 이후에도 **그대로 유효하다.** 전환은 파일 바이트의 저장·조회 경로만 바꿨고 `FileBox`·`FileType`·`AVAILABLE_PATH`의 역할은 바뀌지 않았다. 근거는 부록 C.

### 7.5 오브젝트 스토리지 전환이 임장 사진에 주는 영향

파일 저장이 로컬 디스크에서 오브젝트 스토리지(Cloudflare R2)로 옮겨가면서 생긴 제약 중 **임장 기능에서 실제로 걸리는 것**은 아래 셋이다. 설계 결정은 바뀌지 않지만 API 사용 방식에 영향을 준다.

#### (1) 다중 업로드 요청 총량 60 MB — 임장에서 가장 먼저 걸린다

`spring.servlet.multipart`는 파일당 10 MB, **요청 전체 60 MB**다. `POST /api/assets/files`로 한 번에 올릴 수 있는 사진이 사실상 **6장**이라는 뜻이다.

임장은 이 상한에 여행보다 훨씬 쉽게 닿는다. 요구사항 §30의 임장 사진 예시만 8종(거리·상권·지하철역·공원·도로·학교·주변 환경·단지 외부)이고, 여기에 매물마다 §31의 7종(거실·주방·방·화장실·수납·창밖 뷰·하자)이 더해진다. 매물 3건이면 한 임장에 30장 규모다.

FE는 **6장 단위로 나눠 업로드**하고, 받은 `fileAssetId`를 모아 임장/매물 저장 요청의 `files`에 담는다. 한 번에 다 올리면 `413 PAYLOAD_TOO_LARGE`가 난다. 이 제약을 FE 연동 문서(PR 9)에 반드시 적는다.

#### (2) 원본은 302 리다이렉트 — 목록·상세는 `variant`를 반드시 지정한다

`GET /api/assets/files/{fileId}`는 이제 두 갈래로 갈린다.

| 요청 | 응답 | 캐시 |
| --- | --- | --- |
| `variant`/`width` 지정 (축소본) | 서버가 바이트 응답 | ETag + `private, max-age=86400` |
| 파라미터 없음 (원본) | 서명 URL로 `302` | `no-store`, ETag 없음 |

서명 URL은 서명할 때마다 값이 달라지고 `no-store`가 붙으므로 **캐시가 전혀 먹지 않는다.** 사진 30장짜리 임장 상세를 원본으로 그리면 재방문마다 30번을 새로 받는다.

따라서 임장 목록의 `cover`는 `home-thumb`(320px), 상세의 `photos`는 `home-feature`(960px)로 요청한다. 원본은 사용자가 사진을 확대하거나 내려받을 때만 쓴다. 서버 응답 구조는 바뀌지 않는다 — `FileBoxItemRdo.file`(`FileAssetRdo`)이 그대로 내려가고 variant 선택은 FE가 URL 파라미터로 한다.

#### (3) 파생본 생성이 업로드 요청 안에서 동기로 돈다

`FileLogic.uploadFileAsset`은 업로드마다 `writeVariants`로 WebP 파생본 2종(960/320)을 만든 뒤 원본과 파생본을 순차로 오브젝트 스토리지에 올린다. 6장짜리 요청 하나가 **인코딩 12회 + 오브젝트 업로드 18회**를 동기로 수행한다.

이건 여행도 같은 경로라 신규 결함은 아니지만, 임장은 사진 수가 많아 체감이 다르다. FE는 업로드 진행률을 노출하고 청크를 순차로 보내는 편이 낫다. 서버 측 비동기화는 이 기능의 범위 밖이며, 필요해지면 파일 도메인 공통 과제로 다룬다.

#### (4) 신규 파일 타입에 사전 준비가 필요 없다

`FileType.INSPECTION("inspection")`을 추가하면 키가 `originals/inspection/{uuid}.{ext}`, `derived/inspection/{uuid}_{variant}.{ext}`로 자동으로 갈린다. `R2ObjectStorage.put`은 S3 키를 그대로 쓰고 `LocalFileObjectStorage.put`은 `Files.createDirectories`로 상위 경로를 만든다. **버킷 폴더나 로컬 디렉터리를 미리 만들 필요가 없다.**

백필(`slcn.storage.migration.enabled`)은 `file_asset` 문서를 순회해 기존 로컬 파일을 옮기는 1회성 작업이라 임장과 무관하다. 임장에는 레거시 파일이 없다.

---

## 8. 영속성 설계

스키마: `slcn`. 모든 enum 컬럼에 **`@Enumerated(EnumType.STRING)`을 반드시 명시한다.** 빠뜨리면 ordinal 정수로 굳어 되돌리기 어렵다.

### 8.1 `inspection_area`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | `INSPECTION_AREA-0001` |
| `name` | varchar(100) | not null. 지역명 |
| `description` | varchar(300) | nullable. 지역 설명 |
| `hidden` | boolean | 기본 false |
| `registered_time` / `modified_time` | bigint | |

인덱스: `uk_inspection_area_name (name)` — **유니크**, `idx_inspection_area_hidden_name (hidden, name)`

유니크 제약이 없으면 지역 중복 검사가 앱 레벨 check-then-act로만 남아 동시 요청에 뚫린다(본문 §4.6-1). 태그·질문 버전과 같은 방식으로 DB가 막게 한다. 판정 기준을 `name + description`으로 바꾸기로 하면(본문 §16.2 결정 항목 9) 유니크 대상도 두 컬럼으로 함께 바꾼다.

### 8.2 `inspection_visit`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | `INSPECTION_VISIT-0001` |
| `area_id` | varchar | not null |
| `visited_at` | timestamp | not null |
| `memo` | text | |
| `revisit_intent` | varchar | `@Enumerated(STRING)`, nullable |
| `one_line_review` | varchar(300) | |
| `pros` / `cons` | text | |
| `status` | varchar | `@Enumerated(STRING)`, not null |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_visit_area_visited (area_id, visited_at)`, `idx_inspection_visit_visited (visited_at)`, `idx_inspection_visit_status (status)`

`idx_inspection_visit_visited`는 요구사항 §42의 "임장 기간" 검색과 목록 기본 정렬을 함께 받는다.

### 8.3 `viewed_property`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | UUID |
| `inspection_visit_id` | varchar | not null |
| `complex_name` | varchar(200) | **not null**. 단지/건물명 |
| `name` | varchar(200) | not null. 매물명 |
| `memo` | text | |
| `one_line_review` | varchar(300) | |
| `pros` / `cons` | text | |
| `interest_level` | int | nullable, 1~5 |
| `status` | varchar | `@Enumerated(STRING)`, not null |
| `sort_order` | int | |
| `answers` | text | **JSON. `PropertyAnswerListConverter`.** 문답 스냅샷 + 값(본문 §3.6) |
| `required_answer_count` | int | not null, 기본 0. 파생값(본문 §3.6.1) |
| `unanswered_required_count` | int | not null, 기본 0. 파생값 |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_viewed_property_visit (inspection_visit_id, sort_order)`, `idx_viewed_property_interest (interest_level)`, `idx_viewed_property_complex (complex_name)`

`idx_viewed_property_complex`는 요구사항 §42의 단지/건물명 검색용이다. **유니크가 아니다** — 같은 단지의 여러 매물이 정상이다[요구사항 §49-23].

미완료 집계용 인덱스는 따로 두지 않는다. 집계가 항상 `inspection_visit_id IN (...)`로 시작해 `idx_viewed_property_visit`를 타고, 그 뒤는 정수 컬럼 `sum()`이라 별도 인덱스가 도움이 되지 않는다.

**`answers`를 읽지 않는 조회는 projection을 쓴다.** JPA 엔티티를 그대로 로드하면 TEXT 컬럼이 항상 따라온다(`@Basic(fetch = LAZY)`는 바이트코드 강화 없이는 동작하지 않는다). 목록·집계 경로는 `ViewedPropertySummaryPdo`로 필요한 컬럼만 선택한다(본문 §3.6.2, §11.1).

### 8.4 `inspection_question`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | `INSPECTION_QUESTION-0001` |
| `answer_type` | varchar | `@Enumerated(STRING)`, 생성 후 변경 불가 |
| `required` | boolean | |
| `sort_order` | int | |
| `enabled` | boolean | |
| `versions` | text | **JSON. `QuestionVersionListConverter`.** 버전 이력 전체(본문 §3.5) |
| `current_version_no` | int | `versions`의 마지막 번호 |
| `registered_time` / `modified_time` | bigint | |

인덱스: `idx_inspection_question_enabled_sort (enabled, sort_order)`

`current_version_no`는 `versions`에서 유도할 수 있지만 컬럼으로 둔다. 매물 생성 시 활성 질문 목록을 읽을 때 JSON을 파싱하기 전에 버전 번호가 필요하고, `isCurrentVersion` 배지 계산(본문 §10.8)이 이 값만 쓴다.

**질문 버전용 테이블과 `uk_inspection_question_version` 유니크 제약은 없다.** 버전 번호 채번은 같은 행 안에서 일어나고 `@Version`이 동시 채번을 막는다(본문 §4.6).

### 8.5 태그 테이블

`inspection_tag`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | UUID |
| `name` | varchar(50) | not null, 유니크 |
| `registered_time` / `modified_time` | bigint | |

인덱스: `uk_inspection_tag_name (name)` — 유니크

`inspection_visit_tag`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | UUID |
| `inspection_visit_id` | varchar | not null |
| `tag_id` | varchar | not null |

인덱스: `uk_inspection_visit_tag (inspection_visit_id, tag_id)` 유니크, `idx_inspection_visit_tag_tag (tag_id)`

`viewed_property_tag`

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `id` | varchar | UUID |
| `viewed_property_id` | varchar | not null |
| `inspection_visit_id` | varchar | not null, **비정규화** |
| `tag_id` | varchar | not null |

인덱스: `uk_viewed_property_tag (viewed_property_id, tag_id)` 유니크, `idx_viewed_property_tag_tag (tag_id)`, `idx_viewed_property_tag_visit (inspection_visit_id)`

`inspection_visit_id`를 매물 태그 연결에도 두는 이유: 본문 §12.1의 임장 삭제가 `deleteByInspectionVisitId` 한 번으로 끝나야 하고, 본문 §11.2의 "태그 2회(임장/매물 일괄)" 조회도 매물 ID 목록을 먼저 구하는 왕복 없이 끝나야 한다. 이 컬럼이 없으면 둘 다 `viewed_property` 조인이나 서브쿼리가 된다. 태그 연결이 다른 임장으로 옮겨가는 시나리오가 없으므로 정합성 위험이 없다.

### 8.6 `ddl-auto=update` 주의

- 신규 테이블 **7개**는 자동 생성된다. 기존 테이블 변경은 없다.
- **유니크 인덱스는 `ddl-auto`가 만들어주지 않는 경우가 있다.** `uk_inspection_area_name`, `uk_inspection_tag_name`, 태그 연결 테이블 2개 — 총 **4건**의 유니크 제약이 실제로 생성되었는지 배포 후 `\d+`로 확인한다. 없으면 수동 `CREATE UNIQUE INDEX`를 건다. 하나라도 빠지면 본문 §4.6의 동시성 보호가 무력화된다.
- JSON을 싣는 컬럼(`viewed_property.answers`, `inspection_question.versions`)은 `@Column(columnDefinition = "TEXT")`를 명시한다. 빠뜨리면 `varchar(255)`로 생성되어 질문이 몇 개만 늘어도 저장이 실패한다. `TravelJpo.days`가 같은 방식이다.
- 외래키 제약은 걸지 않는다. 기존 도메인(`travel`, `trip`)도 참조 ID를 plain 컬럼으로 두고 애플리케이션에서 검증한다. 같은 방식을 유지한다.

### 8.7 `id_sequence` 시드

**행이 없으면 등록이 `ID NOT EXIST`로 실패한다.** 배포 전 반드시 실행한다.

```sql
INSERT INTO slcn.id_sequence (name, last_id) VALUES ('INSPECTION_AREA', '0000');
INSERT INTO slcn.id_sequence (name, last_id) VALUES ('INSPECTION_VISIT', '0000');
INSERT INTO slcn.id_sequence (name, last_id) VALUES ('INSPECTION_QUESTION', '0000');
```

`ViewedProperty`, `InspectionTag`와 태그 연결 행은 **UUID**를 쓴다. `PropertyAnswer`와 `QuestionVersion`은 VO라 행 ID 자체가 없다(본문 §3.6, §3.5). `id_sequence`는 상한이 `0xFFFF`(65,535)라 이들 규모를 감당하지 못한다.

---

## 9. 모듈 배치

`docs/module.md` 기준. 도메인 패키지명은 `inspection`으로 통일한다.

```text
slcn-spec/src/main/java/com/seoulchonnom/spec/inspection/
├── constant/
│   └── InspectionConstant.java                 # 에러 메시지 상수
├── entity/
│   ├── InspectionArea.java
│   ├── InspectionVisit.java
│   ├── ViewedProperty.java                     # answers 목록을 품는다
│   ├── InspectionQuestion.java                 # versions 목록을 품는다
│   ├── InspectionTag.java
│   └── vo/
│       ├── RevisitIntent.java
│       ├── InspectionStatus.java
│       ├── QuestionAnswerType.java
│       ├── QuestionChoice.java
│       ├── QuestionVersion.java                # VO (본문 §3.5)
│       └── PropertyAnswer.java                 # VO (본문 §3.6)
├── facade/
│   ├── InspectionAreaFacade.java
│   ├── InspectionVisitFacade.java
│   ├── ViewedPropertyFacade.java
│   ├── InspectionQuestionFacade.java
│   ├── InspectionTagFacade.java
│   └── sdo/                                    # Cdo / Udo / Rdo. 본문 §10.7 참조
└── mapper/
    ├── InspectionAreaMapper.java
    ├── InspectionVisitMapper.java
    ├── ViewedPropertyMapper.java
    ├── PropertyAnswerMapper.java
    ├── InspectionQuestionMapper.java
    └── InspectionTagMapper.java

slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/inspection/
├── exception/
│   ├── InspectionAreaNotFoundException.java
│   ├── InspectionAreaInUseException.java
│   ├── InspectionAreaDuplicatedException.java
│   ├── InspectionVisitNotFoundException.java
│   ├── InvalidInspectionVisitException.java
│   ├── ViewedPropertyNotFoundException.java
│   ├── InvalidViewedPropertyException.java
│   ├── InspectionQuestionNotFoundException.java
│   ├── InvalidInspectionQuestionException.java
│   ├── InspectionQuestionConflictException.java
│   ├── InspectionAnswerRequiredException.java
│   └── InvalidPropertyAnswerException.java
├── logic/
│   ├── InspectionAreaLogic.java
│   ├── InspectionVisitLogic.java
│   ├── ViewedPropertyLogic.java                # 답변 값 검증·파생 카운트 갱신 포함
│   ├── InspectionQuestionLogic.java            # 버전 추가 규칙(기존 버전 불변) 강제
│   └── InspectionTagLogic.java
└── store/
    ├── InspectionAreaStore.java
    ├── InspectionVisitStore.java
    ├── ViewedPropertyStore.java
    ├── InspectionQuestionStore.java
    ├── InspectionTagStore.java
    ├── jpo/
    │   ├── InspectionAreaJpo.java
    │   ├── InspectionVisitJpo.java
    │   ├── ViewedPropertyJpo.java
    │   ├── InspectionQuestionJpo.java
    │   ├── InspectionTagJpo.java
    │   ├── InspectionVisitTagJpo.java
    │   ├── ViewedPropertyTagJpo.java
    │   └── converter/
    │       ├── PropertyAnswerListConverter.java    # viewed_property.answers
    │       └── QuestionVersionListConverter.java   # inspection_question.versions
    ├── mapper/                                 # *JpoMapper
    ├── projection/
    │   └── ViewedPropertySummaryPdo.java       # answers를 읽지 않는다(본문 §3.6.2)
    └── repository/                             # *Repository

slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/flow/inspection/
├── InspectionVisitFlow.java                    # 임장 등록/수정/상태 전이
├── ViewedPropertyFlow.java                     # 매물 등록/수정/상태 전이 + 문답 스냅샷 생성
├── InspectionVisitQueryFlow.java               # 임장 목록/상세 조합
├── InspectionAreaQueryFlow.java                # 지역 목록/복합 상세 조합 (본문 §11.0, §10.1)
├── InspectionQuestionQueryFlow.java            # 질문 조회 + answerCount (본문 §11.5)
├── InspectionPhotoSupport.java                 # FileBox 부분 동기화 공통 (본문 §7.3)
└── InspectionSummarySupport.java               # 미완료 요약 생성 공통 (본문 §10.9)

slcn-rest/src/main/java/com/seoulchonnom/rest/inspection/
├── InspectionAreaResource.java
├── InspectionVisitResource.java
├── ViewedPropertyResource.java
├── InspectionQuestionResource.java
└── InspectionTagResource.java
```

Store가 7종에서 **5종**으로, JPO가 10종에서 **7종**으로 줄었다. `PropertyAnswerStore`/`PropertyAnswerLogic`과 `InspectionQuestionVersionStore`가 없어진 자리를, 부모 엔티티의 Logic이 리스트를 다루는 코드로 흡수한다.

`QuestionChoice` 전용 컨버터는 두지 않는다. 단독 컬럼에 실리는 일이 없고, 위 두 컨버터가 `JsonUtil`로 통째로 직렬화할 때 중첩 객체로 함께 처리된다. `TravelDayListConverter`가 `TravelPlace`를 다루는 방식과 같다.

### 9.1 `StringListConverter` 처리

`StringListConverter`는 내용이 도메인 중립인데 지금 `aggregate.travel.store.jpo.converter`에 있다. `PropertyAnswer.selectedCodes`에 그대로 필요하다. 두 가지 선택지가 있다.

| 안 | 방식 | 평가 |
| --- | --- | --- |
| A | `aggregate/common/store/converter/`로 옮기고 `TravelJpo` 참조 수정 | 올바른 위치. 다만 travel JPO와 그 테스트를 함께 고쳐야 하고, 이 PR의 diff에 travel이 섞인다 |
| B | inspection 패키지에 동일 구현을 하나 더 둔다 | 중복 13줄. travel을 건드리지 않는다 |

**A안을 권장한다.** 이동은 컴파일 단위 변경이라 런타임 동작에 영향이 없고, 여기서 미루면 세 번째 도메인에서 같은 판단을 다시 하게 된다. 단, 공통화 이동은 **PR 1(공통 변경)에 포함**시켜 도메인 PR의 diff에 섞이지 않게 한다.

`InspectionQuestionQueryFlow`와 `InspectionSummarySupport`는 초기 트리에 없던 것이다. 전자는 질문과 매물이라는 **두 aggregate를 가로지르는 집계**(본문 §11.5)라 어느 한쪽 Logic에 넣으면 도메인 경계가 무너진다. 후자는 미완료 요약을 임장 목록·임장 상세·지역 목록·매물 상세 네 곳에서 같은 기준으로 만들어야 하는데, 구현이 갈라지면 본문 §10.9가 경고한 "0개 남았는데 완료가 안 되는" 상태가 생긴다.

`InspectionVisitSummaryPdo`는 만들지 않았다. `inspection_visit`에는 `answers` 같은 대형 JSON 컬럼이 없어 엔티티를 그대로 읽어도 목록 비용이 크게 달라지지 않는다. projection의 존재 이유는 "읽지 말아야 할 컬럼이 있을 때"이고 `viewed_property`만 그에 해당한다.

### 9.2 Flow를 도입하는 이유

임장 등록 한 번이 `지역 조회/생성 + 임장 저장 + 태그 upsert·연결 + FileBox 동기화`를 묶는다. 매물 등록은 `매물 저장 + 활성 질문·현재 버전 조회 + 답변 행 생성 + 태그 + FileBox`를 묶는다. `docs/module.md` 기준으로 이런 복합 커맨드는 `Flow`가 맡는다.

`TravelLogic`은 이미 492줄이고 검증 헬퍼가 절반을 차지한다. 임장은 엔티티가 7종이라 같은 모양으로 가면 더 커진다. 신규 도메인은 처음부터 `Flow`로 시작한다. 단순 단건 조회(질문 목록, 태그 자동완성)는 `Resource -> Logic` 직접 호출을 유지한다.

---

## 10. API 계약

context path는 `/api`다. 아래 경로는 그 뒤에 붙는다.

### 10.1 임장 지역

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-areas` | 목록 + **회차 집계**. `?keyword=` 지역명·설명 부분 일치. 응답 필드는 아래 |
| GET | `/inspection-areas/{areaId}` | 상세. 회차 요약 목록 + 선택 회차 상세를 **한 번에** 반환. `?visitId=`, `?includeProperties=` |
| POST | `/inspection-areas` | 등록. 동일 지역명 존재 시 `409` + 기존 후보 |
| PUT | `/inspection-areas/{areaId}` | 수정. 기존 임장 기록은 영향 없음[요구사항 §6] |
| DELETE | `/inspection-areas/{areaId}` | 임장 기록이 1건이라도 있으면 `409 INSPECTION_AREA_IN_USE`[요구사항 §34.1] |

#### 지역 목록 응답 필드 (`InspectionAreaRdo`)

요구사항 명세서에는 지역 목록 화면 정의가 없다(§38은 **임장** 목록이다). 아래는 FE 화면 설계에서 온 요구이며, 전부 **저장하지 않고 조회 시 집계**한다(본문 §3.2 원칙 유지).

| 필드 | 집계 정의 |
| --- | --- |
| `visitCount` | 이 지역의 `inspection_visit` 건수 |
| `firstVisitedAt` / `lastVisitedAt` | `min(visited_at)` / `max(visited_at)` |
| `totalPropertyCount` | 이 지역의 모든 회차에 속한 `viewed_property` 건수 |
| `latestVisit` | `{ visitId, visitedAt, oneLineReview, revisitIntent, status, tags[] }` — `visited_at` 최신 1건 |
| `topProperty` | `{ propertyId, complexName, name, interestLevel }` — 지역 전체에서 `interestLevel` 최대 1건 |
| `incompleteSummary` | 본문 §10.9의 `IncompleteSummaryRdo`. 지역 목록에서는 **개수 필드만** 싣는다 |
| `thumbnails` | 대표 사진 최대 2건. 최신 회차의 `COVER` → 없으면 `sortOrder` 앞선 `GALLERY` 순 |
| `totalImageCount` | 이 지역 모든 회차 FileBox의 `items` 총 개수 |

`topProperty`가 **회차가 아니라 지역 전체 기준**인 점이 임장 목록의 동명 필드와 다르다. 임장 목록의 `topInterestProperty`는 그 회차 안에서만 고른다.

#### 지역 상세 복합 응답 (`InspectionAreaDetailRdo`)

FE가 회차 탭을 즉시 전환할 수 있도록 한 번의 호출로 아래를 함께 준다.

```text
area                      지역 기본 정보
visits[]                  회차 요약 (visitedAt 내림차순, 전 회차)
                          { visitId, visitedAt, oneLineReview, revisitIntent, status,
                            tags[], propertyCount, incompleteSummary, cover }
selectedVisit             회차 상세 1건. 임장 상세 응답과 동일 형태
                          (매물 + 문답 + 사진 포함)
```

- `visitId` 파라미터가 없으면 **최신 회차**를 `selectedVisit`으로 펼친다.
- `includeProperties=false`면 `selectedVisit`을 생략하고 `visits[]`만 준다. 회차가 많은 지역에서 FE가 지연 로딩으로 전환할 때 쓴다.
- `visits[]`는 전 회차를 주되 **50건을 넘으면 최신 50건으로 자른다.** 넘친 경우 `hasMoreVisits: true`를 함께 내려 FE가 `GET /inspection-visits?areaId=`로 이어받게 한다.

`selectedVisit`의 형태를 `GET /inspection-visits/{visitId}` 응답과 **동일한 타입으로 맞춘다.** 형태가 갈라지면 매퍼가 둘로 늘고 회차 지연 로딩 시 FE가 두 모양을 다뤄야 한다.

### 10.2 임장 기록

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-visits` | 목록. `visited_at` 내림차순. 필터: `areaId`, `status`, `revisitIntent`, `tag`(복수), `from`, `to` |
| GET | `/inspection-visits/{visitId}` | 상세[요구사항 §39]. 매물·문답·사진 포함 |
| POST | `/inspection-visits` | 등록. `areaId` 또는 인라인 `area` 중 하나 필수. 상태 `DRAFT`로 생성 |
| PUT | `/inspection-visits/{visitId}` | 기본 정보·태그·사진 수정 |
| PATCH | `/inspection-visits/{visitId}/status` | `COMPLETED` / `DRAFT` 전이(본문 §4.3) |
| PUT | `/inspection-visits/{visitId}/properties/order` | 매물 정렬 순서 일괄 변경[요구사항 §33] |
| PUT | `/inspection-visits/{visitId}/images/order` | 사진 정렬 순서 일괄 변경[요구사항 §32]. 임장·매물 사진 공용 |
| DELETE | `/inspection-visits/{visitId}` | 하위 매물·문답·태그 연결·FileBox 함께 삭제[요구사항 §34.2] |

### 10.3 확인 매물

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| POST | `/inspection-visits/{visitId}/properties` | 등록. `DRAFT` + 활성 질문 스냅샷 생성(본문 §5.1) |
| GET | `/inspection-visits/{visitId}/properties/{propertyId}` | 상세[요구사항 §40] |
| GET | `/inspection-visits/{visitId}/complex-names` | 이 임장에 이미 등록된 단지/건물명 목록. 입력 자동완성용(본문 §3.4.2) |
| PUT | `/inspection-visits/{visitId}/properties/{propertyId}` | 기본 정보·관심도·태그·사진 수정 |
| PUT | `/inspection-visits/{visitId}/properties/{propertyId}/answers` | 문답 일괄 저장. 값 컬럼만 갱신 |
| PATCH | `/inspection-visits/{visitId}/properties/{propertyId}/status` | `COMPLETED` / `DRAFT` 전이(본문 §4.2) |
| DELETE | `/inspection-visits/{visitId}/properties/{propertyId}` | 문답·태그 연결·사진 연결 함께 삭제[요구사항 §34.3] |

### 10.4 질문 관리

| 메서드 | 경로 | 권한 | 설명 |
| --- | --- | --- | --- |
| GET | `/inspection-questions` | `USER` | 기본은 `enabled=true`만. `?includeDisabled=true`로 전체. `?withAnswerCount=true`로 답변 건수 동봉 |
| GET | `/inspection-questions/{questionId}/versions` | `USER` | 버전 이력. 버전별 `answerCount` 포함 |
| POST | `/inspection-questions` | **`ADMIN`** | 등록. `answerType` 확정, v1 생성 |
| PUT | `/inspection-questions/{questionId}` | **`ADMIN`** | 문구·설명·선택지·단위 수정 → **새 버전 생성** |
| PATCH | `/inspection-questions/{questionId}/policy` | **`ADMIN`** | `required`, `sortOrder` 변경. 버전 유지 |
| PATCH | `/inspection-questions/{questionId}/status` | **`ADMIN`** | `enabled` 토글. 버전 유지 |
| PUT | `/inspection-questions/order` | **`ADMIN`** | 순서 일괄 변경[요구사항 §23] |

물리 삭제 API는 만들지 않는다(본문 §3.5).

#### 권한 분리 규칙

요구사항 §18이 질문 관리를 "관리자" 기능으로 규정한다. **쓰기만 `ADMIN`이고 조회는 `USER`로 남긴다.** 조회까지 막으면 일반 사용자가 매물 문답을 작성할 수 없다 — 매물 생성 시 활성 질문 목록을 읽어야 한다(본문 §5.1).

`SecurityConfiguration`의 기본 체인에 메서드별 matcher를 추가한다. `.anyRequest().hasAuthority(USER_AUTHORITY)`보다 **앞에** 놓아야 적용된다.

```java
// SecurityConfiguration에 import org.springframework.http.HttpMethod; 를 함께 추가한다
.requestMatchers(HttpMethod.POST,  "/inspection-questions", "/inspection-questions/**").hasAuthority(ADMIN_AUTHORITY)
.requestMatchers(HttpMethod.PUT,   "/inspection-questions", "/inspection-questions/**").hasAuthority(ADMIN_AUTHORITY)
.requestMatchers(HttpMethod.PATCH, "/inspection-questions", "/inspection-questions/**").hasAuthority(ADMIN_AUTHORITY)
```

**패턴을 두 개씩 쓴다.** `POST /inspection-questions`는 하위 경로가 없어 `/**` 패턴에 의존하면 매처 구현에 따라 빠질 수 있다. 하나라도 새면 ADMIN 전용 API가 `USER`에게 열리므로 명시한다. `SecurityConfigurationTest`가 `USER` 토큰으로 네 경로를 찔러 이 동작을 고정한다.

`/users/register`가 이미 `.anyRequest()`보다 앞에서 `hasAuthority(ADMIN_AUTHORITY)`를 쓰고 있으므로(`SecurityConfiguration.java:80-81`) 같은 패턴을 그대로 따른다.

**운영상 전제 두 가지를 먼저 확인해야 한다.**

1. `ADMIN` 권한을 부여하는 API가 없다. `UserMapper.defaultAuthorityList()`가 신규 가입자에게 `USER` 하나만 준다. 관리자 계정은 `authority` 테이블에 `ADMIN` 행을 **직접 INSERT**해야 한다. `/users/register`가 이미 `ADMIN` 전용이므로 운영 DB에 해당 행이 이미 있을 가능성이 높지만, 배포 전에 실제로 확인한다.
2. `Authority`는 목록이고 권한 문자열은 서로 포함 관계가 없다. 관리자 계정이 `ADMIN`만 가지고 있으면 `.anyRequest().hasAuthority(USER_AUTHORITY)`에 걸려 나머지 API를 전부 쓸 수 없다. **관리자 계정은 `USER`와 `ADMIN`을 함께 보유해야 한다.**

### 10.5 태그

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/inspection-tags` | 자동완성. `?keyword=`, 사용 빈도 내림차순 |

태그 생성/삭제 API는 두지 않는다. 임장·매물 저장 시 이름 기반 get-or-create로 처리한다(본문 §6.2).

### 10.6 요청/응답 예시

**임장 등록**

```json
POST /api/inspection-visits
{
  "areaId": null,
  "area": { "name": "성수동", "description": "서울숲 ~ 뚝섬역 주변" },
  "visitedAt": "2026-09-17T14:00:00",
  "memo": "서울숲 접근성 좋음. 뚝섬역까지 도보 10분",
  "revisitIntent": "YES",
  "oneLineReview": "직주근접과 분위기는 좋지만 가격이 부담됨",
  "pros": "- 서울숲 접근성이 좋음\n- 강남 이동이 편함\n- 한강 접근성이 좋음",
  "cons": "- 가격대가 높음\n- 저녁 시간 교통 혼잡",
  "tags": ["서울숲", "한강", "직주근접"],
  "files": [
    { "fileAssetId": "file-1", "role": "COVER", "caption": "서울숲 입구" },
    { "fileAssetId": "file-2", "role": "GALLERY", "caption": "뚝섬역 상권", "sortOrder": 1 }
  ]
}
```

`files`의 `targetType`/`targetId`는 서버가 `INSPECTION_VISIT`/`null`로 확정한다(본문 §7.3).

**매물 등록 응답 (문답 스냅샷 포함)**

```json
POST /api/inspection-visits/INSPECTION_VISIT-0001/properties
{ "complexName": "트리마제", "name": "101동 1203호 / 84A" }
→ 200
{
  "propertyId": "8a71c0e2-...",
  "inspectionVisitId": "INSPECTION_VISIT-0001",
  "complexName": "트리마제",
  "name": "101동 1203호 / 84A",
  "interestLevel": null,
  "status": "DRAFT",
  "sortOrder": 1,
  "tags": [],
  "photos": [],
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
      "textValue": null
    },
    {
      "questionId": "INSPECTION_QUESTION-0004",
      "questionVersionNo": 1,
      "question": "방향은?",
      "answerType": "SINGLE_SELECT",
      "required": false,
      "sortOrder": 2,
      "answered": false,
      "choiceOptions": [
        { "code": "SOUTH", "label": "남향" },
        { "code": "EAST", "label": "동향" }
      ],
      "selectedCodes": []
    }
  ]
}
```

**문답 저장**

```json
PUT /api/inspection-visits/INSPECTION_VISIT-0001/properties/8a71c0e2-.../answers
{
  "answers": [
    { "questionId": "INSPECTION_QUESTION-0001", "textValue": "오후에도 상당히 밝았다." },
    { "questionId": "INSPECTION_QUESTION-0004", "selectedCodes": ["SOUTH"] }
  ]
}
```

요청에 없는 `questionId`의 답변 행은 그대로 둔다(부분 저장 허용, 요구사항 §10.1).

**임장 완료 실패 응답**

```json
PATCH /api/inspection-visits/INSPECTION_VISIT-0001/status  { "status": "COMPLETED" }
→ 400
{
  "success": false,
  "message": "필수 조건을 만족하지 않은 매물이 있습니다. propertyIds=[8a71c0e2-..., 9b02f1a3-...]"
}
```

**임장 목록 응답 [요구사항 §38]**

```json
GET /api/inspection-visits
[
  {
    "inspectionVisitId": "INSPECTION_VISIT-0001",
    "area": { "areaId": "INSPECTION_AREA-0001", "name": "성수동" },
    "visitedAt": "2026-09-17T14:00:00",
    "oneLineReview": "직주근접과 분위기는 좋지만 가격이 부담됨",
    "revisitIntent": "YES",
    "status": "COMPLETED",
    "propertyCount": 3,
    "topInterestProperty": {
      "propertyId": "8a71...",
      "complexName": "트리마제",
      "name": "101동 1203호 / 84A",
      "interestLevel": 5
    },
    "tags": ["서울숲", "한강", "직주근접"],
    "cover": { "fileAssetId": "file-1", "file": { "...": "FileAssetRdo" } }
  }
]
```

`topInterestProperty`에 `complexName`을 함께 담는다. 요구사항 §38 예시가 "트리마제 / 101동 1203호"로 단지명을 앞세운다.

### 10.7 DTO 목록 (`spec/inspection/facade/sdo/`)

| 클래스 | 용도 |
| --- | --- |
| `InspectionAreaCdo`, `InspectionAreaUdo`, `InspectionAreaRdo`, `InspectionAreaDetailRdo` | 지역 |
| `InspectionVisitCdo`, `InspectionVisitUdo`, `InspectionVisitRdo`, `InspectionVisitDetailRdo` | 임장 |
| `InspectionVisitStatusUdo` | 상태 전이 |
| `ViewedPropertyCdo`, `ViewedPropertyUdo`, `ViewedPropertyRdo`, `ViewedPropertyDetailRdo` | 매물 |
| `ViewedPropertyStatusUdo`, `ViewedPropertyOrderUdo` | 상태 전이 / 정렬 |
| `PropertyAnswerUdo`, `PropertyAnswerBulkUdo`, `PropertyAnswerRdo` | 문답 |
| `InspectionQuestionCdo`, `InspectionQuestionContentUdo`, `InspectionQuestionPolicyUdo`, `InspectionQuestionStatusUdo`, `InspectionQuestionOrderUdo`, `InspectionQuestionRdo`, `InspectionQuestionVersionRdo` | 질문 |
| `InspectionTagRdo` | 태그 |
| `QuestionChoiceSdo` | 선택지 입력 |
| `IncompleteSummaryRdo` | 미완료 요약(본문 §10.9) |
| `FileBoxItemOrderUdo` | 사진 정렬 일괄 갱신(본문 §10.10) |

`docs/learning/domain-entity-must-not-depend-on-api-dto.md` 규칙에 따라 **엔티티와 VO는 `facade.sdo` 패키지를 절대 import하지 않는다.** 변환은 전부 `spec/inspection/mapper/`가 담당한다.

#### 부분 수정(PUT) 의미론 — 도메인 전체에 하나의 규칙

9개 PR을 여러 번에 걸쳐 구현하므로, `Udo`마다 "필드를 생략하면 어떻게 되는가"가 달라지면 리그레션이 난다(`interestLevel`을 빼고 `memo`만 고쳤는데 관심도가 사라지는 형태). **아래 두 줄이 모든 `Udo`에 동일하게 적용된다.**

| 필드 종류 | 생략 시 | 이유 |
| --- | --- | --- |
| 스칼라 (`memo`, `pros`, `cons`, `oneLineReview`, `interestLevel`, `revisitIntent`, `description` …) | **null로 덮어쓴다.** PUT은 전체 교체다 | Jackson POJO는 "필드 없음"과 "명시적 null"을 구별하지 못한다. 구별하려면 `JsonNullable`류 래퍼가 필요한데 이 저장소에 전례가 없다. 화면이 전체 폼 저장이므로 전체 교체가 자연스럽다 |
| 컬렉션 (`tags`, `files`) | **기존 값을 유지한다** | 각각 별도 위젯·별도 엔드포인트로 편집되고, `files`는 payload가 크다. 여행의 `files` 정책(`TravelLogic.resolveModifyItems`)과 같다 |

**주의: `TravelLogic`의 `tags`는 이 규칙과 반대로 동작한다.** `normalizeTags(null)`이 빈 리스트를 반환해 태그를 **지운다**(`TravelLogic.java:133`). 여행 코드를 참고해 임장 태그 로직을 짜면 그대로 옮겨온다. 임장은 `tags == null`이면 기존 연결을 그대로 두는 분기를 명시적으로 넣는다.

### 10.8 문답 응답의 질문 메타 (`PropertyAnswerRdo`)

과거 기록을 그때 그대로 복원하려면[요구사항 §20] 답변 응답이 **질문 마스터를 조회하지 않고도** 렌더링 가능해야 한다. 아래 필드는 전부 답변 행의 스냅샷에서 온다.

| 필드 | 출처 | 용도 |
| --- | --- | --- |
| `question` | `questionContent` 스냅샷 | 문구 렌더링 |
| `description` | `questionDescription` 스냅샷 | 도움말 |
| `questionVersionNo` | 스냅샷 | "몇 번 문구에 답했는가" |
| `answerType` | 스냅샷 | 입력 위젯 결정 |
| `required` | 스냅샷 | 필수 표시 + 완료 검증 |
| `sortOrder` | 스냅샷 | 표시 순서 |
| `choiceOptions` | 스냅샷 | 선택지 렌더링 |
| `answered` | 저장된 파생값 | 미답변 표시 |

여기에 **현재 질문 마스터와 비교한 파생 필드 두 개**를 더한다. FE가 "옛 문구 기준 답변", "더 이상 쓰지 않는 질문" 배지를 그리기 위해 필요하다.

| 필드 | 계산 | 비고 |
| --- | --- | --- |
| `isCurrentVersion` | `answer.questionVersionNo == question.currentVersionNo` | 질문이 삭제되지 않으므로 항상 판정 가능 |
| `questionEnabled` | `question.enabled` | `false`면 "미사용 질문" 배지 |

**이 두 필드는 배지 전용이며 렌더링을 좌우하지 않는다.** 문구·타입·선택지는 어떤 경우에도 스냅샷을 쓴다. 나중에 누군가 "최신 문구를 보여주자"고 이 필드로 렌더링을 바꾸면 요구사항 §20이 깨진다. 이 제약을 `PropertyAnswerMapper`에 주석으로 남긴다.

계산에 질문 마스터 배치 조회 1회가 추가된다(본문 §11.2). 질문 수는 수십 건 규모라 `findAllByIds` 한 번이면 충분하다.

### 10.9 미완료 요약 (`IncompleteSummaryRdo`)

DRAFT 상태에서 "무엇이 남았는지"를 화면에 적기 위한 요약이다. 본문 §4.2(매물)·§4.3(임장)의 완료 검증과 **같은 기준**을 쓴다.

`IncompleteSummaryRdo`는 **매물 레벨과 임장 레벨 양쪽**을 담는다. 매물 필드만 담으면 "재방문 의사 미입력" 때문에 임장을 완료할 수 없는 상황(본문 §4.3-3)을 화면이 설명하지 못한다.

| 필드 | 레벨 | 정의 | 대응 검증 |
| --- | --- | --- | --- |
| `unansweredRequiredCount` | 매물 | 매물 행의 파생 컬럼(본문 §3.6.1) | §4.2-5 |
| `unansweredRequiredQuestions` | 매물 | `answers` 중 `required && !answered`인 항목의 `{ questionId, question, sortOrder }` | §4.2-5 |
| `missingFields` | 매물 | `complexName` / `name` / `interestLevel` 중 비어 있는 필드명 | §4.2-1~4 |
| `draftPropertyCount` | 임장 | 이 임장에서 `status = DRAFT`인 매물 수 | §4.3-4 |
| `visitMissingFields` | 임장 | `visitedAt` / `revisitIntent` 중 비어 있는 필드명 | §4.3-2, §4.3-3 |
| `draftVisitCount` | 지역 | 이 지역에서 `status = DRAFT`인 임장 수 | 지역 목록 전용 집계 |

각 행의 "대응 검증" 열이 본문 §4.2·§4.3의 조건 번호와 1:1로 맞는지가 이 설계의 핵심이다. **요약에 나타나지 않는 완료 조건이 하나라도 있으면 "남은 것 0개인데 완료 버튼이 안 먹는" 상태가 생긴다.**

**개수와 이름을 화면 단계별로 나눠 싣는다.**

| 응답 | 담는 것 | 이유 |
| --- | --- | --- |
| 지역 목록, 임장 목록 | 개수 필드만 (`unansweredRequiredCount`, `draftPropertyCount`, `draftVisitCount`) | 목록 N행마다 질문 문구를 끌어오면 집계 질의가 답변 본문 조회로 바뀐다 |
| 지역 상세의 `visits[]` | 개수만 | 위와 같음 |
| 임장 상세, 매물 상세 | 전 필드(이름 목록·`missingFields`·`visitMissingFields` 포함) | 이미 답변 행을 전부 로드한 상태라 추가 비용이 없다 |

목록용 개수 집계는 매물 행의 `unanswered_required_count` 정수 컬럼을 `sum()`으로 읽는다(본문 §3.6.1). `answers` JSON을 파싱하지 않으며, 임장/지역 단위 묶음도 `idx_viewed_property_visit`를 탄 단일 `GROUP BY`로 끝난다(본문 §8.3).

### 10.10 정렬 값 저장 [요구사항 §32, §33]

정렬은 전용 엔드포인트로 처리한다. 본문 §7.3의 "`files` 배열 전체 치환"으로 사진 순서를 바꾸면 사진 30장짜리 임장에서 순서 하나 바꾸는 데 배열 전체를 되돌려보내야 한다.

| 엔드포인트 | 요청 | 동작 |
| --- | --- | --- |
| `PUT /inspection-visits/{visitId}/properties/order` | `[{ propertyId, sortOrder }]` | `viewed_property.sort_order`만 갱신 |
| `PUT /inspection-visits/{visitId}/images/order` | `[{ itemId, sortOrder }]` | FileBox `items[].sortOrder`만 갱신 |

공통 규칙:

- 요청에 빠진 항목은 기존 `sortOrder`를 유지한다. 부분 갱신을 허용한다.
- 대상 ID가 해당 임장에 속하지 않으면 `400`.
- 사진 정렬의 `itemId`는 FileBox 문서 안에서 유일하므로 **임장 사진과 매물 사진을 한 엔드포인트가 함께 처리한다.** `targetType`을 요청에 받지 않는다 — 서버가 항목을 찾아 판정한다.
- `sortOrder` 중복은 허용한다. 중복을 `400`으로 막으면 FE가 드래그 한 번에 전체를 재계산해 보내야 한다. **대신 모든 조회의 정렬에 결정적 2차 키를 붙인다** — 매물은 `ORDER BY sort_order, registered_time, id`, 사진은 `sortOrder` 다음 `id`. 2차 키가 없으면 동률 행의 순서를 PostgreSQL이 보장하지 않아 사용자가 정렬을 바꾸지 않았는데도 새로고침마다 화면이 뒤바뀐다.
- 정렬 변경은 상태 전이를 유발하지 않는다. `COMPLETED` 임장에서도 허용한다(본문 §4.4의 자동 복귀 대상이 아니다).

---

## 11. 조회 성능과 화면 조립

### 11.0 지역 목록 화면 (FE 설계 요구)

지역 N건 각각에 대해 회차 전체를 가로지르는 집계가 필요하다(본문 §10.1). 지역마다 회차를 끌어오면 N × 회차 수만큼 매물·태그·FileBox를 읽게 된다. `InspectionAreaQueryFlow`에서 **지역 수와 무관하게 고정한다.**

아래 단계는 논리 단위이고, **실제 저장소 왕복은 7회**다. 태그 조회가 연결 테이블과 태그 마스터 2회로 갈라지고 사진에 `FileAsset` 조회가 1회 붙는다. 처음에는 이 둘을 세지 않아 "6회"로 적었는데, 세는 기준을 저장소 왕복으로 통일했다.

```text
1) InspectionAreaStore.findPage(조건)                        → 지역 목록 (1회)
2) InspectionVisitStore.findAggregatesByAreaIds(areaIds)     → visitCount, first/lastVisitedAt (1회, GROUP BY)
3) InspectionVisitStore.findLatestByAreaIds(areaIds)         → latestVisit 후보 (1회)
4) ViewedPropertyStore.findAreaSummaries(areaIds)            → totalPropertyCount, topProperty,
                                                               incompleteSummary (1회, 조인 + GROUP BY)
5) InspectionTagStore.findNamesByVisitIds(latestVisitIds)    → latestVisit.tags (1회, 조인)
6) FileBoxStore.findAllByOwnerTypeAndOwnerIdIn(visitIds)     → thumbnails, totalImageCount (1회)
```

5·6번이 쓰는 `visitIds`는 3번 결과에서 얻는다.

**미완료 집계가 4번에 흡수되어 질의가 하나 줄었다.** 미답변 수가 매물 행의 정수 컬럼이라(본문 §3.6.1) 매물 집계와 같은 `GROUP BY`에서 `sum(unanswered_required_count)`로 함께 나온다. `answers` JSON은 읽지 않는다.

#### `totalImageCount`의 비용

이 필드만 성격이 다르다. 나머지는 PostgreSQL `GROUP BY` 집계지만, 사진은 MongoDB `FileBox` 문서의 `items` 배열 길이라 **지역에 속한 모든 회차의 FileBox 문서를 실제로 읽어야** 나온다. 지역 20개 × 회차 5개면 문서 100건을 읽고 배열 길이만 센다.

지금 규모(사용자 2명)에서는 문제가 아니므로 그대로 간다. **회차가 300건을 넘으면** 두 가지 중 하나로 전환한다.

- `FileBox`에 `itemCount`를 비정규화해 두고 `syncItems`에서 갱신한다(집계 전용 필드라 정합성 위험이 낮다)
- 목록에서 `totalImageCount`를 빼고 `thumbnails`만 남긴다(6번을 최신 회차 FileBox만 읽도록 줄일 수 있다)

이 판단은 본문 §3.2의 "집계를 저장하지 않는다" 원칙과 충돌하는 유일한 지점이므로, 전환할 때 원칙의 예외임을 명시한다.

### 11.1 임장 목록 화면 [요구사항 §38]

한 행에 필요한 정보: 지역명, 임장일, 한줄평, 재방문 의사, 매물 수, 최고 관심 매물(단지명+매물명), 태그, 대표 사진. 소박하게 짜면 임장 N건에 대해 지역 N회 + 매물 N회 + 태그 N회 + FileBox N회 = 4N 왕복이 된다.

`InspectionVisitQueryFlow`에서 **배치 조회로 고정한다.**

```text
1) InspectionVisitStore.findPage(조건)                     → 임장 목록 (1회)
2) InspectionAreaStore.findAllByIds(areaIds)               → 지역 (1회)
3) ViewedPropertyStore.findSummariesByVisitIds(visitIds)   → 매물 수 + 최고 관심 매물
                                                             + 미완료 집계 (1회, projection)
4) InspectionTagStore.findNamesByVisitIds(visitIds)        → 태그 (1회, 조인)
5) FileBoxStore.findAllByOwnerIds(INSPECTION_VISIT, ids)   → 대표 사진 (1회)
```

3번은 `viewed_property`에서 `inspection_visit_id IN (...)`로 한 번에 읽고 애플리케이션에서 그룹핑한다. "최고 관심 매물"은 `interestLevel` 내림차순, 동률이면 `sortOrder` 오름차순으로 첫 번째를 고른다(null은 최하위). projection에 `complexName`과 `unansweredRequiredCount`를 포함하고 **`answers`는 포함하지 않는다**(본문 §3.6.2).

5번을 위한 배치 조회는 **`FileBoxRepository`에 이미 정의되어 있다.**

```java
// FileBoxRepository.java:17 — 이미 존재하나 저장소 전체에서 호출부가 0건인 미사용 메서드
List<FileBoxDoc> findAllByOwnerTypeAndOwnerIdIn(FileBoxOwnerType ownerType, Collection<String> ownerIds);
```

`FileBoxStore`에 이를 노출하는 **얇은 래퍼 메서드 하나만 추가하면 된다.** 새 Spring Data 파생 질의를 설계할 필요가 없다. 현재 `FileBoxStore`는 단건 `findOptionalByOwner`만 공개하고 있어 `TravelLogic.getTravels()`가 여행 건마다 N회 호출한다. **travel 동작은 이 PR에서 건드리지 않되**, 래퍼가 생기면 travel 목록도 같은 방식으로 고칠 수 있으므로 별도 개선 과제로 남긴다.

### 11.2 상세 화면 [요구사항 §39, §40, §41]

```text
1) InspectionVisitStore.findById
2) InspectionAreaStore.findById
3) ViewedPropertyStore.findAllByVisitId           (sortOrder 오름차순. answers가 함께 온다)
4) InspectionQuestionStore.findAllByIds(questionIds)  (isCurrentVersion / questionEnabled 판정용, 본문 §10.8)
5) 태그 2회 (임장 / 매물 일괄)
6) FileBoxStore.findOptionalByOwner               (1회, 임장+매물 사진 모두 포함)
```

실제 저장소 왕복은 **10회**다(임장 1 + 지역 1 + 매물 1 + 질문 마스터 1 + 임장 태그 2 + 매물 태그 2 + FileBox 1 + FileAsset 1). 위 단계 수와 다른 이유는 §11.0과 같다 — 태그는 연결+마스터 2회, 사진은 FileBox+FileAsset 2회다.

**중요한 것은 절대 횟수가 아니라 매물 수와 문답 수에 비례해 늘지 않는다는 점이다.** 답변 조회가 3번에 흡수되어 한 번 줄었다 — 상세 화면은 어차피 문답을 그려야 하므로 여기서는 `answers`를 통째로 읽는 것이 맞고, 미완료 요약도 이미 로드한 매물로 만든다(같은 값을 얻자고 projection을 다시 조회하지 않는다).

4번은 **배지 계산에만 쓴다.** 질문 마스터 조회가 실패하거나 비어도 문답 렌더링은 스냅샷으로 정상 동작해야 한다 — `isCurrentVersion`/`questionEnabled`를 `null`로 두고 FE가 배지를 생략한다. 이 조회를 렌더링의 전제로 만들면 요구사항 §20의 독립성이 깨진다.

지역 상세의 복합 응답(본문 §10.1)은 위 10회에 `visits[]` 요약용 조회를 더한다. 회차 요약에 사진을 싣지 않으므로 FileBox 추가 조회는 없다.

**단지별 그룹핑은 서버가 하지 않는다.** 요구사항 §41은 "UI에서는 필요에 따라 단지명을 기준으로 매물을 묶어서 표시할 수 있다"고 쓴다 — 표시 방식의 선택지이지 데이터 구조가 아니다. 응답은 `sortOrder` 오름차순 평면 배열로 내려주고, 각 항목에 `complexName`을 담는다. FE가 필요할 때 `complexName`으로 묶는다.

서버가 그룹핑하지 않는 이유:

- 사용자가 정한 `sortOrder`[요구사항 §33]와 단지 그룹 순서가 충돌한다. 서버가 묶어서 내려주면 "트리마제 → 자이 → 트리마제" 같은 사용자 정렬을 표현할 수 없다.
- 그룹 구조로 내려주면 `VisitedComplex` 도입 시[요구사항 §45] 응답 형태가 두 번 바뀐다. 평면 배열은 그때 `visitedComplexId` 필드가 하나 늘어날 뿐이다.

### 11.3 페이지네이션

임장 목록은 `visited_at DESC` 커서 기반이 자연스럽지만, 현재 저장소의 다른 목록 API가 전부 전체 조회다(`findAllByHiddenFalseOrderByStartDateDesc`). 일관성을 위해 **초기에는 전체 조회로 가고**, 임장 건수가 200건을 넘으면 페이지네이션을 도입한다. 그때 공통 페이지 응답 타입을 함께 정하는 편이 낫다.

### 11.4 검색 확장 [요구사항 §42]

요구사항 §42가 요구하는 검색 축:

| 기준 | 축 | 준비된 인덱스 |
| --- | --- | --- |
| 임장 | 지역명 | `idx_inspection_area_hidden_name` |
| 임장 | 임장 기간 | `idx_inspection_visit_visited` |
| 임장 | 재방문 의사 | 소량이라 인덱스 불필요. 필터만 |
| 임장 | 태그 | `idx_inspection_visit_tag_tag` |
| 매물 | 단지/건물명 | `idx_viewed_property_complex` |
| 매물 | 매물명 | 부분 일치라 인덱스 효과 제한적. 소량 기준 스캔 허용 |
| 매물 | 관심도 | `idx_viewed_property_interest` |
| 매물 | 태그 | `idx_viewed_property_tag_tag` |

요구사항 §42의 검색 축에 **문답 값이 없다는 점을 확인해 둔다.** 임장 기준은 지역명·기간·재방문 의사·태그, 매물 기준은 단지/건물명·매물명·관심도·태그이며 전부 컬럼으로 존재한다. 답변을 매물 안 JSON에 둔 결정(본문 §3.6)이 §42의 어느 축도 막지 않는다.

요구사항 §43(매물 비교)의 비교 항목에는 문답이 들어 있지만, 이는 **선택한 매물 N건을 ID로 불러 나란히 그리는** 기능이다. 값으로 검색하는 것이 아니므로 JSON 저장이 걸림돌이 되지 않는다.

임장 목록의 필터(지역·기간·재방문 의사·태그)는 **초기 범위에 포함한다**(본문 §10.2). 매물 횡단 검색(`GET /viewed-properties?minInterest=4&complexName=트리마제&tag=남향`)은 초기 범위에서 제외하되 위 인덱스를 지금 만들어 두어, 나중에 Resource + QueryFlow 추가만으로 끝나게 한다 — 본문 §16 결정 항목 4.

### 11.5 질문별·버전별 `answerCount` (FE 요구 #5)

답변이 매물 안 JSON이라 이 집계만 인덱스를 타지 못한다. **이 설계에서 유일하게 느려지는 조회이므로 처리 방법을 정해 둔다.**

| 조회 | 방법 |
| --- | --- |
| `GET /inspection-questions?withAnswerCount=true` | 질문별 **총계**. `viewed_property`의 `answers`를 전건 읽어 `questionId`로 집계한다 |
| `GET /inspection-questions/{questionId}/versions` | 그 질문의 버전별 집계. 같은 스캔에서 `questionVersionNo`로 한 번 더 나눈다 |

**`answered = true`인 항목만 센다.** 매물을 만들면 활성 질문이 전부 빈 항목으로 깔리므로, 거르지 않으면 "이 질문이 깔린 매물 수"가 되어 `answerCount`라는 이름과 다른 값이 나간다.

- 두 API 모두 **관리자 화면 전용이고 저빈도**다. 일반 사용자 흐름(매물 작성)은 `withAnswerCount` 없이 호출하므로 이 스캔을 타지 않는다.
- 기본값은 `withAnswerCount=false`다. FE가 명시적으로 켜야 한다.
- 매물 **2,000건을 넘으면** 전환한다. 가장 싼 수단은 `inspection_question`에 `answer_count`를 비정규화하고 매물 생성 시 증가시키는 것이다(스냅샷은 생성 시점에 고정되므로 이후 증감이 없다). 질문별 총계는 이것으로 정확하지만 버전별 분해는 여전히 스캔이 필요하다.

이 한계는 답변을 매물 안에 둔 대가이고, 본문 §5.3에 트레이드오프로 적어 두었다.

---

## 12. 삭제 정책과 트랜잭션 경계

### 12.1 삭제 [요구사항 §34]

| 대상 | 정책 |
| --- | --- |
| 임장 지역 | 연결된 임장 기록이 있으면 물리 삭제 금지(`409`). 없으면 삭제 허용. `hidden` 플래그로 비활성화도 가능 |
| 임장 기록 | 사용자 삭제 허용. `ViewedProperty`(문답 포함), `inspection_visit_tag`, `viewed_property_tag`, `FileBox` 문서를 함께 삭제 |
| 매물 | `viewed_property_tag`, FileBox의 해당 `targetId` 항목을 함께 삭제. **문답은 매물 행 안에 있어 별도 삭제 대상이 아니다** |
| 질문 / 질문 버전 | 물리 삭제 없음. `enabled=false`만. 버전은 질문 행 안의 리스트라 따로 지울 대상이 없다 |
| 태그 마스터 | 삭제하지 않는다. 연결만 끊는다 |
| 실제 파일 / `file_asset` 문서 | 남긴다. 기존 정책과 동일(`docs/file-asset.md`) |

임장 삭제 순서:

```text
1) ViewedPropertyTagStore.deleteByInspectionVisitId   ┐
2) ViewedPropertyStore.deleteByInspectionVisitId      │ 하나의 PostgreSQL 트랜잭션.
3) InspectionVisitTagStore.deleteByInspectionVisitId  │ 커밋까지 완료한다
4) InspectionVisitStore.delete                        ┘
        ↓ RDB 커밋 성공 후에만
5) FileBoxStore.deleteByOwner(INSPECTION_VISIT, visitId)
```

**답변 삭제 단계가 사라졌다.** 2번이 매물 행을 지우면 그 안의 문답도 함께 사라진다 — 문답만 남는 중간 상태가 존재할 수 없다.

**RDB를 먼저 커밋하고 FileBox를 나중에 지운다.** 두 실패 창을 비교하면 방향이 분명하다.

| 순서 | 중간 실패 시 남는 상태 | 복구 가능성 |
| --- | --- | --- |
| FileBox 먼저 → RDB 실패 | 임장 기록은 남아 있는데 **사진 연결만 영구 소실**. 사용자는 "삭제 실패" 에러만 받고 사진이 사라진 것은 알지 못한다 | **불가능.** FileBox 문서가 사라져 `fileAssetId` 목록 자체를 잃는다. 재동기화할 원본 정보가 없다 |
| RDB 먼저 → FileBox 실패 | 아무도 참조하지 않는 고아 `FileBox` 문서 1건 | **무해.** `(INSPECTION_VISIT, 삭제된 visitId)`로는 다시 조회되지 않고, `id_sequence`가 단조 증가하므로 ID 재사용도 없다. 정리 배치로 언제든 지운다 |

이전 판본은 "Mongo 삭제 실패 시 참조 대상 없는 문서가 남는다"를 근거로 반대 순서를 택했다. **그 판단은 틀렸다** — 고아 문서보다 복구 불가능한 사진 소실이 훨씬 나쁘다. 순서를 뒤집는다.

### 12.2 트랜잭션 경계

- `Logic`은 `@Transactional(readOnly = true)`를 클래스에, 커맨드 메서드에 `@Transactional`을 다는 기존 패턴(`TravelLogic`)을 따른다.
- `Flow`도 커맨드 메서드에 `@Transactional`을 단다.
- **FileBox는 MongoDB라 PostgreSQL 트랜잭션에 참여하지 않는다.** 기존 `TravelLogic`과 동일하게 등록·수정은 "RDB 저장 성공 후 FileBox 동기화" 순서를 지킨다. 반대로 하면 RDB 롤백 시 고아 FileBox가 남는다.
- **삭제도 등록과 같은 방향이다. RDB 트랜잭션을 커밋한 뒤에 FileBox를 지운다**(본문 §12.1). 이것은 `@Transactional` 메서드 본문에 Mongo 삭제를 마지막 줄로 두는 것으로는 달성되지 않는다 — 트랜잭션 프록시는 **메서드가 반환된 뒤에** 커밋하므로 본문의 Mongo 삭제는 언제나 커밋보다 먼저 확정된다. `AfterCommitExecutor.run(...)`으로 `TransactionSynchronization.afterCommit`에 등록해야 실제로 순서가 지켜진다. 어느 쪽이든 실패 창은 남지만, 남는 것이 "무해한 고아 Mongo 문서"이지 "복구 불가능한 사진 소실"이어서는 안 된다.
- 두 저장소를 한 트랜잭션으로 묶을 방법이 없으므로 **교차 저장소 연산의 원칙을 하나로 통일한다: PostgreSQL이 진실의 원천이고, FileBox 쪽 불일치는 사후에 정리 가능한 형태로만 남긴다.** 등록·수정 실패든 삭제 실패든 남는 것은 고아 FileBox 문서 하나이고, 같은 정리 배치가 처리한다.
- 고아 FileBox 정리 배치는 이 기능의 범위 밖이다. `ownerId`가 더 이상 존재하지 않는 문서를 지우는 작업은 travel/trip에도 필요하므로 파일 도메인 공통 과제로 남긴다.

---

## 13. 공통 코드 변경 체크리스트

- [x] `SequenceName`에 `INSPECTION_AREA`, `INSPECTION_VISIT`, `INSPECTION_QUESTION` 추가
- [ ] **(배포 작업)** `slcn.id_sequence`에 시드 행 3건 INSERT (본문 §8.7) — **빠뜨리면 등록이 `ID NOT EXIST`로 실패**
- [x] `FileType`에 `INSPECTION("inspection")` 추가
- [x] `FileConstant.AVAILABLE_PATH`에 `inspection` 추가 — **빠뜨리면 업로드가 `FilePathInvalidException`**
- [x] `FileBoxOwnerType`에 `INSPECTION_VISIT` 추가
- [x] `FileBoxTargetType`에 `INSPECTION_VISIT`, `VIEWED_PROPERTY` 추가
- [x] `FileBoxStore`에 `findAllByOwnerTypeAndOwnerIdIn` **래퍼 메서드** 추가 — 리포지터리 파생 질의(`FileBoxRepository.java:17`)는 이미 존재하는 미사용 메서드다 (본문 §11.1)
- [x] `StringListConverter`를 `aggregate/common/store/converter/`로 이동 + `TravelJpo`/테스트 참조 수정 (본문 §9.1)
- [x] `PropertyAnswerListConverter`, `QuestionVersionListConverter` 작성 — `TravelDayListConverter`와 같은 형태. **JPO 필드에 `@Column(columnDefinition = "TEXT")`를 반드시 함께 붙인다**(본문 §8.6)
- [x] `InspectionConstant`에 에러 메시지 상수 정의
- [x] `ErrorCode`에 아래 추가

| ErrorCode | HTTP | 용도 |
| --- | --- | --- |
| `INSPECTION_AREA_NOT_FOUND` | 400 | 지역 없음 |
| `INSPECTION_AREA_IN_USE` | 409 | 임장 기록이 있는 지역 삭제 |
| `INSPECTION_AREA_DUPLICATED` | 409 | 동일 지역명 존재 |
| `INSPECTION_VISIT_NOT_FOUND` | 400 | 임장 기록 없음 |
| `INVALID_INSPECTION_VISIT` | 400 | 임장 입력/완료 조건 위반 |
| `VIEWED_PROPERTY_NOT_FOUND` | 400 | 매물 없음 |
| `INVALID_VIEWED_PROPERTY` | 400 | 매물 입력/완료 조건 위반(단지명·매물명·관심도 포함) |
| `INSPECTION_QUESTION_NOT_FOUND` | 400 | 질문/답변 행 없음 |
| `INVALID_INSPECTION_QUESTION` | 400 | `answerType` 변경 등 금지된 질문 수정 |
| `INSPECTION_QUESTION_CONFLICT` | 409 | 두 관리자가 같은 질문을 동시에 수정(본문 §4.6). 500으로 새어나가지 않게 한다 |
| `INSPECTION_ANSWER_REQUIRED` | 400 | 필수 문답 미완료 |
| `INVALID_PROPERTY_ANSWER` | 400 | 타입별 값 검증 실패 |
| `INVALID_INSPECTION_FILE` | 400 | 사진 `targetId`가 해당 임장/매물에 없음, 파일 타입이 `inspection`이 아님(본문 §7.2) |
| `INVALID_INSPECTION_ORDER` | 400 | 정렬 대상 ID가 해당 임장 소속이 아님(본문 §10.10) |

- [x] `docs/file-asset.md`에 `INSPECTION_VISIT` owner / `INSPECTION_VISIT`·`VIEWED_PROPERTY` target, `inspection` 파일 타입 추가
- [x] `docs/inspection/01-spec-design.md` 상단에 "이 문서는 `docs/field_research/implementation_design.md`로 대체됨" 표기
- [x] 오브젝트 스토리지 사전 준비 **불필요** 확인 완료 — `FileType` 값 추가만으로 `originals/inspection/`·`derived/inspection/` 키가 생성된다 (본문 §7.5-4)
- [x] FE 연동 문서에 다중 업로드 상한(요청 60 MB / 파일 10 MB ≈ 6장)과 `variant` 사용 규칙 명시 (본문 §7.5-1, §7.5-2) — `docs/field_research/api.md` §7
- [x] `SecurityConfiguration`에 `/inspection-questions` **쓰기 메서드(POST/PUT/PATCH) `ADMIN` matcher 추가** — `anyRequest()`보다 앞에 배치. `import org.springframework.http.HttpMethod;`를 함께 추가한다 (본문 §10.4)
- [ ] **(배포 작업)** 관리자 계정이 `USER` + `ADMIN` 권한을 함께 보유하는지 운영 DB에서 확인 (본문 §10.4) — 없으면 질문 등록을 아무도 못 한다

JPA 엔티티 스캔은 `AggregateConfiguration`이 `com.seoulchonnom.aggregate` 전체를 훑으므로 **설정 변경이 필요 없다.**

---

## 14. 테스트 계획

테스트 클래스는 `*Test`, 메서드는 `action_shouldExpectedResult` 형식을 따른다.

### 14.1 aggregate

| 대상 | 검증 |
| --- | --- |
| `ViewedPropertyFlowTest` | 매물 생성 시 활성 질문 수만큼 답변 행 생성 / 버전·문구·required·sortOrder 스냅샷 복사 / 비활성 질문 제외 / 활성 질문 0개여도 생성 성공 |
| `ViewedPropertyFlowTest` | **매물 생성 후 질문을 추가해도 그 매물의 답변 행 수가 그대로인지** [요구사항 §24 회귀 테스트] |
| `ViewedPropertyFlowTest` | **질문 문구를 수정해 버전이 올라간 뒤에도 기존 매물 응답 문구가 그대로인지** [요구사항 §20 회귀 테스트] |
| `ViewedPropertyFlowTest` | 질문 미사용 → 문구 변경 → 재사용 후에도 과거 답변의 `questionVersionNo` 유지 |
| `ViewedPropertyFlowTest` | `complexName` 공백 시 400 / 매물명 공백 시 400 / 필수 문답 미완료 시 `COMPLETED` 전이 400 / 관심도 없이 전이 400 / 관심도 0·6 전이 400 |
| `ViewedPropertyFlowTest` | 같은 단지명으로 매물 2건 등록 성공 [요구사항 §49-23] |
| `ViewedPropertyFlowTest` | `COMPLETED` 임장에 매물 추가 시 임장이 `DRAFT`로 자동 복귀 (본문 §4.4) |
| `InspectionVisitFlowTest` | 매물 0건 임장 완료 성공 [요구사항 §36, §49-22] / 미완료 매물 존재 시 400 / `revisitIntent` 없이 완료 400 |
| `InspectionVisitFlowTest` | `areaId` 인라인 생성 / 동일 지역명 중복 시 409 / 존재하지 않는 `areaId` 400 |
| `InspectionVisitFlowTest` | 지역 정보를 수정해도 기존 임장 기록이 그대로인지 [요구사항 §6 회귀 테스트] |
| `InspectionVisitFlowTest` | 임장 삭제 시 매물(문답 포함)·태그 연결·FileBox 모두 삭제 (본문 §12.1 순서 포함) |
| `ViewedPropertyLogicTest` | 타입별 값 검증 (RATING 0·1·5·6, NUMBER에 textValue 동봉, SINGLE_SELECT 2건, 미등록 code, MULTI_SELECT 중복) |
| `ViewedPropertyLogicTest` | `answered` 파생 규칙 6종 (본문 §3.6 표) |
| `ViewedPropertyLogicTest` | 스냅샷에 없는 `questionId` 요청 시 400 / 부분 저장 시 나머지 항목 유지 |
| `ViewedPropertyLogicTest` | `answers`를 바꾸는 모든 경로에서 `requiredAnswerCount`·`unansweredRequiredCount`가 리스트와 일치 (본문 §3.6.1) |
| `ViewedPropertyLogicTest` | `unit`이 스냅샷에 복사되고, 이후 질문의 `unit`을 바꿔도 과거 답변의 `unit`이 유지되는지 |
| `InspectionQuestionLogicTest` | `content`·`choices`·`unit` 수정 시 버전 추가 / `required`·`sortOrder`·`enabled` 수정 시 버전 유지 / `answerType` 변경 400 |
| `InspectionQuestionLogicTest` | **기존 버전이 수정되지 않는지** — v2를 추가한 뒤 v1의 `content`·`choices`·`unit`이 그대로인지 (본문 §3.5) |
| `InspectionTagLogicTest` | 이름 정규화(`#` 제거, trim) / get-or-create / 10개 초과 400 / 요청에서 빠진 태그 연결 해제 / `tags` 생략 시 유지 |
| `InspectionAreaLogicTest` | 임장 기록 있는 지역 삭제 409 / 지역 수정이 기존 임장에 영향 없음 |
| `InspectionVisitQueryFlowTest` | 목록 배치 조회 왕복 횟수 고정 / 최고 관심 매물 선정 규칙(동률·null 처리) / `topInterestProperty`에 `complexName` 포함 / 태그·매물 수 매핑 정확성 |
| `InspectionAreaQueryFlowTest` | 지역 수를 늘려도 조회 횟수가 고정인지 / `visitCount`·`first`·`lastVisitedAt` 집계 / `topProperty`가 **지역 전체** 기준인지(회차 기준이 아님) / `latestVisit` 선정 / `thumbnails` 최대 2건 / 회차 0건 지역의 집계 기본값 |
| `InspectionAreaQueryFlowTest` | 복합 상세: `visitId` 생략 시 최신 회차 확장 / `includeProperties=false`면 `selectedVisit` 생략 / 회차 50건 초과 시 절단 + `hasMoreVisits` |
| `IncompleteSummaryTest` | 미완료 요약이 본문 §4.2·§4.3 완료 검증과 **같은 결과**를 내는지 — 요약이 전부 0/빈 값이면 완료 전이가 반드시 성공해야 한다 / `revisitIntent` 미입력이 `visitMissingFields`에 잡히는지 / 목록에는 개수만, 상세에는 이름까지 실리는지 |
| `PropertyAnswerMapperTest` | `isCurrentVersion`·`questionEnabled` 판정 / **질문 마스터가 비어도 문답 렌더링 필드가 스냅샷으로 온전한지**(본문 §10.8 회귀 테스트) |
| `InspectionQuestionLogicTest` | 질문별·버전별 `answerCount` 집계 / 답변 0건 질문의 `answerCount = 0` |
| `InspectionOrderTest` | 매물·사진 정렬 부분 갱신 / 요청에 빠진 항목 유지 / 타 임장 ID 400 / `sortOrder` 중복 허용 / 동률 시 2차 키로 **재조회 순서가 항상 같은지** / `COMPLETED` 임장에서도 정렬 변경 가능(상태 미변경) |
| `InspectionOrderTest` | 정렬 직후 `files` 배열을 `sortOrder` 없이 되보내도 **기존 순서가 유지되는지**(본문 §7.2 회귀 테스트) |
| `ViewedPropertyFlowTest` | **`COMPLETED` 매물의 필수 문답을 `PUT .../answers`로 비우면 400**(본문 §4.4 불변식 회귀 테스트) |
| `InspectionConcurrencyTest` | 동일 지역명 동시 등록 시 1건만 생성되고 나머지는 409 / 신규 태그 동시 사용 시 양쪽 모두 성공하고 태그 행은 1건 / 완료 전이 중 필수 답변이 비워지면 완료가 실패 (본문 §4.6) |
| `InspectionVisitFlowTest` | 임장 삭제 시 **RDB 커밋 이후에 FileBox가 삭제되는 순서**인지 / RDB 삭제 실패 시 FileBox가 남아 있는지 (본문 §12.1) |
| `*JpoMapperTest` | 타입별 답변 컬럼 왕복 / `choiceOptions`·`selectedCodes` JSON 왕복 / enum STRING 저장 / null 안전성 |

### 14.2 spec

| 대상 | 검증 |
| --- | --- |
| `InspectionVisitMapperTest` | 상세 응답에 매물·문답·사진이 정렬 순서대로 담기는지 / 매물이 `sortOrder` 평면 배열로 나오는지(그룹핑하지 않음, 본문 §11.2) |
| `PropertyAnswerMapperTest` | `answerType`별로 해당 값 필드만 채워지는지 / 스냅샷 문구가 응답에 그대로 나오는지 |
| `InspectionQuestionMapperTest` | 버전 이력 응답 형태 |

### 14.3 rest

| 대상 | 검증 |
| --- | --- |
| `Inspection*ResourceTest` | 엔드포인트 상태 코드, 인증 필요(`401`), 경로 변수 바인딩 |
| `Inspection*ResourceJsonContractTest` | 요청/응답 JSON 필드 계약. 특히 `answers[]`의 타입별 필드, `files[]`, 매물의 `complexName` |

`slcn-boot`의 기존 `SecurityConfigurationTest`에 아래를 보강한다.

| 대상 | 검증 |
| --- | --- |
| `SecurityConfigurationTest` | `USER` 권한으로 `GET /inspection-questions` 200 / `USER` 권한으로 `POST`·`PUT`·`PATCH /inspection-questions/**` 403 / `ADMIN` 권한으로 쓰기 200 / 인증 없이 전부 401 |

각 단계마다 `./gradlew :{module}:test`, 마지막에 `./gradlew test`를 돌린다.

---

## 15. 구현 순서 (PR 분할)

| # | PR | 내용 | 산출물 |
| --- | --- | --- | --- |
| 1 ✅ | 공통 계약 | `SequenceName`, `FileType`, `FileConstant`, `FileBoxOwnerType`/`TargetType`, `FileBoxStore.findAllByOwnerTypeAndOwnerIdIn`, `StringListConverter` 공통 이동, `ErrorCode`, `InspectionConstant` | travel 테스트 포함 기존 테스트 전부 통과 |
| 2 ✅ | spec 계약 | 엔티티 5종, VO 3종/enum 3종, Facade 5종, sdo 33종, mapper 6종 | 컴파일 + 엔티티·mapper 테스트 36건 |
| 3 ✅ | aggregate — 질문 | `InspectionQuestion` JPO·Store·Logic(`versions` 컨버터 포함), 버전 추가 규칙, 시드 SQL 문서화 | `InspectionQuestionLogicTest` 14건 |
| 4 ✅ | aggregate — 지역·태그 | `InspectionArea`, `InspectionTag` + 연결 테이블 JPO·Store·Logic | `InspectionAreaLogicTest`, `InspectionTagLogicTest` |
| 5 ✅ | aggregate — 임장 | `InspectionVisit` JPO·Store·Logic + `InspectionPhotoSupport` + `InspectionVisitFlow`(등록/수정) | `InspectionVisitLogicTest`, `InspectionVisitFlowTest`, `InspectionPhotoSupportTest` |
| 6 ✅ | aggregate — 매물·문답·상태·삭제 | `ViewedProperty` JPO·Store·Logic(`answers` 컨버터, 파생 카운트 갱신, 타입별 값 검증 포함) + `ViewedPropertyFlow`(스냅샷 생성 포함) + **양쪽 상태 전이와 삭제** | `ViewedPropertyFlowTest`, `ViewedPropertyLogicTest` |
| 7 ✅ | aggregate — 조회 | `InspectionVisitQueryFlow`, `InspectionAreaQueryFlow`, `InspectionQuestionQueryFlow`, `InspectionSummarySupport`, `ViewedPropertySummaryPdo`, 목록·상세 배치 조회, 목록 필터, 미완료 요약, 질문 `answerCount`(본문 §11.5) | `InspectionVisitQueryFlowTest`, `InspectionAreaQueryFlowTest`, `InspectionQuestionQueryFlowTest`, `InspectionSummarySupportTest` |
| 8 ✅ | rest + 보안 | Resource 5종, `InspectionAreaFlow`, `SecurityConfiguration`에 질문 쓰기 `ADMIN` matcher | `*ResourceTest` 4종, `SecurityConfigurationTest` 6건 추가 |
| 9 ✅ | 문서 | `docs/file-asset.md` 갱신, FE 연동 문서(`docs/field_research/api.md`), `01-spec-design.md` 대체 표기 | — |

3~4번은 서로 독립이라 병행 가능하다. 5번은 4번, 6번은 3·5번에 의존한다.

**상태 전이와 삭제는 5번이 아니라 6번에 둔다.** 임장 완료 조건(본문 §4.3-4)이 "모든 매물이 COMPLETED"이고 임장 삭제가 하위 매물 삭제를 포함하므로, 두 기능 다 `ViewedProperty`가 있어야 성립한다. 5번에 두면 매물을 모르는 반쪽짜리 구현을 만들었다가 6번에서 다시 고치게 된다.

**PR 1~9 구현이 완료되었다.** 아래 §15.1은 배포 시 사람이 해야 하는 작업이며 코드로 대신할 수 없다.

### 15.1 배포 시 순서

1. PR 1~8 머지 후 빌드
2. **`id_sequence` 시드 3건 INSERT** (본문 §8.7)
3. 애플리케이션 기동 → `ddl-auto=update`가 테이블 7개 생성
4. 유니크 인덱스 **4건** 생성 여부 확인, 없으면 수동 생성 (본문 §8.6)
5. `viewed_property.answers`와 `inspection_question.versions`가 `text`로 생성되었는지 확인 (본문 §8.6)
6. 초기 질문 세트 등록 (`POST /inspection-questions` × N)

---

## 16. 확인이 필요한 결정 사항

### 16.1 확정된 결정

| # | 항목 | 결정 | 반영 위치 |
| --- | --- | --- | --- |
| 1 | 질문 관리 권한 | **`ADMIN` 전용**(쓰기만). 조회는 `USER` 유지 | 본문 §10.4, §13 체크리스트, §14.3 테스트, PR 8 |
| 2 | 문답의 모델링 | **`PropertyAnswer`는 VO.** `ViewedProperty`가 리스트로 품고 `viewed_property.answers` JSON 컬럼에 저장 | 본문 §3.1.1, §3.6, §5.3, 부록 F |
| 3 | 질문 버전의 모델링 | **`QuestionVersion`은 VO.** `InspectionQuestion`이 리스트로 품고 `inspection_question.versions` JSON 컬럼에 저장 | 본문 §3.5, 부록 F |
| 4 | 미완료 집계 | 매물 행에 `requiredAnswerCount` / `unansweredRequiredCount`를 **파생 저장** | 본문 §3.6.1, §11.0 |
| 5 | 답변 스냅샷의 `unit` | 스냅샷에 **포함한다** | 본문 §3.6 |

### 16.2 미결정 항목

구현 착수 전에 정해야 할 항목이다. 각 항목의 기본값은 이 문서가 이미 택한 값이며, 바꾸려면 여기서 결정한다. 번호는 이전 판본과 동일하게 유지해 본문 참조가 어긋나지 않게 한다.

| # | 항목 | 이 문서의 기본값 | 대안 | 영향 범위 |
| --- | --- | --- | --- | --- |
| 2 | 태그 저장 방식 | `InspectionTag` 마스터 + 연결 테이블 [요구사항 §25 다이어그램] | `Travel`처럼 JSON `List<String>` 컬럼 → 테이블 3개 감소, 대신 태그 필터가 전체 스캔 | 테이블 3개, Store/Logic 2종 |
| 3 | 태그 용도 구분 | 구분 없이 공통 풀 공유 | `scope { VISIT, PROPERTY, BOTH }` 추가 → 자동완성이 화면별로 정확해짐. 요구사항 §26/§27의 예시 태그군이 실제로 거의 겹치지 않는다 | 컬럼 1개, 조회 조건 |
| 4 | 매물 횡단 검색 API [요구사항 §42] | 초기 범위 제외. 인덱스만 준비 | 지금 포함 → `ViewedPropertySearchQueryFlow` + Resource 추가 | PR 1개 추가 |
| 5 | 완료 후 자동 DRAFT 복귀 | 자동 복귀(본문 §4.4) | `409`로 거절하고 FE가 명시적으로 되돌리게 함 | Flow 분기 |
| 6 | `InspectionStatus` 공용 enum | 임장·매물 공용 1개 | `InspectionVisitStatus` / `ViewedPropertyStatus` 분리 | enum 1개 |
| 7 | 임장 목록 페이지네이션 | 전체 조회(기존 도메인과 동일) | 커서 기반 도입 | Query/Repository |
| 8 | **"완료 대상 매물" 해석** [요구사항 §36-4, §36-6] | 등록된 **모든** 매물이 `COMPLETED`여야 임장 완료 | `ViewedProperty.completionTarget` 같은 플래그를 두고 "참고용 매물"은 `DRAFT`로 남긴 채 임장 완료 허용 | 컬럼 1개 + 완료 검증 조건 + 매물 API |
| 9 | 지역 중복 판정 기준 | 지역명 완전 일치 시 `409` + 기존 후보 제시 | 지역명 + 지역 설명 모두 일치할 때만 차단(같은 이름의 다른 범위를 별도 지역으로 허용) | Store 질의 1건 |
| 10 | 단지명 자동완성 범위 | 해당 임장 내 기존 단지명만(본문 §3.4.2) | 전체 임장 횡단 단지명 → 재임장 시 편하지만 사실상 단지 마스터가 되어 §45 확장과 충돌 | Repository 질의 1건 |
| 11 | **임장 목록 화면[요구사항 §38] 존치 여부** | 지역 목록과 **둘 다** 제공(`GET /inspection-areas`, `GET /inspection-visits`) | FE가 지역 목록만 쓴다면 임장 목록은 필터 조회 전용으로 축소 → `InspectionVisitQueryFlow`의 집계 부분 삭제 | Flow 1개, projection 1개, 테스트 |
| 12 | `totalImageCount` 제공 여부 | 제공. 회차 300건까지는 FileBox 문서를 직접 읽어 집계(본문 §11.0) | 목록에서 제외하고 `thumbnails`만 / `FileBox.itemCount` 비정규화 | 공유 계약 또는 응답 필드 |

이전 판본의 결정 항목이었던 "매물 0건 임장 완료 허용 여부"는 요구사항 §36·§49-22가 "매물이 존재하지 않아도 완료할 수 있다"로 확정하여 목록에서 제외했다.

---

## 부록 A. `docs/inspection/01-spec-design.md`와의 차이

이전 제안은 매물(`ViewedProperty`) 계층이 없고 장소가 개별 단지인 2계층 설계였다. 요구사항이 3계층 + 생활권 단위 지역을 명시하면서 아래가 달라졌다.

| 항목 | 01-spec-design.md | 이 문서 | 사유 |
| --- | --- | --- | --- |
| 계층 | 장소 → 방문 | 지역 → 임장 → **매물** | 요구사항 §2 |
| 최상위 엔티티의 의미 | `InspectionPlace` = 개별 단지(마포래미안푸르지오) | `InspectionArea` = **지역/생활권**(성수동) | 요구사항 §3.1, §49-1 |
| 단지 정보 | 최상위 엔티티가 곧 단지 | `ViewedProperty.complexName` 문자열 | 요구사항 §13, §49-6·7 |
| 문답 대상 | 방문(`Inspection`) | **매물(`ViewedProperty`)** | 요구사항 §17, §49-8 |
| 방문 엔티티명 | `Inspection` | `InspectionVisit` | 요구사항 용어 |
| 좌표 | `latitude`/`longitude` 필수 + 지도 API 검토 | **저장하지 않음** | 요구사항 §5, §49-21 |
| 방문 시간 | `visitedDate` + `VisitTimeSlot` | `visitedAt` 단일 `LocalDateTime` | 요구사항 §9 |
| 상태 | 없음 | `DRAFT` / `COMPLETED` | 요구사항 §10, §16 |
| 관심도 | 없음(종합 평점은 미결정 항목) | `interestLevel` 1~5. DRAFT에서는 null 허용, COMPLETED 전이 시 필수 | 요구사항 §15 |
| 장단점 | `List<String>` | 자유 텍스트 `pros`/`cons` | 요구사항 §28 |
| 태그 | Place의 JSON 문자열 목록 | 마스터 + 연결 테이블, 임장/매물 각각 | 요구사항 §25 |
| 질문 스냅샷 | 방문 등록 시 답변에 복사 | **매물 생성 시 빈 답변 VO를 매물 안에 미리 생성** | 요구사항 §24 |
| 답변 타입 | `TEXT, SCORE, BOOLEAN, NUMBER, SINGLE_CHOICE, MULTI_CHOICE` | `TEXT, LONG_TEXT, BOOLEAN, SINGLE_SELECT, MULTI_SELECT, NUMBER, RATING` | 요구사항 §21 |
| 사진 카테고리 | `FileBoxItem.category` 추가 제안 | **추가하지 않음** | 요구사항에 카테고리 개념 없음. 공유 계약 변경 회피 |
| 비교 기능 | `InspectionCompareQueryFlow` 포함 | 범위 제외 | 요구사항 §43·§44는 향후 확장 |
| 중복 방문 차단 | 같은 날·시간대 409 | 차단하지 않음 | 요구사항 §6은 재임장을 자유롭게 허용한다. 지역 단위라면 하루에 오전·오후 두 번도 정상 기록이다 |

| 답변 저장 | 별도 `property_answer` 테이블 | **`viewed_property.answers` JSON 컬럼(VO)** | 본문 §3.1.1, §5.3 |
| 질문 버전 저장 | 별도 `inspection_question_version` 테이블 | **`inspection_question.versions` JSON 컬럼(VO)** | 본문 §3.5 |

유지되는 판단: 질문과 버전의 개념적 분리, 답변의 문구 스냅샷, `answerType` 변경 금지, `Flow` 우선 도입.

**뒤집힌 판단 하나를 명시한다.** 이전 판본과 이 문서의 초기 판본은 모두 답변을 별도 테이블로 두었다. 근거는 "문답 값으로 질의해야 한다"였는데, 요구사항 §42의 검색 축을 다시 확인하니 **문답 값이 축에 없다.** 요구되지 않는 능력을 위해 테이블 2개와 명시적 행 잠금을 떠안고 있었다. 본문 §3.1.1의 세 가지 기준으로 다시 판정해 VO로 내렸다.

---

## 부록 B. 요구사항 개정(2026-09-17)에 따른 설계 변경

요구사항 명세서가 40개 규칙 판본에서 49개 규칙 판본으로 개정되면서 이 문서가 바꾼 내용이다.

### B.1 구조를 바꾼 변경

| # | 요구사항 변경 | 설계 영향 |
| --- | --- | --- |
| 1 | **`InspectionPlace`(개별 단지) → `InspectionArea`(지역/생활권)** [요구사항 §3.1, §49-1] | 엔티티·테이블·ID prefix·엔드포인트·DTO·예외·ErrorCode 전면 개명. `address` 필드 → `description`(지역 설명). 중복 판정 기준을 "이름+주소 일치"에서 "지역명 일치"로 변경 |
| 2 | **`ViewedProperty.complexName` 필수 필드 신설** [요구사항 §12, §13, §37-1, §49-6] | 컬럼·인덱스(`idx_viewed_property_complex`) 추가, 매물 완료 조건에 항목 1개 추가, DRAFT 최소 입력에 추가, 목록 응답의 `topInterestProperty`와 매물 상세 응답에 추가, 단지명 자동완성 API 신설(본문 §10.3), 테스트 3건 추가 |
| 3 | §41 동일 단지 매물 그룹 표시 신설 | 서버가 그룹핑하지 않고 `sortOrder` 평면 배열 + `complexName`으로 내려주는 근거를 본문 §11.2에 명시 |
| 4 | §45 `VisitedComplex` 확장 경로 신설 | 본문 §3.4.1 신설 — `complexName`에 유니크·마스터·단지 단위 필드를 붙이지 않는다는 제약과 마이그레이션 경로 명시 |
| 5 | §42 검색 축에 임장 기간·단지/건물명·매물명 추가 | 본문 §11.4를 표로 재작성. 임장 목록 필터는 초기 범위 포함으로 승격, 매물 횡단 검색은 인덱스만 준비 |

### B.2 결정을 확정하거나 새로 연 변경

| # | 요구사항 변경 | 설계 영향 |
| --- | --- | --- |
| 6 | §36 "임장에는 매물이 존재하지 않아도 된다"가 **단정문으로 확정**(이전: "별도 정책으로 결정할 수 있다") | 결정 항목에서 제거. 본문 §4.3에 확정 사항으로 기술 |
| 7 | §37 매물 완료 조건이 3개 → **5개**로 세분화(단지명, 관심도 범위 명시) | 본문 §4.2를 5개 조건으로 재작성 |
| 8 | §10.2·§36-4·§36-6이 "**완료 대상** 매물"을 반복 사용 | 해당 필드가 없으므로 "등록된 모든 매물"로 해석하되, **결정 항목 8을 신설**해 다른 해석(참고용 DRAFT 매물 허용)의 비용을 명시 |
| 9 | §8에 "재방문 의사는 지역 자체의 속성이 아니라 특정 임장 시점의 판단" 문장 추가 | `InspectionVisit`에 두는 기존 배치의 근거로 본문 §3.3에 인용 |
| 10 | §26 임장 태그 예시가 지역·생활권 특성으로 교체(`#한강`, `#직주근접`, `#교통혼잡`) | 구조 변화 없음. 다만 임장 태그군과 매물 태그군이 거의 겹치지 않게 되어 **결정 항목 3(태그 scope 분리)의 실익이 커졌다**는 점을 명시 |

### B.3 변경되지 않은 핵심 결정

요구사항 개정에도 그대로 유지된다.

- 문답 스냅샷을 `PropertyAnswer` 행 미리 생성으로 구현(본문 §5) — §24의 두 선택지 중 후자
- 사진을 기존 `FileBox`로 처리하고 신규 이미지 테이블을 만들지 않음(본문 §7) — §29가 재사용을 명시
- 태그 마스터 + 연결 테이블(본문 §6) — §25 다이어그램 유지
- DRAFT/COMPLETED 검증을 저장 제약이 아닌 상태 전이 조건으로 처리(본문 §4)
- 완료 이후 수정 시 자동 DRAFT 복귀(본문 §4.4) — 요구사항에 여전히 명시 없음
- 3개 시퀀스 + 나머지 UUID의 ID 전략(본문 §8.7)

### B.4 문서 자체의 변경

- 요구사항 절 번호가 전면 재배치되어(예: 구 §32 임장 완료 조건 → 신 §36) 문서 내 모든 인용을 갱신했다.
- 요구사항 인용과 이 문서의 자체 절 참조가 같은 `§N` 표기로 섞여 있어 `[요구사항 §N]` / `본문 §N` 규약을 도입했다.
- 예시 데이터를 개정 요구사항의 시나리오(성수동 / 트리마제 / 서울숲리버뷰자이)로 교체했다.

---

## 부록 C. 오브젝트 스토리지 전환(`feat/object-storage-migration`) 반영 검토

`9d6dd41` 머지로 파일 저장이 로컬 디스크에서 오브젝트 스토리지로 전환되었다. 본문 §7(사진 설계)의 결정이 유효한지 전수 확인한 결과다.

### C.1 결론

**설계 결정은 하나도 바뀌지 않는다.** 전환은 파일 **바이트의 저장·조회 경로**만 교체했고, 임장 설계가 의존하는 계약(`FileBox`, `FileType`, `AVAILABLE_PATH`, 업로드 API)은 그대로다. 추가된 것은 API 사용 방식의 제약 세 가지(본문 §7.5)뿐이다.

### C.2 임장 설계가 의존하는 지점별 확인

| 의존 지점 | 이번 머지의 변경 | 판정 |
| --- | --- | --- |
| `FileBox` 문서 구조 (`ownerType`/`ownerId`/`items`) | **변경 없음.** `filebox` 패키지 전체가 diff에 없다 | 본문 §7.1 유효 |
| `FileBoxOwnerType` / `FileBoxTargetType` | 변경 없음. 여전히 `TRAVEL`, `TRIP` / `TRAVEL`, `TRAVEL_DAY`, `TRAVEL_PLACE`, `TRIP` | `INSPECTION_VISIT`·`VIEWED_PROPERTY` 추가 필요 — 체크리스트 유지 |
| `FileBoxItem` 필드 (`caption`, `sortOrder`) | 변경 없음 | 요구사항 §30·§31 저장 정보와 1:1 대응 유지. 신규 이미지 테이블 불필요 |
| `FileType` | 변경 없음. 여전히 `logo\|map\|travel\|profile` | `INSPECTION("inspection")` 추가 필요 — 체크리스트 유지 |
| `FileConstant.AVAILABLE_PATH` | 변경 없음 | 업로드 검증이 `FileUtils.saveImageAsset` → `stageUpload`로 **메서드만 바뀌고 `type.matches(AVAILABLE_PATH)` 검사는 그대로**다. 빠뜨리면 여전히 `FilePathInvalidException` |
| 업로드 API 계약 (`POST /assets/file`, `/files` → `fileId`) | 변경 없음. 응답에 필드만 추가 | 본문 §7.3 부분 동기화 흐름 유효 |
| `FileAssetStore.findById` (파일 타입 검증용) | 변경 없음. `findPage`만 추가 | 본문 §7.2의 "파일 타입이 `inspection`이 아니면 400" 검증 유효 |
| `FileAssetRdo` (응답 임베드) | 변경 없음 | 목록·상세 응답 형태 유효 |
| `FileBoxStore` / `FileBoxRepository` | 변경 없음 | 리포지터리에 `findAllByOwnerTypeAndOwnerIdIn`가 **이미 있다**(미사용). Store 래퍼만 추가하면 된다 (본문 §11.1) |

### C.3 새로 생긴 제약 (본문 §7.5에 반영)

| 변경 | 임장에 대한 영향 |
| --- | --- |
| `max-request-size: 60MB` 신설 | 다중 업로드 ≈ 6장 상한. **임장에서 가장 먼저 걸리는 제약** — 사진 30장 규모가 정상 시나리오다 |
| 원본 조회가 `302` + 서명 URL (`no-store`) | 목록·상세는 축소본(`variant`)을 써야 캐시가 먹는다 |
| 파생본 생성·업로드가 업로드 요청 안에서 동기 수행 | 사진 수가 많은 임장은 업로드 응답이 느려진다. FE 청크·진행률 UX 필요 |
| `originals/` / `derived/` 키 prefix 분리 | 신규 타입 사전 준비 불필요. `FileType` 값 추가로 끝 |
| 백필(`slcn.storage.migration.*`) | 임장과 무관. `file_asset` 문서 기준 1회성 작업이고 임장에는 레거시 파일이 없다 |

### C.4 재검토가 필요해지는 조건

아래 중 하나라도 생기면 본문 §7을 다시 본다.

- `FileBox`를 MongoDB에서 걷어내거나 `FileBoxItem` 구조가 바뀔 때 — 본문 §7.1의 "신규 이미지 테이블 없음" 결정의 근거가 사라진다
- 업로드가 서버 경유에서 **클라이언트 직접 업로드(presigned PUT)**로 바뀔 때 — 본문 §7.3의 "서버가 `targetType`/`targetId`를 확정한다"는 전제와 파일 타입 검증 지점이 달라진다
- 파생본 variant 목록(`home-feature`, `home-thumb`)이 바뀔 때 — 본문 §7.5-2의 화면별 variant 선택 기준을 갱신한다

---

## 부록 D. FE 화면 설계 요구 대응

FE 화면 설계에서 제기된 6개 항목의 반영 결과다. 대부분 요구사항 명세서에 없던 **집계**이며, 데이터 구조 변경 없이 조회 계층에서 처리된다.

### D.1 대응 요약

| # | FE 요구 | 이전 상태 | 반영 위치 | 데이터 구조 변경 |
| --- | --- | --- | --- | --- |
| 1 | 지역 목록 집계 필드 | **없음.** 지역 목록은 `keyword` 검색만이었고 집계는 지역 *상세*에 일부만 있었다 | §10.1 응답 필드표, §11.0 배치 조회 | 없음 (전부 조회 시 집계) |
| 2 | DRAFT 미완료 요약 (개수 + 이름) | **부분.** 완료 검증 실패 `400` 메시지에만 담겼고 정상 조회 응답에는 없었다 | §10.9 `IncompleteSummaryRdo` | 없음 (인덱스 1건 조정) |
| 3 | 지역 상세 한 번 호출 | **없음.** 지역 상세 + 임장 상세 2회 호출이었다 | §10.1 복합 응답, §11.2 | 없음 |
| 4 | 문답에 질문 메타 동봉 | **거의 충족.** 8개 필드는 이미 스냅샷으로 있었고 `isCurrentVersion`·`questionEnabled` 2개가 없었다 | §10.8 | 없음 (질문 마스터 배치 조회 1회 추가) |
| 5 | 질문별·버전별 `answerCount` | **없음** | §10.4, §11.5 | 없음 (전건 스캔. §11.5에 전환 임계값) |
| 6 | 정렬 값 저장 엔드포인트 | **부분.** 매물 정렬은 있었고 사진 정렬 전용 엔드포인트가 없었다 | §10.10 | 없음 |

**FE 요구 6건 중 어느 것도 스키마를 바꾸지 않았다.** 요구가 전부 집계·조회 조합이었고, 답변에 `required`·`answered`를 스냅샷으로 저장해 둔 설계(§3.6)가 미완료 집계를 단일 `GROUP BY`로 받아냈기 때문이다. 이후 답변을 VO로 내리면서(부록 F) 이 집계는 매물 행의 파생 정수 컬럼으로 옮겨갔고, 질의 횟수는 오히려 하나 줄었다(§11.0). 다만 요구 #5(버전별 `answerCount`)만 인덱스를 잃어 §11.5에 별도 처리 방법을 두었다.

### D.2 요구를 그대로 받지 않고 조정한 부분

| 항목 | FE 요구 | 조정 | 이유 |
| --- | --- | --- | --- |
| 미완료 요약의 "이름" | 지역 행·매물 행 모두에 미충족 질문 **이름** | 목록은 **개수만**, 상세에만 이름 (§10.9) | 목록 N행마다 질문 문구를 끌어오면 인덱스만 읽던 집계가 답변 본문 조회로 바뀐다. 목록 화면에 필요한 것은 "N개 남음"이라는 수치다 |
| `isCurrentVersion`, `questionEnabled` | 각 `PropertyAnswer`에 동봉 | 동봉하되 **배지 전용**으로 못박음 (§10.8) | 이 둘만 현재 마스터 파생값이다. 렌더링까지 이 필드에 기대면 요구사항 §20(과거 기록 불변)이 깨진다. 질문 마스터 조회가 실패해도 문답은 스냅샷으로 온전해야 한다 |
| 지역 상세 `visits[]` | 회차 목록 전체 | 50건 초과 시 절단 + `hasMoreVisits` (§10.1) | FE도 "회차가 많아지면 지연 로딩"을 전제했다. 상한을 서버가 정해 두지 않으면 지연 로딩 전환 시점이 화면마다 달라진다 |
| 사진 정렬 | 사진 `sortOrder` 일괄 갱신 | 임장·매물 사진을 **한 엔드포인트**로 통합, `targetType`을 요청에서 받지 않음 (§10.10) | `itemId`가 FileBox 문서 안에서 유일하다. 클라이언트가 대상 종류를 판정하게 하면 틀릴 여지만 생긴다 |
| `totalImageCount` | 지역 목록에 포함 | 포함하되 비용을 명시하고 전환 임계값을 정함 (§11.0) | 이 필드만 MongoDB 문서를 실제로 읽어야 나온다. 나머지 집계와 성격이 다르다는 점을 숨기지 않는다 |

### D.3 회차 간 매물 연결

FE가 "프런트에서 계산하므로 백엔드 변경 불필요"로 정리했고, 이 판단에 동의한다. 같은 단지·같은 매물명이 여러 회차에 나타나는 것을 문자열 비교로 묶는 수준이면 충분하다.

다만 요구사항 §43(매물 비교)로 갈 때는 명시적 연결이 필요해진다. 문자열 비교는 "101동 1203호"와 "101동 1203호 / 84A"를 다른 매물로 본다. 그 시점의 선택지는 둘이다.

- `ViewedProperty`에 `linkedPropertyId`(이전 회차의 같은 매물)를 추가한다 — 사용자가 명시적으로 연결
- 요구사항 §45의 `VisitedComplex` 도입과 함께 단지를 먼저 정규화한 뒤 그 아래에서 매물을 묶는다

후자가 구조적으로 맞지만 비용이 크다. **지금은 어느 쪽도 하지 않는다.** 본문 §3.4.1의 제약(단지명에 유니크·마스터를 붙이지 않는다)을 지키면 두 경로 모두 열려 있다.

### D.4 남은 확인 사항

- 요구사항 §38의 **임장 목록 화면**이 FE 설계에 존재하는지. FE가 기술한 IA는 `지역 목록 → 지역 상세(회차 탭)`이고, 여기에 임장 목록이 보이지 않는다. 둘 다 제공하는 것을 기본값으로 두었으나(§16.2 결정 항목 11) FE가 쓰지 않는다면 `InspectionVisitQueryFlow`의 집계 부분은 만들 필요가 없다.
- 지역 목록의 `topProperty`가 **지역 전체 기준**인지 **최신 회차 기준**인지. 이 문서는 지역 전체로 해석했다("이 지역에서 본 매물 중 가장 관심 있던 것"). 최신 회차 기준이라면 집계 질의가 달라진다.

---

## 부록 E. 설계 검증 기록 (2026-09-17)

세 갈래의 독립 검증을 거쳤다. 각 검증에서 제기된 지적과 처리 결과다.

### E.1 코드베이스 사실 검증

설계서가 기존 코드에 대해 주장한 사실을 전수 대조했다. 지적 2건, 모두 반영.

| 지적 | 처리 |
| --- | --- |
| `FileBoxRepository.findAllByOwnerTypeAndOwnerIdIn`가 **이미 존재**(호출부 0건인 미사용 메서드). 설계서는 새로 만들어야 하는 것처럼 서술 | §11.1·§13·부록 C 정정 — Store 래퍼만 추가 |
| `SecurityConfiguration`에 `HttpMethod`가 import되어 있지 않아 §10.4 스니펫이 컴파일되지 않음 | 스니펫에 주석 + §13 체크리스트 추가 |

정량 주장(`TravelLogic` 492줄, `0xFFFF`, `10MB`/`60MB`, `ddl-auto`, `context-path`)과 "~이 없다"는 주장(ADMIN 부여 API 부재, 임장 코드 0줄, `filebox` 패키지 미변경)은 전부 사실로 확인됐다.

### E.2 요구사항 추적성 검증

요구사항 §1~§49와 §49의 24개 핵심 규칙에 대한 은닉 누락은 **없었다.** 범위 밖 항목(§43~§47)은 모두 §1.2에서 이유와 함께 명시적으로 defer되어 있다. 인용 오류(잘못된 절 번호)도 0건. 지적 8건 중 실질 결함 2건 포함, 전부 반영.

| 심각도 | 지적 | 처리 |
| --- | --- | --- |
| HIGH | §11.0이 "6회로 고정"이라 쓴 직후 "실제로는 7회"라고 자기모순 | 태그 조회를 6번 단계로 명시하고 **7회 고정**으로 통일 |
| HIGH | §10.1의 `incompleteSummary`가 §10.9에 정의되지 않은 `draftVisitCount`/`draftPropertyCount`를 참조 | §10.9를 매물·임장·지역 3레벨 표로 재작성, 각 필드에 대응 검증 조건 명시 |
| MEDIUM | §10.9가 "§4.2와 같은 기준"이라 했으나 임장 완료 조건(`revisitIntent`)을 담을 필드가 없었다 | `visitMissingFields` 추가. 요약에 없는 완료 조건이 있으면 "0개 남았는데 완료가 안 되는" 상태가 생긴다 |
| MEDIUM | `answerType` 변경 금지가 요구사항 §18 "질문 타입 설정"과 충돌하는데 해소 문장이 없었다 | §3.5에 관계 설명 추가 — "생성 시점 설정"으로 좁히고 타입 변경은 새 질문으로 |
| MEDIUM | "질문 물리 삭제 API는 만들지 않는다"가 요구사항보다 강한 주장 | 요구사항이 금지하는 범위와 설계의 단순화를 구분해 서술 |
| LOW | 부록 A의 "`interestLevel` 1~5 필수"가 DRAFT nullable을 누락 | 정밀화 |
| LOW | 모듈 트리에 `InspectionTagMapper` 누락 | 추가, PR 2 산출물 mapper 5종 → 6종 |
| LOW | `visitedAt`의 "필수" 근거가 §9가 아니라 §7.1 | 인용 분리 |

### E.3 설계 건전성 검증

설계 자체의 결함과 프로젝트 규약 준수를 검토했다. HIGH 5건 전부 실제 결함으로 확인되어 반영.

| 심각도 | 지적 | 처리 |
| --- | --- | --- |
| HIGH | **`PUT .../answers`에 완료 재검증이 걸려 있지 않아** `COMPLETED` 매물의 필수 문답을 비워도 상태가 그대로 남는다 | §4.4에 붕괴 시나리오와 함께 명시. 두 쓰기 경로가 `revalidateIfCompleted`를 공유하도록 규정 |
| HIGH | 완료 전이가 "검증 SELECT → UPDATE"인데 잠금 전략이 없어, 검증 통과 후 커밋 전에 답변이 비워지면 필수 문항이 빈 `COMPLETED`가 생긴다 | §4.6 신설. 당시에는 `property_answer`에 `SELECT ... FOR UPDATE`로 막았다. 이후 답변을 매물 안으로 옮기면서(부록 F) 같은 행이 되어 `@Version`이 자동으로 막는다 |
| HIGH | 지역명 중복 방지가 앱 레벨 check-then-act뿐. 더블클릭이면 "성수동"이 2건 생겨 **이 설계가 막으려던 결함이 그대로 재현** | `uk_inspection_area_name` 유니크 인덱스 추가 |
| HIGH | 지역 삭제 전 "임장 0건" 검사도 TOCTOU. FK가 없어 고아 참조가 생기면 상세 조회가 깨진다 | §4.6에 행 잠금 규칙 명시 |
| HIGH | **삭제 순서가 거꾸로였다.** FileBox를 먼저 지우고 RDB가 실패하면 사진만 복구 불가능하게 사라진다 | §12.1·§12.2에서 **순서를 뒤집었다.** 남는 것이 무해한 고아 문서여야 한다는 원칙으로 재정리 |
| MEDIUM | `sortOrder` 중복을 허용하면서 2차 정렬 키가 없어 재조회마다 순서가 달라질 수 있다 | `ORDER BY sort_order, registered_time, id` 명시 |
| MEDIUM | 그룹 전체 치환(§7.3)과 전용 정렬(§10.10)이 충돌 — 정렬 직후 캐시된 `files` 재전송이 순서를 덮어쓴다 | §7.2에 "기존 `id`를 가진 항목은 기존 `sortOrder` 유지" 단서 추가 |
| MEDIUM | 태그 get-or-create의 유니크 위반 처리가 없어 동시 요청 시 임장 등록 전체가 실패 | §4.6-4 + §6.2에 재조회·재사용 규정 |
| MEDIUM | 스칼라 optional 필드의 PUT 의미론(생략=유지 vs 삭제)이 미정의. 9개 PR에 걸쳐 제각각 구현될 위험 | §10.7에 도메인 전체 단일 규칙 신설. **`TravelLogic`의 `tags`가 반대로 동작한다는 함정도 명시** |
| MEDIUM | 질문 동시 수정 시 유니크 위반이 500으로 샐 수 있다 | §4.6에 `409` 매핑 규정 |
| LOW | 사진 `targetId` 오류, 정렬 대상 오류에 대응하는 ErrorCode 부재 | `INVALID_INSPECTION_FILE`, `INVALID_INSPECTION_ORDER` 추가 |

건전한 것으로 판단된 부분: 문답 스냅샷 materialization(§5), DRAFT/COMPLETED를 컬럼 제약이 아닌 전이 검증으로 분리한 결정(§4.5), UUID/`id_sequence` 분리(§8.7), FileBox 그룹 단위 부분 동기화 원칙(§7), 모듈·패키지 배치의 `module.md` 정합성.

### E.4 검토했으나 반영하지 않은 지적

| 지적 | 판단 |
| --- | --- |
| 질문 관리를 `InspectionQuestionAdminFacade`로 분리 (`module.md`의 `{Domain}AdminFacade` 규칙) | `module.md`는 "분리할 수 있습니다(추후 서비스가 커질 경우 대비)"로 선택지로 제시한다. 강제 규칙이 아니고 엔드포인트 7개 중 쓰기가 5개라 분리 실익이 작다. **본문 §16.2에 결정 항목으로 올리지 않고 여기 기록만 남긴다** |
| `QuestionChoiceSdo` 네이밍이 `Sdo`(Save Data Object) 의미와 어긋남 | 생성·수정 양쪽 입력에 중첩되는 값이라 `Cdo`/`Udo` 어느 쪽으로도 맞지 않는다. 범용 저장 입력이라는 뜻의 `Sdo`가 가장 가깝다고 보고 유지 |

---

## 부록 F. 엔티티/VO 재판정 (2026-09-17)

### F.1 물음

"7종을 전부 엔티티로 둘 필요가 있는가. VO로 관리해도 되는 것이 있는가. 문답은 별도 테이블이 아니라 JSON 컬럼이어도 되지 않는가 — 그러면 질문 버전도 따로 관리할 필요가 없어 보인다."

### F.2 판정

본문 §3.1.1의 세 기준(**바깥에서 ID로 참조되는가 / 자기만의 생명주기가 있는가 / 자기 필드로 부모를 가로지르는 질의가 있는가**)으로 7종을 다시 판정했다. `PropertyAnswer`와 `InspectionQuestionVersion` 두 종이 **셋 다 아니었다.**

| | 엔티티 7종 / 테이블 9개 | 엔티티 5종 / 테이블 7개 |
| --- | --- | --- |
| Store | 7 | 5 |
| JPO | 10 | 7 |
| 유니크 인덱스 | 5 | 4 |
| 지역 목록 질의 | 7회 | **6회** |
| 임장 상세 질의 | 8회 | **7회** |
| 임장 삭제 단계 | 6 | **5** |
| check-then-act 지점 | 4 | **3** |

### F.3 결정을 뒤집은 근거

초기 판본이 답변을 테이블로 둔 근거는 본문 §3.6의 이 한 줄이었다.

> 문자열 하나에 몰면 "채광 4점 이상" 같은 질의가 불가능해진다.

**요구사항 §42의 검색 축을 다시 읽으니 문답 값이 없다.** 임장 기준은 지역명·임장 기간·재방문 의사·태그, 매물 기준은 단지/건물명·매물명·관심도·태그다. 요구사항이 요구하지 않은 능력을 근거로 구조를 무겁게 하고 있었다. 요구사항 §43(매물 비교)의 비교 항목에 문답이 있지만 그것은 선택한 매물을 ID로 불러 나란히 그리는 기능이라 저장 형태와 무관하다.

요구사항 §24는 오히려 이쪽을 가리킨다. 두 선택지 중 하나가 **"`ViewedProperty` 생성 시 적용되는 `QuestionVersion` 목록 Snapshot"** 이고, 이 문장을 문자 그대로 구현하면 매물이 자기 안에 스냅샷 목록을 품는 형태다.

저장소에도 선례가 있다. `Travel`은 엔티티이고 `TravelDay`·`TravelPlace`는 `travel.days` TEXT 컬럼의 JSON VO다(`TravelJpo.java:38`). `TravelPlace.placeKey`는 그 JSON 안의 UUID인데 `FileBoxItem.targetId`로 쓰인다 — 답변보다 바깥 참조가 많은 데이터도 VO로 다루고 있다.

### F.4 부수 효과

의도하지 않았지만 검증에서 잡혔던 결함 하나가 **구조적으로** 사라졌다.

설계 검증(부록 E.3)에서 HIGH로 잡힌 "완료 전이 레이스"는 `viewed_property.status`를 쓰기 전에 `property_answer`를 읽어 검증하는 교차 테이블 check-then-act였고, `SELECT ... FOR UPDATE`로 막았다. 답변이 매물 행 안으로 들어오면서 **읽는 대상과 쓰는 대상이 같은 행이 되어 `EntityJpo`의 `@Version`이 자동으로 막는다.** 질문 버전 동시 채번도 마찬가지로 유니크 제약 위반이 아니라 낙관적 잠금으로 처리된다.

### F.5 받아들인 대가

| 항목 | 내용 | 처리 |
| --- | --- | --- |
| 버전별 `answerCount` | 인덱스를 잃고 전건 스캔이 된다 | 본문 §11.5에 처리 방법과 전환 임계값(매물 2,000건) |
| 미완료 집계 | `GROUP BY`를 잃는다 | 매물 행에 정수 컬럼으로 파생 저장(본문 §3.6.1). 결과적으로 **더 싸다** |
| 매물 행 크기 | 매물당 수 KB | 목록·집계는 projection으로 `answers`를 읽지 않는다(본문 §3.6.2) |
| 문답 값 검색 | 불가능해진다 | 요구사항에 없다. 필요해지면 JSON을 테이블로 펼치는 마이그레이션 한 번(본문 §5.3) |

### F.6 함께 반영한 것

`PropertyAnswer` 스냅샷에 **`unit`을 추가했다.** 빠져 있으면 `NUMBER` 답변이 값만 남고 "만원"인지 "m2"인지 잃는데, `unit` 변경은 새 버전을 만드는 변경이라 마스터를 되짚어 복원할 수도 없다. 요구사항 §20을 지키려면 값 옆에 있어야 한다.

