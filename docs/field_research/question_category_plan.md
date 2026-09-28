# 임장 질문 대분류 추가 계획

> 명칭이 아직 정해지지 않아 문서에서는 "대분류"라고 부른다.
> 코드명은 `InspectionQuestionCategory`, 경로는 `/inspection-question-categories`를 임시로 쓴다. 머지 전에는 이름을 바꾸기 쉽다.

## 0. 확정된 결정

| # | 결정 | 코드에 반영되는 규칙 |
|---|---|---|
| 1 | 분류 필수 | 질문을 등록하거나 옮길 때 `categoryId`가 없으면 400. **DB 컬럼은 nullable로 둔다.** `ddl-auto=update`로는 행이 있는 테이블에 NOT NULL 컬럼을 추가할 수 없다. 필수 여부는 도메인 검증이 지키고, 기존 질문은 관리자가 지정할 때까지 과도기 상태로 둔다 |
| 2 | 기존 매물 스냅샷: B안 | 관리자가 분류 지정을 마친 뒤, 기존 매물 답변 스냅샷에 현재 마스터 기준으로 분류를 **한 번만** 채운다 |
| 3 | 비활성화: A안 | 활성 질문이 하나라도 있는 분류는 비활성화할 수 없다(409) |
| 4 | 분류에 sortOrder | 정렬은 `분류.sortOrder → 질문.sortOrder → questionId` 순서다. 질문의 `sortOrder`는 **분류 안에서의 순서**로 의미가 바뀐다 |
| 5 | 1단계 | 중분류는 계획에 없다. `parentId`를 두지 않는다 |
| 6 | 이름 중복 금지 범위 | **비활성 분류까지 포함해** 전체에서 중복을 금지한다 |

## 1. 도메인 규칙

### 분류 (`InspectionQuestionCategory`)
- 필드는 `name`, `sortOrder`, `enabled`다. `DomainEntity`를 상속한다.
- `name`은 필수, 앞뒤 공백 제거, 최대 50자다. 비활성 분류를 포함해 전체에서 중복을 금지한다(409).
- 물리 삭제는 없다. `enabled=false`만 있다.
- 비활성화는 활성 질문이 0개일 때만 허용한다.
- 등록할 때 `sortOrder`가 0 이하이면 맨 뒤(`max+1`)로 채번한다.

### 질문 (`InspectionQuestion`)
- `categoryId`를 추가한다.
- 등록할 때 `categoryId`는 필수이고, **활성 분류**만 허용한다.
- 분류 이동은 전용 API(`PATCH /inspection-questions/{id}/category`)로 한다. 옮기면 대상 분류의 맨 뒤(`max(sortOrder)+1`)에 배치한다.
- 분류 변경은 **버전을 올리지 않는다.** `required`, `sortOrder`와 같은 수집 정책 성격이다.
- 비활성 분류에 속한 질문은 다시 활성화할 수 없다. 이 규칙이 없으면 결정 3을 우회할 수 있다.
- 과도기에 `categoryId=null`인 질문은 조회와 스냅샷에서 "미분류"로 취급하고 맨 뒤에 둔다.

### 답변 스냅샷 (`PropertyAnswer`)
- `categoryId`, `categoryName`, `categorySortOrder`(Integer)를 추가한다. 매물 생성 시점의 값을 복사한다.
- 이후 분류 이름이나 순서가 바뀌어도 기존 매물은 그대로다. 질문 `sortOrder`를 스냅샷하는 기존 원칙과 같다.
- `@EqualsAndHashCode`가 이미 붙어 있어 새 필드도 비교 대상에 들어간다(JSON 컬럼 중복 UPDATE 방지).

## 2. 정렬을 한 곳에서 처리

지금은 스냅샷이 `findAllByEnabledTrueOrderBySortOrderAscIdAsc` 순서로 저장되고, 응답은 저장된 순서 그대로 내려간다. 이 구조로는 백필 뒤에 기존 매물의 답변 순서가 분류별로 정리되지 않는다.

→ **읽을 때 정렬한다.**
- spec에 `InspectionQuestionOrdering` 유틸을 두고 Comparator 두 개를 정의한다.
  - 질문용: `(category.sortOrder, nulls last) → question.sortOrder → id`
  - 답변용: `(categorySortOrder, nulls last) → sortOrder → questionId`
- 적용하는 곳:
  - `ViewedPropertyMapper.toPropertyAnswerRdos`
  - `InspectionSummarySupport.unansweredQuestions`
  - 질문 목록 조회
  - `materializeAnswers`: 저장 순서도 맞춰 둔다
- 이렇게 하면 백필은 배열 순서를 건드리지 않고 분류 필드만 채우면 된다.

## 3. API 계약

### 분류 관리 (신규, 쓰기 ADMIN / 조회 USER)

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/inspection-question-categories?includeDisabled=false` | `sortOrder`, `id` 순서. 응답에 `enabledQuestionCount`를 포함한다 |
| POST | `/inspection-question-categories` | `{ "name": "채광·환기", "sortOrder": 1 }` |
| PUT | `/inspection-question-categories/{categoryId}` | 이름 변경 `{ "name": "..." }` |
| PATCH | `/inspection-question-categories/{categoryId}/status` | `{ "enabled": false }`. 활성 질문이 있으면 409 |
| PUT | `/inspection-question-categories/order` | `[{ "categoryId": "...", "sortOrder": 1 }]`. 요청에 빠진 분류는 기존 순서를 유지한다 |

```json
// InspectionQuestionCategoryRdo
{ "categoryId": "INSPECTION_QUESTION_CATEGORY-0001", "name": "채광·환기",
  "sortOrder": 1, "enabled": true, "enabledQuestionCount": 4 }
```

### 질문 API 변경

| 대상 | 변경 |
|---|---|
| `POST /inspection-questions` | `InspectionQuestionCdo`에 `categoryId`(필수)를 추가한다 |
| `PATCH /inspection-questions/{id}/category` (신규) | `{ "categoryId": "..." }`. 대상 분류의 맨 뒤로 옮긴다 |
| `PATCH .../policy` | **변경 없음.** 여기에 `categoryId`를 필수로 넣으면 기존 FE 호출이 전부 400이 된다 |
| `PATCH .../status` | 비활성 분류의 질문을 다시 활성화하려 하면 400 |
| `PUT .../order` | 계약 변경 없음. FE가 분류 안에서의 순서를 보낸다 |
| `InspectionQuestionRdo` | `categoryId`, `categoryName`, `categorySortOrder`를 추가하고, 목록을 2절의 순서로 정렬한다 |

### 매물 문답 응답 변경
- `PropertyAnswerRdo`에 `categoryId`, `categoryName`, `categorySortOrder`를 추가한다. 백필 전의 과거 매물은 null이다.
- `UnansweredQuestionRdo`에 `categoryName`을 추가한다.
- 응답은 **평면 배열**이고 FE가 `categoryId`로 묶는다(단지 그룹핑 §11.2와 같은 원칙).

### 에러 코드 (`ErrorCode` 추가)
- `INSPECTION_QUESTION_CATEGORY_NOT_FOUND` (400): 기존 `*_NOT_FOUND`와 같은 상태 코드다
- `INVALID_INSPECTION_QUESTION_CATEGORY` (400)
- `INSPECTION_QUESTION_CATEGORY_DUPLICATED` (409)
- `INSPECTION_QUESTION_CATEGORY_IN_USE` (409)
- `INSPECTION_QUESTION_CATEGORY_CONFLICT` (409): `@Version` 낙관적 잠금 충돌. 질문 Store와 같은 방식으로 변환한다

## 4. 모듈별 작업

### slcn-spec
1. `entity/InspectionQuestionCategory.java`: 생성자, `rename`, `changeSortOrder`, `changeEnabled`
2. `InspectionQuestion`: `categoryId` 필드와 `moveCategory(categoryId, sortOrder)`
3. `PropertyAnswer`: 스냅샷 필드 3개와 `assignCategory(id, name, sortOrder)`(백필용, `categoryId`가 null일 때만 채우고 채웠는지 boolean을 반환)
4. `InspectionQuestionOrdering`: 2절의 Comparator
5. DTO
   - 신설: `InspectionQuestionCategoryCdo`, `InspectionQuestionCategoryUdo`, `InspectionQuestionCategoryStatusUdo`, `InspectionQuestionCategoryOrderUdo`, `InspectionQuestionCategoryRdo`, `InspectionQuestionCategoryMoveUdo`
   - 변경: `InspectionQuestionCdo`, `InspectionQuestionRdo`, `PropertyAnswerRdo`, `UnansweredQuestionRdo`
6. 매퍼
   - `InspectionQuestionCategoryMapper`를 신설한다.
   - `InspectionQuestionMapper.toInspectionQuestionRdo(question, category, answerCount)`로 확장한다.
   - `PropertyAnswerMapper.toPropertyAnswer(question, version, category)`로 확장한다.
7. `InspectionQuestionCategoryFacade`를 신설하고, `InspectionQuestionFacade`에 `moveInspectionQuestionCategory`를 추가한다.
8. 엔티티와 VO가 `facade.sdo`를 import하지 않는다(`docs/learning/domain-entity-must-not-depend-on-api-dto.md`).

### slcn-aggregate
1. `SequenceName.INSPECTION_QUESTION_CATEGORY`
2. 분류 저장소
   - `InspectionQuestionCategoryJpo`: 테이블 `inspection_question_category`(스키마 `slcn`), 인덱스 `(enabled, sort_order)`, `name` unique
   - `InspectionQuestionCategoryRepository`, `InspectionQuestionCategoryJpoMapper`, `InspectionQuestionCategoryStore`
3. `InspectionQuestionJpo`에 `categoryId` 컬럼과 인덱스 `(category_id, enabled)`를 추가하고, 매퍼를 갱신한다.
4. `InspectionQuestionRepository` 추가 메서드
   - `countByCategoryIdAndEnabledTrue`: 결정 3 검증용
   - 분류 안 max sortOrder 조회: 이동, 등록 채번용
   - 분류별 활성 질문 수 group by 집계: `enabledQuestionCount`용
5. `InspectionQuestionCategoryLogic`: 등록, 이름 변경, 상태 변경, 정렬. 비활성화할 때 `InspectionQuestionStore`로 활성 질문 수를 확인한다. Logic끼리는 서로 호출하지 않고 Store만 참조한다.
6. `InspectionQuestionLogic`
   - 등록: 활성 분류인지 검증하고 `sortOrder`를 채번한다(요청 값이 0 이하일 때).
   - `moveInspectionQuestionCategory`를 신설한다.
   - 상태 변경: 켤 때 분류가 활성인지 검증한다.
   - 분류 조회에는 `InspectionQuestionCategoryStore`를 쓴다.
7. `InspectionQuestionQueryFlow`: 목록 조회에 분류 맵을 합쳐 정렬한다.
8. `ViewedPropertyFlow.registerViewedProperty`: 활성 질문과 분류 맵을 함께 조회해 `materializeAnswers(property, questions, categoryMap)`로 넘긴다.
9. `ViewedPropertyLogic.materializeAnswers`: 분류를 스냅샷에 복사하고 정렬해서 저장한다.
10. `ViewedPropertyMapper`, `InspectionSummarySupport`: 읽을 때 정렬한다.

### slcn-rest / slcn-boot
1. `InspectionQuestionCategoryResource`를 신설한다.
2. `InspectionQuestionResource`에 `PATCH /{questionId}/category`를 추가한다.
3. 예외 핸들러에 새 예외를 추가한다.
4. `SecurityConfiguration`에 `/inspection-question-categories`, `/inspection-question-categories/**`의 POST, PUT, PATCH를 ADMIN으로 거는 matcher를 추가한다.

## 5. 백필 (결정 2, B안)

**실행 방식**: `ObjectStorageConfiguration.objectStorageMigrationRunner`의 선례를 따른다.
`ApplicationRunner`를 두고 `@ConditionalOnProperty(name = "slcn.inspection.category-backfill.enabled", havingValue = "true")` 조건으로 켠다. 기동할 때 1회 실행하고 결과를 로그로 확인한다. 상시 엔드포인트는 만들지 않는다.

**`InspectionQuestionCategoryBackfillLogic.backfill()` 규칙**
1. 사전 점검: `categoryId=null`인 질문이 있으면 아무것도 하지 않고 해당 질문 ID를 로그로 남긴 뒤 종료한다.
2. 질문 전체와 분류 전체를 메모리 맵으로 올린다.
3. 매물은 ID 목록을 먼저 읽고, 한 건씩 **별도 트랜잭션**으로 처리한다. 한 건의 충돌이 전체를 롤백하지 않게 한다.
4. 답변마다 스냅샷 `categoryId`가 null일 때만 현재 마스터 질문 → 분류 값으로 채운다. 값이 있으면 건드리지 않으므로 다시 실행해도 안전하다.
5. 바뀐 답변이 없는 매물은 저장하지 않는다(`@Version` 보존).
6. 리포트 `record BackfillReport(int updated, int skipped, int unresolved, int failed)`
   - `unresolved`: 마스터에 질문이 없거나 분류가 null인 답변이 있는 매물
   - `failed`: 충돌 등 예외. 다시 실행하면 처리된다

주의점:
- COMPLETED 매물도 대상이다. 분류는 표시용 메타데이터라 완료 조건에 영향이 없다.
- 백필은 **실행 시점의 분류 이름**을 스냅샷한다. 분류 이름은 백필 전에 확정한다.
- 사용자 쓰기와 겹치면 충돌이 날 수 있다. `failed > 0`이면 한 번 더 실행한다.

**운영 순서**
1. 배포한다. 이 시점부터 새 질문과 이동은 분류가 필수다. 기존 질문은 미분류로 표시된다.
2. 관리자가 분류를 만들고 기존 질문 전부를 분류에 지정한다.
3. `slcn.inspection.category-backfill.enabled=true`로 재기동하고, 로그에서 `unresolved=0`, `failed=0`을 확인한다.
4. 플래그를 내린다.
5. (선택) `inspection_question.category_id`에 NOT NULL을 수동으로 적용한다. `ddl-auto`는 이 제약을 걸어 주지 않는다.

## 6. 테스트

| 모듈 | 테스트 | 검증 내용 |
|---|---|---|
| spec | `InspectionQuestionCategoryTest` | 이름 변경, 상태 변경, `modifiedTime` 갱신 |
| spec | `InspectionQuestionOrderingTest` | 분류 순서 → 질문 순서, null 분류는 맨 뒤, 동점이면 id |
| spec | `PropertyAnswerTest` | `assignCategory`는 null일 때만 채움, 새 필드가 equals에 포함됨 |
| spec | `PropertyAnswerMapperTest`, `InspectionQuestionMapperTest` | 스냅샷과 Rdo에 분류가 복사됨 |
| aggregate | `InspectionQuestionCategoryLogicTest` | 이름 중복 409(비활성 포함), 활성 질문이 있을 때 비활성화 409, 정렬에서 빠진 항목 유지 |
| aggregate | `InspectionQuestionLogicTest` | 분류 누락, 없는 분류, 비활성 분류로 등록 거부. 이동하면 맨 뒤 배치, 버전 불변. 비활성 분류 질문 재활성화 거부 |
| aggregate | `ViewedPropertyLogicTest` | 스냅샷이 분류 순서대로 생성됨, 분류 이름을 바꿔도 기존 스냅샷 불변 |
| aggregate | `InspectionQuestionCategoryBackfillLogicTest` | 사전 점검 중단, null만 채움, 재실행 시 updated=0, 변경 없는 매물은 저장 안 함, 한 건 실패가 나머지에 영향 없음 |
| aggregate | `InspectionSummarySupportTest` | 미답 필수 문항의 정렬과 분류명 |
| rest | `InspectionQuestionCategoryResourceTest`, `InspectionQuestionResourceTest` | 계약과 에러 매핑 |
| boot | `SecurityConfigurationTest` | 분류 GET은 USER 200, 쓰기는 USER 403, ADMIN 200 |

**실제로 띄워서 확인할 것** (DB 통합 테스트가 없다):
- 테이블, 컬럼, unique 제약 생성
- `id_sequence`의 `INSPECTION_QUESTION_CATEGORY` 행 자동 생성
- group by 카운트 쿼리와 max sortOrder 쿼리
- 매물을 한 번 저장할 때 UPDATE 1회(`entity_version` +1)
- 백필 전후로 과거 매물 응답이 분류순으로 정렬되는지
- 백필을 두 번 실행해도 `entity_version`이 오르지 않는지

## 7. 문서
- `docs/field_research/api.md`에 분류 관리 절을 새로 만들고, 질문 관리 절(`categoryId`, `/category`)과 §4 문답 응답 예시, 백필 운영 절차를 갱신한다.
- `screen_design.md` §5.5(분류별 섹션, 분류 드래그, 미분류 배지)는 FE와 협의한 뒤 반영한다.

## 8. 커밋 단위
1. `feat: 질문 대분류 도메인과 저장소 추가` (spec 엔티티, Jpo, Store, Logic, 테스트)
2. `feat: 질문 대분류 관리 API 추가` (Resource, Security, 에러 코드)
3. `feat: 질문 등록·이동에 대분류를 필수로 적용` (질문 Logic과 Rdo)
4. `feat: 문답 스냅샷에 대분류를 싣고 읽기 시점 정렬로 전환`
5. `feat: 기존 매물 문답 대분류 백필 러너 추가`
6. `docs: api.md에 질문 대분류 계약 반영`
