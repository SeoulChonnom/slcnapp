# 서울 아파트 내집찾기 — BE 상세 구현 설계

> 상태: 구현 설계(Implementation Design). `docs/myseoulhouse/spec.md`(기능 명세, FE·BE 합의본)를 BE 코드 배치 수준까지 내린 문서다.
> 선행 문서: `spec.md`(명세 원본), `docs/module.md`(배치 규칙), `docs/architecture.md`(전체 구조), `docs/field_research/implementation_design.md`(같은 저장소의 직전 구현 설계, 관례 참고)
> FE 상세 설계는 이 문서의 범위가 아니다. FE와 맞닿는 계약은 명세 10장이 기준이고, 이 문서는 그 계약을 BE가 어떻게 만족하는지만 다룬다.

**인용 표기 규약**: `[명세 §N]`은 `spec.md`의 장, `본문 §N`은 이 문서의 절을 가리킨다.

---

## 1. 문서 목적과 범위

### 1.1 목적

명세가 정한 동작을 BE 구현자가 바로 코드로 옮길 수 있게 한다. 명세가 "무엇을"이라면 이 문서는 "어느 클래스가, 어떤 쿼리로, 어느 트랜잭션 안에서"다.

### 1.2 범위

- 포함: 외부 데이터 수집(실거래, 건축물대장, 학교, 정비사업, 행안부 좌표), 야간 배치, 통계·랭킹 산출, 조회 API 9종, 관리자 API 2종, 영속성, 설정, 보안, 테스트, 배포 순서
- 제외: 예산 계산 알고리즘. 계산은 FE가 한다[명세 §3]. BE는 정책 데이터만 내려준다(본문 §7.1)

### 1.3 전제

| 전제 | 설계에 주는 영향 |
| --- | --- |
| 커플 2인 서비스 (AGENTS.md Service Scale) | 조회 캐시, 분산 락, PostGIS, Spring Batch를 두지 않는다 |
| `ddl-auto: update`, 마이그레이션 도구 없음 | 테이블은 JPO가 만든다. 부분 인덱스·체크 제약은 쓰지 않는다. 유니크 인덱스는 배포 후 직접 확인한다(본문 §9.6) |
| DB 통합 테스트 없음 | 네이티브 SQL(통계·묶음·영역 조회)은 단위 테스트로 검증되지 않는다. 로컬 DB에서 직접 실행해 확인한다(본문 §12.4) |
| 외래키를 걸지 않는다 | 기존 도메인과 같다. 참조 ID는 plain 컬럼이고 애플리케이션이 검증한다 |
| `id_sequence` 상한 `0xFFFF`(65,535) | 거래·수집 단위·배치 기록·학교처럼 많거나 기계가 만드는 행은 UUID를 쓴다(본문 §3.1) |
| 서울 데이터 규모 | 단지 수천 개, 거래는 최근 40개월 약 20만 행. 수집 범위가 40개월로 정해져 있어 통계 대상 행 수가 계속 늘지 않는다 |

---

## 2. 명세 → 설계 결정 요약

| 명세 요구 | 설계 결정 | 본문 |
| --- | --- | --- |
| 예산은 브라우저 계산[명세 §3] | BE는 `GET /house/budget-policy`로 정책 한 행만 준다. 입력값을 받는 API가 없다 | §7.1 |
| 단지 식별 = `aptSeq`[명세 §8] | `house_complex.apt_seq` 유니크. 실거래 수집 중 처음 보면 단지를 만든다. 미매칭 거래가 생기지 않는다 | §5.2 |
| 좌표 = 행안부[명세 §8] | 기존 `AddressGateway` + `UtmkToWgs84Converter`를 주입받아 쓴다. 카카오 로컬 API는 쓰지 않는다 | §4.4 |
| 별도 실행기 없음[명세 §7] | `@Scheduled` 하나(`HouseNightlyScheduler`)가 `HouseNightlyFlow.run(today)`를 부른다. 단계 6개를 순서대로 실행한다 | §5 |
| 단계별 기록[명세 §9] | 단계마다 `house_batch_run` 1행. `step` 값은 명세 9장 목록 그대로 | §5.1 |
| 통계는 통째로 교체[명세 §7] | 6단계는 한 트랜잭션에서 이상치 재판정 → `house_complex_area_stat` DELETE+INSERT → `house_ranking` DELETE+INSERT | §5.6 |
| 비회원 접근 불가[명세 §1] | `permitAll`을 추가하지 않는다. `/house/admin/**`만 ADMIN 매처를 추가한다 | §8.2 |
| 외부 호출 중 트랜잭션 금지 | 호출은 Flow에서 트랜잭션 밖에서 한다. 저장만 Logic의 `@Transactional` 메서드로 짧게 한다 | §5 |
| 일일 호출 상한[명세 §7] | API마다 상한을 따로 둔다(공공데이터포털은 API별 활용신청·한도). 명세의 단일 상한에서 바꾼다 | §4.5, §13 |

---

## 3. 도메인 모델

### 3.1 엔티티 개요와 ID

| 도메인 엔티티 (`spec/house/entity`) | 테이블 | 성격 | ID |
| --- | --- | --- | --- |
| `HouseComplex` | `house_complex` | 원본(배치가 생성·보강) | `id_sequence` `HOUSE_COMPLEX` → `HOUSE_COMPLEX-0a1f` |
| `HouseTrade` | `house_trade` | 원본 | UUID |
| `HouseCollectUnit` | `house_collect_unit` | 원본(수집 진행 상태) | UUID |
| `HouseBudgetPolicy` | `house_budget_policy` | 원본(수동 SQL로만 추가) | UUID (`gen_random_uuid()`) |
| `HouseBatchRun` | `house_batch_run` | 원본(기록) | UUID |
| `HouseSchool` | `house_school` | 참조 데이터 | UUID |
| `HouseRedevelopment` | `house_redevelopment` | 참조 데이터 | UUID |
| — | `house_complex_area_stat` | 파생(6단계가 재생성) | 복합 PK (`complex_id`, `area_bucket`) |
| — | `house_ranking` | 파생 | 복합 PK (`ranking_type`, `area_bucket`, `rank_no`) |
| — | `house_complex_school` | 파생(4단계가 재생성) | 복합 PK (`complex_id`, `level`) |
| — | `house_complex_redevelopment` | 파생(5단계가 재생성) | 복합 PK (`complex_id`, `redevelopment_id`) |

- 원본 JPO는 `DomainEntityJpo`(String `id`, `@Version entityVersion`, `registeredTime`/`modifiedTime` epoch ms)를 상속한다.
- 파생 JPO는 상속하지 않는다. `@IdClass` 복합 키 엔티티이고 `@Version`이 없다. 쓰기는 네이티브 SQL(DELETE + INSERT)로만 하고, JPA로는 읽기만 한다. 도메인 엔티티를 두지 않고 Store가 바로 Rdo 조립용 Pdo(projection)를 돌려준다.
- 단지 ID에 `id_sequence`를 쓰는 이유: 비교 URL `cmp`에 실리므로 짧은 것이 좋고(`HOUSE_COMPLEX-0a1f` 18자, UUID는 36자), 서울 아파트 단지 수(수천)가 상한 65,535보다 충분히 작다. `:`와 `,`가 들어가지 않는다[명세 §10 비교표].
- UUID 행은 Store에서 `jpo.setId(UUID.randomUUID().toString())`로 채운다(`InspectionTagStore`와 같은 방식).

### 3.2 HouseComplex

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `aptSeq` | String(20) | 실거래 단지 일련번호. 대체 키일 때는 `ALT:{sggCd}{umdCd}{bonbun}{bubun}:{정규화명 해시 8자}` (본문 §5.2) |
| `lawdCd` | String(5) | 구 코드 (`sggCd`) |
| `umdCd` | String(5) | 법정동 코드 뒤 5자리 |
| `umdName` | String(50) | |
| `bonbun`, `bubun` | String(4) | 0 채움 4자리. 건축물대장 조회 키 |
| `aptName` | String(100) | 가장 최근 거래의 단지명 |
| `roadName`, `roadBonbun`, `roadBubun` | String | 실거래 도로명 필드. 행안부 검색어 조립용 |
| `roadAddress` | String(200) | 행안부 검색으로 확정한 도로명주소(저장 가능) |
| `addressOverride` | `AddressOverride` VO(JSON TEXT) | 관리자 보정값 `{roadAddress, bonbun, bubun}`. null이면 보정 없음 |
| `latitude`, `longitude` | Double | WGS84. `geocodeStatus = DONE`일 때만 값이 있다 |
| `geocodeStatus`, `geocodeAttempts` | enum, int | |
| `registerStatus`, `registerAttempts`, `registerSyncedDate` | enum, int, LocalDate | |
| `buildYear` | Integer | 실거래 건축년도 |
| `approvalDate` | LocalDate | 건축물대장 사용승인일 |
| `households` | Integer | |
| `farPct`, `bcrPct` | BigDecimal(7,2) | 용적률, 건폐율 |
| `farSource` | enum `FarSource` | RECAP \| TITLE |

### 3.3 HouseTrade

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `dedupKey` | String(64) | SHA-256 hex (본문 §5.2.3) |
| `complexId` | String | NOT NULL. 거래는 항상 단지에 연결된다 |
| `lawdCd`, `dealYm` | String(5), String(6) | 수집 단위 키. 원천 삭제 판정 범위 |
| `dealDate` | LocalDate | |
| `exclusiveArea` | BigDecimal(7,2) | 전용면적 원값 |
| `areaBucket` | enum `AreaBucket` | B59 \| B84 \| OTHER |
| `amountManwon` | Long | |
| `floor` | Integer | 지하는 음수, 빈 값은 null |
| `aptDong` | String(30) | 빈 값은 null |
| `buildYear` | Integer | |
| `dealingType` | enum `DealingType` | BROKER \| DIRECT \| UNKNOWN |
| `cancelled`, `cancelledDate` | boolean, LocalDate | |
| `outlier` | boolean | 6단계가 매번 다시 판정 |
| `removed` | boolean | 원천 삭제 (본문 §5.2.4) |

### 3.4 HouseCollectUnit · HouseBatchRun · HouseBudgetPolicy

- `HouseCollectUnit`: `lawdCd`, `dealYm`, `status`(`CollectUnitStatus` NEVER \| DONE \| FAILED), `lastSuccessDate`(LocalDate), `attempts`(연속 실패 횟수, 성공 시 0).
- `HouseBatchRun`: `runDate`, `step`(`BatchStep`), `status`(`BatchRunStatus` SUCCESS \| PARTIAL \| FAILED \| SKIPPED), `apiCalls`, `detail`(`Map<String, Object>` JSON TEXT, 단계별 건수), `startedTime`, `finishedTime`, `error`(예외 클래스명과 우리가 만든 메시지만. 외부 응답 본문·URL 금지).
- `HouseBudgetPolicy`: 명세 9장 컬럼 그대로. `priceCaps`(`List<PriceCap>`), `costRates`(`CostRates`)는 JSON TEXT VO다.

### 3.5 VO / enum

| 이름 | 위치 | 값·필드 |
| --- | --- | --- |
| `AreaBucket` | `entity/vo` | B59(55.00 ≤ x < 65.00), B84(80.00 ≤ x < 90.00), OTHER. `of(BigDecimal)`이 경계를 판정한다. 경계 기본값은 `HouseConstant`, 설정으로 덮을 수 있다[명세 §8] |
| `DealingType` | `entity/vo` | BROKER("중개거래"), DIRECT("직거래"), UNKNOWN(그 밖, 빈 값) |
| `GeocodeStatus`, `RegisterStatus` | `entity/vo` | PENDING, DONE, FAILED |
| `CollectUnitStatus` | `entity/vo` | NEVER, DONE, FAILED |
| `BatchStep` | `entity/vo` | COLLECT_UNIT_PREPARE, TRADE_COLLECT, COMPLEX_ENRICH, SCHOOL_SYNC, REDEVELOPMENT_SYNC, STATS_BUILD |
| `BatchRunStatus` | `entity/vo` | SUCCESS, PARTIAL, FAILED, SKIPPED |
| `RankingType` | `entity/vo` | LEADER, RISE, FALL, VOLUME |
| `RankingBadge` | `entity/vo` | LOW_SAMPLE, NO_TRADE |
| `SampleStatus` | `entity/vo` | OK, LOW, INSUFFICIENT. `of(cntFrom, cntTo)`: 둘 중 작은 값 < 2 → INSUFFICIENT, < 3 → LOW, 그 밖 OK |
| `SummaryGroupBy` | `entity/vo` | DISTRICT, DONG |
| `FarSource` | `entity/vo` | RECAP, TITLE |
| `SchoolLevel` | `entity/vo` | E, M, H |
| `PriceCap` | `entity/vo` | `maxPriceManwon`(Long, 마지막만 null), `capManwon` |
| `CostRates` | `entity/vo` | `acquisitionTax`, `firstTimeRelief`, `brokerage{tiers[], vatRatio}`, `registrationRatePct`. 명세 10장 JSON과 같은 구조 |
| `AddressOverride` | `entity/vo` | `roadAddress`, `bonbun`, `bubun` |

**JSON TEXT 컬럼 VO는 모두 `@EqualsAndHashCode`를 붙인다.** 없으면 Hibernate 더티 체크가 flush마다 변경으로 판정해 UPDATE가 한 번 더 나가고 `@Version`이 2씩 오른다(2026-09-21 임장 질문 수정에서 실제로 발생). 대상: `PriceCap`, `CostRates`와 그 안의 중첩 VO 전부, `AddressOverride`. `HouseBatchRun.detail`은 `Map`이라 자체 equals가 있다.

---

## 4. 외부 연동

### 4.1 공통 (`DataGoKrConfiguration`)

- `RestClient` + `SimpleClientHttpRequestFactory`(연결·읽기 타임아웃 `slcn.house.data-go-kr.timeout-seconds`, 기본 10초). 1 미만은 시작 시 `IllegalStateException`(`JusoConfiguration`과 같은 규칙).
- 서비스 키가 비면 `DisabledTradeGateway`, `DisabledBuildingRegisterGateway`, `DisabledSchoolGateway`를 빈으로 등록한다. 이 구현체들은 호출 시 `HouseDataSourceUnavailableException`을 던진다. 야간 Flow는 시작할 때 키 설정 여부를 먼저 보고, 없으면 해당 단계를 SKIPPED로 남긴다.
- **서비스 키 인코딩**: 공공데이터포털은 Encoding 키와 Decoding 키를 준다. `RestClient`는 URI 템플릿 변수를 한 번 인코딩하므로 **Decoding 키**를 설정값으로 받고 `{serviceKey}` 템플릿 변수로 넘긴다. Encoding 키를 넣으면 `%`가 두 번 인코딩되어 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`가 난다. 설정 주석에 적는다.
- **로그**: 요청 URL에 키가 들어 있으므로 예외 메시지·cause·응답 본문을 로그에 남기지 않는다. 예외 클래스 단순명과 우리 쪽 결과 코드만 남긴다(`JusoAddressGateway.get`과 같은 방식).
- **재시도**: 네트워크 오류·타임아웃·5xx·해석 불가는 1초·2초·4초 대기 후 최대 3회 재시도. 대기는 주입 가능한 `HouseSleeper` 인터페이스로 해서 테스트에서 0초로 바꾼다. 인증 키 오류와 일일 한도 초과는 재시도하지 않는다.
- **응답 형식**: 세 API 모두 JSON을 준다(2026-10-21 실제 호출로 확인). 실거래·건축물대장은 `_type=json`, 학교는 `type=json`이다. 기존 Jackson으로 `JsonNode`를 읽고 XML 파서는 두지 않는다. 본문이 JSON이 아니면 UPSTREAM으로 분류한다. 게이트웨이 안에서만 파싱하고, 밖으로는 `TradeRecord` 같은 우리 레코드만 내보낸다.
- **값 읽기**: 같은 필드가 행마다 숫자로도 문자열로도 온다(`jibun`이 `652`와 `"91-16"`, `bonbun`이 `"0652"`와 `652`, `aptDong`도 같다). 그래서 모든 값을 `JsonNode.asText()`로 읽은 뒤 변환한다. 빈 값은 `null`이 아니라 공백 한 칸(`" "`)으로 오므로 trim 후 빈 문자열이면 null로 본다. 이 규칙은 `DataGoKrResponseReader.text(node, field)` 한 곳에 둔다.
- **응답 봉투**: 정상 응답은 실거래·건축물대장이 `response.header`/`response.body`, 학교가 최상위 `header`/`body`다. 게이트웨이 단계 오류(타임아웃·키 오류 등)는 HTTP 503 등과 함께 `OpenAPI_ServiceResponse.cmmMsgHeader`(`errMsg`, `returnReasonCode`)로 온다. 두 형태를 모두 읽는다.
- **결과 코드 분류** (`DataGoKrResultCode`): 정상 코드는 실거래 `000`, 건축물대장·학교 `00`이다. 헤더 `resultCode`가 정상이 아니거나 `cmmMsgHeader`가 오면 `errMsg`로 다음과 같이 분류한다.

  | 분류 | 예 | 처리 |
  | --- | --- | --- |
  | KEY_REJECTED | `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`, `SERVICE_ACCESS_DENIED_ERROR` | 단계 중단, FAILED, 재시도 없음 |
  | QUOTA_EXCEEDED | `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR` | 단계 중단, PARTIAL. 남은 단위는 다음 날 |
  | NO_DATA | `NODATA_ERROR` 또는 `totalCount = 0` | 정상(빈 결과) |
  | UPSTREAM | `SERVICETIMEOUT_ERROR`(`returnReasonCode` 05) 등 그 밖 | 재시도 후 실패 처리 |

### 4.2 실거래 (`MolitTradeGateway implements TradeGateway`)

```text
GET {base}/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev
    ?serviceKey={key}&LAWD_CD={lawdCd}&DEAL_YMD={yyyyMM}&pageNo={n}&numOfRows=1000&_type=json
```

- 포트: `TradeFetchResult fetchAll(String lawdCd, YearMonth ym, CallBudget budget)`. 모든 페이지를 받아 `List<TradeRecord>`와 `complete`(모든 페이지 성공 여부)를 돌려준다. `totalCount`를 보고 `ceil(totalCount / 1000)`쪽을 받는다. 서울 구·월 단위는 대부분 1쪽이다.
- 페이지 하나를 부를 때마다 `CallBudget.consume()`을 먼저 한다. 예산이 0이면 호출하지 않고 `complete = false`로 돌려준다.
- 확인 결과(2026-10-21, 강남·종로·노원 2026-08분 670건): `aptSeq` 누락 0건(형식 `"11680-283"`), `floor`는 모두 정수, `cdealDay`는 `"26.08.28"` 형식, 해제 행 19건. `items`가 빈 문자열로 오는 0건 응답과 행이 1건일 때 `item`이 배열이 아닌 객체로 오는 경우를 함께 처리한다.
- 필드 변환 (모든 값은 위 "값 읽기" 규칙으로 문자열로 읽은 뒤 변환):

  | 응답 필드 | `TradeRecord` | 변환 |
  | --- | --- | --- |
  | `aptSeq` | `aptSeq` | trim, 빈 값은 null |
  | `sggCd`, `umdCd`, `umdNm` | `lawdCd`, `umdCd`, `umdName` | trim |
  | `bonbun`, `bubun` | 같음 | 숫자만 남기고 4자리 0 채움. 비면 null |
  | `aptNm` | `aptName` | trim |
  | `roadNm`, `roadNmBonbun`, `roadNmBubun` | `roadName` 등 | 본번·부번 앞 0 제거 |
  | `excluUseAr` | `exclusiveArea` | BigDecimal, 소수 둘째 자리 반올림 |
  | `dealAmount` | `amountManwon` | 공백·쉼표 제거 후 long ("82,500" → 82500) |
  | `dealYear`, `dealMonth`, `dealDay` | `dealDate` | LocalDate |
  | `floor` | `floor` | int, 비면 null |
  | `aptDong` | `aptDong` | trim, 비면 null |
  | `buildYear` | `buildYear` | int, 비면 null |
  | `cdealType` | `cancelled` | 값이 있으면(예: "O") true |
  | `cdealDay` | `cancelledDate` | "yy.MM.dd" → 20yy년. 해석 실패 시 null(해제 여부는 유지) |
  | `dealingGbn` | `dealingType` | "중개거래" → BROKER, "직거래" → DIRECT, 그 밖 UNKNOWN |

- 한 행이라도 필수값(`dealAmount`, 날짜, `excluUseAr`)을 해석하지 못하면 그 행만 버리고 `detail.skippedRows`에 센다. 단위 전체를 실패로 만들지 않는다.

### 4.3 건축물대장 (`BuildingRegisterGatewayImpl implements BuildingRegisterGateway`)

```text
GET {base}/1613000/BldRgstHubService/getBrRecapTitleInfo  (총괄표제부)
GET {base}/1613000/BldRgstHubService/getBrTitleInfo       (표제부)
    ?serviceKey={key}&sigunguCd={lawdCd}&bjdongCd={umdCd}&platGbCd=0&bun={bonbun}&ji={bubun}&numOfRows=100&pageNo=1&_type=json
```

- 데이터셋: 국토교통부_건축HUB_건축물대장정보 서비스(공공데이터포털 15134735). 개발계정 일일 10,000건, 이용허락범위 제한 없음.
- 확인 결과(2026-10-21): 표제부는 경로·필드(`hhldCnt`, `vlRat`, `bcRat`, `useAprDay`, `mainPurpsCdNm`, `totArea`, `dongNm`)가 위와 같다. 지번은 `bun`/`ji` 4자리 0 채움이다. 총괄표제부는 이날 `SERVICETIMEOUT_ERROR`(HTTP 503)였으나 2026-11-03에는 정상 응답했다(일시 장애).
- 확인 결과(2026-11-03): 총괄표제부가 정상이어도 오래된 단지는 `vlRat`·`bcRat`이 0이고 `useAprDay`가 빈 값이다(예: 개포동 652 → `hhldCnt` 405만 있음). 로컬 실행에서 단지 5개 모두 사용승인일을 표제부로 채워 단지당 2회를 썼다.
- 포트: `RegisterLookupResult find(lawdCd, umdCd, bonbun, bubun, CallBudget, RecapFailureTracker)`. 결과는 FOUND·NOT_FOUND·FAILED·BUDGET_EXHAUSTED·QUOTA_EXCEEDED·KEY_REJECTED다. `Optional`로는 "결과 없음(즉시 FAILED)"과 "호출 실패(attempts + 1)"를 가를 수 없다. 연속 실패 상태는 실행마다 새로 만드는 `RecapFailureTracker`가 든다.
- 규칙[명세 §8] — **필드 단위 대체**:
  1. 총괄표제부를 조회해 세대수 `hhldCnt`, 용적률 `vlRat`, 건폐율 `bcRat`, 사용승인일 `useAprDay`(yyyyMMdd)를 채운다.
  2. 넷 중 하나라도 비었으면 표제부를 조회해 **빈 필드만** 채운다. 주용도(`mainPurpsCdNm`)가 "공동주택"인 동만 모은다. 세대수는 합, 나머지는 연면적(`totArea`)이 가장 큰 동의 값이다.
  3. `farSource`는 용적률을 실제로 가져온 곳(RECAP/TITLE)이고, 용적률이 없으면 null이다.
  4. 넷 다 없으면 NOT_FOUND. 호출은 최대 2번이다. 총괄표제부 값이 있어도 표제부가 UPSTREAM으로 실패하면 FAILED(attempts + 1)로 둔다. 일시 장애로 일부 값만 DONE이 되지 않게 하기 위해서다.
- `vlRat`·`bcRat`·`hhldCnt`가 `0`이면 값이 없는 것으로 보고 null로 둔다.
- 30일 갱신 조회가 NOT_FOUND면 기존 값을 유지하고 `registerSyncedDate`만 오늘로 바꾼다. 일시적인 빈 응답으로 좋은 값이 지워지지 않게 하기 위해서다.
- 총괄표제부가 UPSTREAM으로 실패하면 재시도하지 않고 바로 표제부로 넘어간다. 한 실행에서 총괄표제부가 연속 3번 UPSTREAM이면 남은 단지는 총괄표제부를 건너뛴다. 타임아웃이 단지마다 재시도로 이어지면 배치가 몇 시간 늘어나기 때문이다. 이렇게 표제부 값으로 채운 단지도 30일 뒤 갱신(§5.3) 때 총괄표제부부터 다시 조회한다.

### 4.4 행안부 좌표 (기존 `AddressGateway` 재사용)

- `ComplexGeocoder`(aggregate `house/collect`)가 `AddressGateway`와 `UtmkToWgs84Converter`를 주입받는다. 둘 다 `aggregate/inspection/geo`에 있다. 패키지 이동은 이 작업 범위가 아니다. 두 도메인이 같은 클래스를 쓴다는 점만 클래스 주석에 남긴다.
- 검색어 순서[명세 §8]:
  1. `addressOverride.roadAddress`가 있으면 그것
  2. `"서울특별시 {구 이름} {roadName} {roadBonbun}[-{roadBubun}]"` (도로명 필드가 있을 때)
  3. `"서울특별시 {구 이름} {umdName} {bonbun 앞 0 제거}[-{bubun 앞 0 제거}]"` (지번, 부번이 0이면 생략)
- 각 검색어로 `search(keyword, 1, 1)`. 후보가 있으면 첫 후보의 `CoordKey`로 `findEntrance` → `convert`. 성공하면 `roadAddress`에 후보의 `roadAddr`를 저장한다.
- 결과 분류:
  - 세 검색어 모두 후보가 없거나 `AddressCoordinateNotFoundException`이면 **즉시 FAILED**. 다시 해도 같은 결과다.
  - `InvalidAddressKeywordException`이면 다음 검색어로 넘어간다.
  - `AddressLookupUnavailableException`(503/502)이면 `geocodeAttempts + 1`. 3이 되면 FAILED, 아니면 PENDING으로 남겨 다음 날 다시 한다. 키 미설정(503)이면 단계 전체를 SKIPPED로 끝낸다.
- 행안부 호출은 단지당 최대 2번(검색 + 좌표)이 정상이고, 검색어 대체가 일어나면 최대 4번이다. 상한은 `slcn.house.call-limit.juso`(기본 4,000)다.
- `AddressCoordinateNotFoundException`은 그 검색어만 실패로 보고 다음 검색어로 넘어간다. 모든 검색어가 실패하면 NOT_FOUND(즉시 FAILED)다.
- **순간 호출 제한**(2026-11-03 실측): 쉬지 않고 연달아 부르면 20건 남짓에서 "짧은 시간 동안 다량의 주소검색 요청" 오류가 난다. 기존 `JusoAddressGateway`는 이를 upstream 실패로 던진다. 일일 한도는 명시되어 있지 않다.
  - `ComplexGeocoder`는 행안부 호출이 끝날 때마다(성공·실패 모두) `slcn.house.juso-interval-millis`(기본 1,000)만큼 쉰다. 로컬에서 20회 연속 호출에 오류가 없었다. 4,000회면 약 70분이다.
  - upstream 실패가 **연속 3번**이면 그날 좌표 처리를 멈추고(PARTIAL, `JUSO_UPSTREAM_STREAK`), 그 3개 단지의 attempts는 올리지 않는다. 우리 쪽 호출 속도 때문에 단지가 FAILED가 되면 안 된다. 연속 3번이 되기 전의 개별 실패는 attempts + 1이다.

### 4.5 학교 위치 (`SchoolStandardDataGateway implements SchoolGateway`)

```text
GET {school-base}/openapi/tn_pubr_public_elesch_mskul_lc_api
    ?serviceKey={key}&pageNo={n}&numOfRows=1000&type=json&cddcNm=서울특별시교육청
```

- 호스트가 다른 두 API와 다르다(`api.data.go.kr`, `s` 없음). 그래서 `slcn.house.data-go-kr.school-base-url`을 따로 둔다(§8.1).
- 확인 결과(2026-10-21): 필드는 `schoolId`, `schoolNm`, `schoolSe`(초등학교/중학교/고등학교), `operSttus`, `lnmadr`, `rdnmadr`, `latitude`, `longitude`(문자열), `referenceDate`. `cddcNm=서울특별시교육청`으로 서울만 받을 수 있다. 1,313건, 2쪽이다(전국은 12,004건).
- 서울 필터를 서버에서 걸어도 소재지 주소(`lnmadr`)가 "서울특별시"로 시작하는지 한 번 더 본다. 학교급은 "초등학교" → E, "중학교" → M, "고등학교" → H이고, 그 밖(특수학교 등)은 버린다. `operSttus`가 "운영"이 아니면 버린다.
- 상한은 `slcn.house.call-limit.school`(기본 100)이다.

### 4.6 정비사업 (`RedevelopmentGateway`)

데이터셋이 미확정이다[명세 §13 #2]. 1차 구현은 포트와 `DisabledRedevelopmentGateway`만 둔다. 5단계는 항상 SKIPPED가 되고, 비교표 `redevelopments`는 항상 `null`("정보 없음")이다. 데이터셋이 정해지면 구현체 하나만 추가한다.

### 4.7 호출 예산 (`CallBudget`)

- 야간 실행 1회마다 API별로 하나씩 만든다: `trade`, `buildingRegister`, `school`, `juso`.
- 공공데이터포털 한도는 활용신청한 API마다 따로 매겨진다. 명세 7장의 단일 상한(`daily-call-limit`)을 API별 설정으로 나눈다(본문 §8.1, §13).
- 수동 재기동 등으로 같은 날 두 번 돌 수 있다. 그래서 예산은 "오늘 이미 쓴 호출 수"를 빼고 시작한다. 같은 날 여러 번 실행해도 한도를 넘지 않는다.
  - 실거래(2단계)는 그날 `TRADE_COLLECT` 기록의 `api_calls` 합을 뺀다.
  - 3단계는 두 API를 쓰므로 `detail`에 `jusoCalls`, `buildingRegisterCalls`를 따로 남기고, 그날 `COMPLEX_ENRICH` 기록의 이 값들을 합쳐 API별로 뺀다. `api_calls`는 두 값의 합계다.

---

## 5. 야간 배치 상세

### 5.1 진입점과 단계 기록

```java
@Scheduled(cron = "${slcn.house.nightly-cron:-}", zone = "Asia/Seoul")
public void runNightly() { houseNightlyFlow.run(LocalDate.now(houseClock)); }
```

- `HouseNightlyScheduler`는 `aggregate/house/logic`에 둔다(`RawUploadCleanupScheduler`와 같은 위치 규칙). 예외를 밖으로 던지지 않고 `log.error`만 한다. 스케줄러 스레드가 죽지 않게 하기 위해서다.
- `HouseNightlyFlow.run(today)`는 단계를 순서대로 부르고, 단계마다 `BatchStepRecorder`가 시작 시각·종료 시각·상태·호출 수·`detail`을 `house_batch_run`에 저장한다. 한 단계의 예외는 그 단계를 FAILED로 남기고 다음 단계로 넘어간다[명세 §7].
- `houseClock`은 `HouseConfiguration`이 등록하는 `Clock.system(ZoneId.of("Asia/Seoul"))`다. 테스트는 `Clock.fixed`로 바꾼다. 조회 Flow도 "오늘"을 이 Clock으로 얻는다.
- 단계 상태 결정:

  | 상황 | 상태 |
  | --- | --- |
  | 할 일을 다 했다 | SUCCESS |
  | 일부만 했다(예산 소진, 한도 초과, 일부 단위 실패) | PARTIAL |
  | 시작하자마자 실패했다(키 거절, 예외) | FAILED |
  | 할 필요가 없다(갱신 시점 전, 키 없음, 데이터셋 없음) | SKIPPED |

### 5.2 단계 1·2 — 수집 단위 준비와 실거래 수집

#### 5.2.1 수집 단위 준비 (`COLLECT_UNIT_PREPARE`)

- 대상 월: `YearMonth.from(today).minusMonths(39)` \~ `YearMonth.from(today)` (40개월). 예: 2026-10-20 → 2023-07 \~ 2026-10. 3년 전 3개월 창(2023-07-21 \~ 2023-10-20)을 덮는다.
- 25개 구 × 40개월 = 1,000행을 `INSERT … ON CONFLICT (lawd_cd, deal_ym) DO NOTHING`으로 보장한다(네이티브 쿼리). 범위 밖으로 밀려난 옛 단위는 지우지 않고 수집 대상에서만 뺀다.

#### 5.2.2 수집 순서 (`TRADE_COLLECT`)

`HouseCollectUnitStore.findDueUnits(today)`가 아래 순서로 정렬된 목록을 돌려준다. Flow는 예산이 남는 동안 앞에서부터 처리한다.

1. 최근 3개월 단위(`deal_ym ∈ {이번 달, −1, −2}`) 전부. 매일 다시 받는다.
   - 3개월 창은 4개 월에 걸치지만(예: 07-21 \~ 10-20), 가장 오래된 월의 해당 일자 계약은 신고 기한 30일이 이미 지났다. 그래서 매일 다시 받는 대상은 3개 월로 충분하다.
2. `status = FAILED`, `attempts` 적은 순
3. `status = NEVER`, `deal_ym` 내림차순(최근부터 과거로)
4. 매월 1일에만: 4\~12개월 전 단위 중 `last_success_date`가 이번 달 1일보다 이른 것

단위 하나의 처리:

```text
result = tradeGateway.fetchAll(lawdCd, ym, tradeBudget)        // 트랜잭션 밖
if 예산 소진으로 시작도 못 함 → 단위 상태 그대로, 단계 PARTIAL, 루프 종료
if 실패(재시도 후) → houseCollectUnitLogic.markFailed(unit)      // attempts+1, FAILED
else → houseTradeLogic.applyUnit(unit, result, today)           // 아래, 단위당 트랜잭션 1개
```

#### 5.2.3 단위 반영 (`HouseTradeLogic.applyUnit`, `@Transactional`)

1. **단지 확정**: 레코드의 `aptSeq`별로 `HouseComplexStore.findByAptSeqIn`. 없는 `aptSeq`는 단지를 새로 만든다.
   - 새 단지는 `geocodeStatus = PENDING`, `registerStatus = PENDING`이다.
   - 기존 단지는 그 단위에서 가장 최근 계약일인 레코드로 표시 정보(`aptName`, `umdName`, `bonbun`, `bubun`, 도로명, `buildYear`)를 갱신한다. 값이 같으면 건드리지 않아 UPDATE가 나가지 않게 한다.
   - `aptSeq`가 빈 레코드는 대체 키 `ALT:{sggCd}{umdCd}{bonbun}{bubun}:{sha256(정규화명) 앞 8자}`를 `aptSeq` 자리에 쓴다. 정규화 = 공백 제거, 괄호와 그 안 내용 제거, "아파트" 제거, 영문 소문자화[명세 §8].
   - 동시에 단지를 만드는 경로는 이 배치 하나뿐이다. 그래도 `apt_seq` 유니크 제약을 둔다.
2. **중복 제거 키**: 레코드를 응답 순서대로 돌면서 `(aptSeq, aptDong, floor, excluUseAr 원문, dealDate, amountManwon)`이 같은 것끼리 출현 순번 1, 2, …를 붙인다. `dedupKey = sha256Hex(필드들을 "|"로 이은 문자열 + "|" + 순번)`. null은 빈 문자열로 넣는다. 계산은 `DedupKeyFactory`(순수 클래스)가 한다.
3. **upsert**: `INSERT … ON CONFLICT (dedup_key) DO UPDATE SET cancelled, cancelled_date, dealing_type, removed = false, modified_time WHERE (값이 달라졌을 때만)`. 네이티브 쿼리, 행 단위(서울 구·월 단위는 수백 행).
   - 해제는 `cdealType`만 바뀌고 키는 같으므로 같은 행이 갱신된다[명세 §7].
   - JPA `saveAll`을 쓰지 않는 이유: 기존 행을 먼저 SELECT해야 해서 행마다 두 번 왕복한다. `ON CONFLICT` 한 문장이 동시성도 DB가 보장한다.
4. **원천 삭제**: `result.complete`일 때만 실행한다.
   - `UPDATE house_trade SET removed = true WHERE lawd_cd = ? AND deal_ym = ? AND removed = false AND dedup_key <> ALL(?)` (이번 응답 키 배열)
   - 다시 나타난 행은 3번 upsert에서 `removed = false`로 돌아온다.
5. **단위 상태**: `status = DONE`, `last_success_date = today`, `attempts = 0`.
6. `detail`에 단위별 건수(upsert, 신규, 해제, removed, skippedRows)를 누적한다.

### 5.3 단계 3 — 단지 보강 (`COMPLEX_ENRICH`)

- 좌표 대상: `geocode_status = PENDING`, 최근 12개월 거래 건수 내림차순(화면에 자주 나올 단지부터). `juso` 예산이 남는 동안 처리한다.
- 건축물대장 대상 (`buildingRegister` 예산이 남는 동안):
  1. `register_status = PENDING`, 최근 12개월 거래 건수 내림차순
  2. `register_status = DONE`이고 `register_synced_date`가 30일 이상 지난 단지, 오래된 순
- 명세 7장은 "매월 1일 전체 단지 재조회"였다. 단지 수천 개 × 최대 2회 호출은 하루 상한을 넘으므로 "30일 지난 것부터 남은 예산으로"로 바꾼다. 갱신 주기(약 월 1회)는 같다(본문 §13).
- 단지 하나의 결과 저장은 `HouseComplexLogic.applyGeocode`, `applyRegister`(`@Transactional`, 단지 1건)로 한다.
- 관리자가 같은 단지를 동시에 보정하면 `@Version` 충돌(`ObjectOptimisticLockingFailureException`)이 날 수 있다. 그 단지만 건너뛰고 다음 날 다시 한다.
- 건축물대장 결과가 없으면(`Optional.empty()`) 즉시 FAILED다. 호출 실패는 `registerAttempts + 1`, 3이 되면 FAILED다(좌표와 같은 규칙).

### 5.4 단계 4 — 학교 동기화 (`SCHOOL_SYNC`)

- 실행 조건: `SCHOOL_SYNC`의 마지막 SUCCESS가 없거나, 그 `run_date`가 180일 이상 지났다. 아니면 SKIPPED.
- 순서:
  1. 전국 데이터 수집. 일부 페이지라도 실패하면 FAILED로 끝내고 기존 데이터를 유지한다.
  2. 한 트랜잭션에서 `house_school` upsert(키 `school_code`)를 하고, 이번에 없는 학교는 삭제한다.
  3. 같은 트랜잭션에서 `house_complex_school`을 DELETE하고, 좌표 있는 단지마다 학교급별 최근접 학교를 계산해 INSERT한다.
- 최근접 계산은 Java에서 한다. 단지 수천 × 서울 학교 약 1,300 = 수백만 번 haversine이라 1초 안팎이다. `distance_m`은 정수 반올림이다. `GeoDistance.haversineMeters`를 `house/stat`에 둔다.
- 좌표가 새로 생긴 단지는 다음 학교 동기화(최대 180일 뒤)까지 학교 정보가 없다. 이를 막기 위해 **3단계에서 좌표가 새로 DONE이 된 단지가 있으면** 4단계는 수집 없이 "최근접 재계산만" 실행한다. 이때 기록은 SUCCESS가 아니라 `detail.recomputeOnly = true`인 SKIPPED로 남겨 180일 주기를 흐트러뜨리지 않는다.

### 5.5 단계 5 — 정비사업 동기화 (`REDEVELOPMENT_SYNC`)

- 실행 조건: 마지막 SUCCESS가 없거나 30일 이상 지났고, 게이트웨이가 Disabled가 아님. 1차는 항상 SKIPPED(본문 §4.6).
- 구현되면 학교와 같은 순서다: 수집 → `house_redevelopment` upsert(키 `source_key`) → `house_complex_redevelopment` 재생성(반경 500m, 직선거리).

### 5.6 단계 6 — 통계 (`STATS_BUILD`)

한 트랜잭션(`HouseStatLogic.rebuild(today)`)에서 아래 세 가지를 순서대로 한다. 실패하면 전부 롤백되고 이전 통계·랭킹이 그대로 남는다. PostgreSQL MVCC라 커밋 전까지 조회 API는 이전 데이터를 본다.

#### 5.6.1 날짜 경계

모든 창은 왼쪽 열림·오른쪽 닫힘 `(start, end]`이고 `deal_date`로 비교한다.

| 이름 | start(제외) | end(포함) | 2026-10-20 기준 |
| --- | --- | --- | --- |
| `w3m` | today − 3개월 | today | 2026-07-21 \~ 2026-10-20 |
| `w6m` | today − 6개월 | today | 2026-04-21 \~ 2026-10-20 |
| `w12m` | today − 12개월 | today | 2025-10-21 \~ 2026-10-20 |
| `w3m1y` | today − 15개월 | today − 12개월 | 2025-07-21 \~ 2025-10-20 |
| `w3m3y` | today − 39개월 | today − 36개월 | 2023-07-21 \~ 2023-10-20 |

`LocalDate.minusMonths`를 쓴다. 말일 보정(예: 05-31 − 3개월 = 02-28)은 Java 기본 동작을 따르고, `HouseStatWindows` 한 곳에서만 계산한다.

#### 5.6.2 이상치 재판정 (`OutlierMarker`)

- 대상: `area_bucket IN (B59, B84)`, `cancelled = false`, `removed = false`, `deal_date > today − 40개월`.
- `(complex_id, area_bucket)`로 묶어 Java에서 계산한다. PostgreSQL은 `percentile_cont`를 윈도 함수로 쓸 수 없어서, 거래별 앞뒤 창 중앙값을 SQL 한 문장으로 만들기 어렵다.
  1. 그룹 거래를 `deal_date` 순으로 정렬한다.
  2. 거래마다 `[dealDate − 6개월, dealDate + 6개월]`(양 끝 포함, 자기 자신 포함) 창을 두 포인터로 잡는다.
  3. 창 안 건수 < 5면 판정하지 않는다(outlier = false).
  4. 창 금액의 중앙값 m(짝수면 가운데 두 값 평균)을 구한다. `|amount − m| / m > 0.40`이면 outlier. 정확히 40%는 이상치가 아니다.
- 반영: `UPDATE house_trade SET outlier = false WHERE outlier = true AND deal_date > ?` 다음 `UPDATE … SET outlier = true WHERE id = ANY(?)`(1,000개씩).
- 메모리: 약 20만 행 × (id, 날짜, 금액, 그룹) → 수십 MB. 그룹 단위로 스트리밍(`complex_id` 순 정렬 조회, `Stream` + fetch size)하면 더 줄일 수 있지만, 규모상 한 번에 읽어도 된다. 병목이 확인되면 바꾼다.

#### 5.6.3 단지·평형 통계 (`AreaStatBuilder`)

`DELETE FROM slcn.house_complex_area_stat;` 다음 한 문장으로 INSERT한다(스케치. 파라미터는 §5.6.1 경계).

```sql
INSERT INTO slcn.house_complex_area_stat (complex_id, area_bucket, avg_area_12m,
  avg_3m, cnt_3m, avg_6m, cnt_6m, avg_12m, cnt_12m,
  avg_3m_1y_ago, cnt_3m_1y_ago, avg_3m_3y_ago, cnt_3m_3y_ago, volume_3m,
  recent_price_manwon, recent_window_months, recent_sample,
  change_1y_pct, change_1y_manwon, change_3y_pct, change_3y_manwon, computed_date)
SELECT g.*, … (아래 파생 규칙)
FROM (
  SELECT complex_id, area_bucket,
    ROUND(AVG(exclusive_area) FILTER (WHERE valid AND deal_date > :w12mStart), 2)  AS avg_area_12m,
    ROUND(AVG(amount_manwon)  FILTER (WHERE valid AND deal_date > :w3mStart))      AS avg_3m,
    COUNT(*)                  FILTER (WHERE valid AND deal_date > :w3mStart)       AS cnt_3m,
    … 6m, 12m, 1y-ago(:w3m1yStart < d <= :w3m1yEnd), 3y-ago 같은 패턴 …,
    COUNT(*) FILTER (WHERE NOT cancelled AND NOT removed AND deal_date > :w3mStart) AS volume_3m
  FROM (SELECT t.*, (NOT cancelled AND NOT removed AND NOT outlier) AS valid
        FROM slcn.house_trade t
        WHERE area_bucket IN ('B59','B84') AND deal_date > :w3m3yStart AND deal_date <= :today) t
  GROUP BY complex_id, area_bucket
) g;
```

파생 규칙:

| 컬럼 | 규칙 |
| --- | --- |
| `avg_*` | 만원 정수, 소수 첫째 자리에서 반올림(`ROUND(AVG(...))`, numeric half-up) |
| `recent_*` | `cnt_3m > 0` → (`avg_3m`, 3, `cnt_3m`), 아니면 `cnt_6m > 0` → (`avg_6m`, 6, `cnt_6m`), 아니면 (null, null, 0)[명세 §4] |
| `change_1y_*` | `cnt_3m ≥ 2 AND cnt_3m_1y_ago ≥ 2`일 때만. `manwon = avg_3m − avg_3m_1y_ago`, `pct = ROUND((avg_3m − avg_3m_1y_ago) * 100.0 / avg_3m_1y_ago, 2)`. **반올림된 평균으로 계산한다.** 그래야 화면의 "4.3억 → 4.5억 (+4.65%)"가 표시된 두 값으로 다시 계산했을 때와 맞는다 |
| `change_3y_*` | 같은 방식, `avg_3m_3y_ago` 기준 |
| `volume_3m` | 해제·원천 삭제만 빼고 이상치는 포함[명세 §6] |

- `sample_status`는 저장하지 않는다. 조회 시 `SampleStatus.of(cntFrom, cntTo)`로 만든다.
- 행은 수집 범위 안에 B59/B84 거래가 한 건이라도 있는 (단지, 구간)에만 생긴다.
- 명세 9장 표에 없던 `avg_6m`, `cnt_6m`을 추가한다. 최근 실거래가가 6개월 창으로 대체될 때 근거값을 남기기 위해서다(본문 §13).

#### 5.6.4 랭킹 (`RankingBuilder`)

`house_complex_area_stat` + `house_complex`를 읽어 Java에서 순위를 만들고, `DELETE FROM house_ranking` 후 `batchUpdate`로 INSERT한다. Java에서 하는 이유는 단계적 완화와 동률 규칙이 SQL로는 읽기 어렵기 때문이다. 대상은 단지 수천 개라 메모리 부담이 없다.

| 타입 | 대상 | 값 | 정렬(앞이 우선) | 개수 |
| --- | --- | --- | --- | --- |
| LEADER | 구마다 1행 | `avg_12m` | 구 안에서 `avg_12m` 내림 → 1년 변동률 내림(null 뒤) → \|1년 등락가격\| 내림 → 단지명 | 25 |
| RISE | `cnt_3m ≥ 3`, `cnt_3m_1y_ago ≥ 3`, `change_1y_pct > 0` | `change_1y_pct` | 변동률 내림 → \|등락가격\| 내림 → 단지명 | ≤ 20 |
| FALL | 같은 표본 조건, `change_1y_pct < 0` | `change_1y_pct` | 변동률 오름(하락 큰 순) → \|등락가격\| 내림 → 단지명 | ≤ 20 |
| VOLUME | `volume_3m > 0` | `volume_3m` | 건수 내림 → 1년 변동률 내림(null 뒤) → \|등락가격\| 내림(null 뒤) → 단지명 | ≤ 20 |

- **LEADER 단계적 완화**[명세 §6]: 구마다
  1. `cnt_12m ≥ 3`인 단지 중 1위(배지 없음)
  2. 없으면 `cnt_12m ≥ 1` 중 1위 + `LOW_SAMPLE`
  3. 그래도 없으면 `complex_id = null`, `value_num = null`, `NO_TRADE`

  `rank_no`는 25행을 `value_num` 내림차순으로 매긴다. NO_TRADE 행은 맨 뒤이고, 그 안에서는 `lawd_cd` 순이다.
- **단지명 비교**는 `String.compareTo`(코드 포인트 순)로 한다. 한글 음절은 유니코드에서 가나다 순으로 놓여 있어 그대로 맞다. DB 정렬은 콜레이션에 따라 달라질 수 있어 쓰지 않는다. 동명 단지는 마지막으로 `complex_id`로 가른다(순위 결정성).
- `period_from`/`period_to`는 명세 10장 랭킹의 기간 정의(창의 첫날 \~ today)를 저장한다.
- `sample_count`: LEADER = `cnt_12m`, RISE/FALL = `min(cnt_3m, cnt_3m_1y_ago)`, VOLUME = `volume_3m`.
- `change_manwon`: RISE/FALL/LEADER/VOLUME 모두 `change_1y_manwon`(없으면 null).

---

## 6. 조회 API 상세

공통:
- Resource는 `@RequestParam`을 받아 Flow에 넘기기만 한다.
- 입력 검증은 Flow 첫머리에서 하고, `BusinessException(VALIDATION_FAILED, 메시지)`를 던진다(`GeoQueryFlow`와 같은 방식). 필드 단위 위반 목록이 필요한 곳(비교 `items`)은 `HouseValidationException`이 `ErrorResponse.errors`를 채운다.
- 조회 Flow는 `@Transactional(readOnly = true)`.
- `area` 파라미터는 `B59`/`B84`만 허용한다(`OTHER`는 400). enum 변환 실패는 기존 핸들러가 `INVALID_PARAMETER`로 바꾼다.

### 6.1 `GET /house/budget-policy` → `BudgetPolicyRdo`

- `HouseBudgetPolicyStore.findEffective(today)` = `findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDesc`. 없으면 `HouseBudgetPolicyNotFoundException`(503 `HOUSE_BUDGET_POLICY_NOT_FOUND`).
- `ltv`는 `{"FIRST_TIME": ltvFirstTime, "NON_FIRST": ltvNonFirst}` Map으로 낸다.
- `priceCaps`, `costRates` 정렬 규칙[명세 §10]은 저장 시점에 보장한다. 정책 행은 수동 SQL로만 들어가므로 Store가 읽을 때 정렬·마지막 null 규칙을 검사하고, 깨졌으면 `log.error` 후 503으로 낸다. 잘못된 규칙으로 계산하게 두지 않는다.

### 6.2 `GET /house/meta` → `HouseMetaRdo`

```sql
SELECT CASE WHEN COUNT(*) < 75 OR COUNT(*) FILTER (WHERE last_success_date IS NULL) > 0
            THEN NULL ELSE MIN(last_success_date) END
FROM slcn.house_collect_unit WHERE deal_ym IN (:ym0, :ym1, :ym2);
```

- `ym0`\~`ym2` = 이번 달, −1, −2. 결과가 `dataAsOf`다[명세 §10 메타].
- `statsComputedDate` = `BatchStep.STATS_BUILD`, `SUCCESS`의 최대 `run_date`. 없으면 null.

### 6.3 `GET /house/complexes` → `ComplexMapListRdo`

검증:
- 연식(`buildYear`)은 `approval_date`의 연도, 없으면 `build_year`다(SQL `COALESCE`, 규칙은 `HouseComplex.resolveBuildYear()`와 같다).
- 영역 4개(`swLat`, `swLng`, `neLat`, `neLng`)와 `lawdCd` 중 정확히 하나만 와야 한다. 영역은 4개 모두 있어야 한다.
- 영역: `swLat < neLat`, `swLng < neLng`, 각 변 ≤ 0.2도.
- `lawdCd`: `HouseConstant.DISTRICTS`의 25개 코드 중 하나.
- `minPrice`·`maxPrice`: 0 이상, `minPrice ≤ maxPrice`. 하나만 와도 된다(없는 쪽은 열림).

쿼리 (네이티브, 두 번: 목록 + 건수):

```sql
SELECT c.id, c.apt_name, c.lawd_cd, c.umd_name, c.latitude, c.longitude, c.build_year, c.households,
       s.recent_price_manwon, s.recent_window_months, s.recent_sample
FROM slcn.house_complex c
LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area
WHERE c.geocode_status = 'DONE'
  AND (:bounds = false OR (c.latitude BETWEEN :swLat AND :neLat AND c.longitude BETWEEN :swLng AND :neLng))
  AND (:lawdCd IS NULL OR c.lawd_cd = :lawdCd)
  AND (:hasPrice = false OR (s.recent_price_manwon IS NOT NULL
       AND s.recent_price_manwon >= :minPrice AND s.recent_price_manwon <= :maxPrice))
ORDER BY s.recent_price_manwon DESC NULLS LAST, c.apt_name, c.id
LIMIT 300;
```

- `total` = 같은 조건의 `COUNT(*)`. `truncated = total > 300`.
- 가격 조건이 없으면 가격 없는 단지도 포함된다(`recentPrice: null`)[명세 §10].
- `districtName`은 `HouseConstant.districtName(lawdCd)`.
- 인덱스는 `(latitude, longitude)` B-tree다. 위도 범위로 먼저 줄어들고, 서울 단지 수천 개라 충분하다.

### 6.4 `GET /house/complexes/summary` → `ComplexSummaryRdo`

검증: DISTRICT는 영역 파라미터가 하나라도 오면 400. DONG은 영역 4개가 필수이고, 각 변 ≤ 1.0도.

**DISTRICT**: 25행 고정[명세 §10].

```sql
SELECT c.lawd_cd,
       COUNT(*) FILTER (WHERE :hasPrice = false OR s.recent_price_manwon BETWEEN :minPrice AND :maxPrice) AS cnt
FROM slcn.house_complex c
LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area
WHERE c.geocode_status = 'DONE'
GROUP BY c.lawd_cd;
```

Java에서 `HouseConstant.DISTRICTS` 25개를 돌며 위 결과를 붙인다. 없는 구는 0이다. 중심은 `HouseConstant`의 구별 고정 좌표다(본문 §8.3).

**DONG**:

```sql
SELECT c.lawd_cd, c.umd_cd, MAX(c.umd_name) AS umd_name,
       AVG(c.latitude) AS center_lat, AVG(c.longitude) AS center_lng,
       COUNT(*) FILTER (WHERE :hasPrice = false OR s.recent_price_manwon BETWEEN :minPrice AND :maxPrice) AS cnt
FROM slcn.house_complex c
LEFT JOIN slcn.house_complex_area_stat s ON s.complex_id = c.id AND s.area_bucket = :area
WHERE c.geocode_status = 'DONE'
GROUP BY c.lawd_cd, c.umd_cd
HAVING AVG(c.latitude) BETWEEN :swLat AND :neLat AND AVG(c.longitude) BETWEEN :swLng AND :neLng;
```

- 중심은 필터와 무관하게 좌표 있는 전체 단지 평균이다(`COUNT`만 FILTER).
- `code = lawd_cd || umd_cd`(10자리).
- 서울 전체를 GROUP BY해도 단지 수천 개라 가볍다. 영역으로 미리 거르지 않는 이유: 중심이 영역 안인 동의 일부 단지가 영역 밖에 있을 수 있다. 미리 거르면 중심과 개수가 영역에 따라 달라진다.
- 정렬은 명세에 없다. 후보 리스트는 FE가 후보 수 내림차순으로 정렬한다[명세 §4]. BE는 `cnt DESC, umd_name`으로 낸다.

### 6.5 `GET /house/complexes/search` → `ComplexSearchRdo`

- 검증: `keyword`는 trim 후 2자 이상, `size`는 1\~20(기본 10).
- `WHERE REPLACE(c.apt_name, ' ', '') LIKE '%' || :kw || '%' ESCAPE '\' ORDER BY c.apt_name, c.id LIMIT :size`
  - `kw`는 공백을 빼고 `\`, `%`, `_`를 이스케이프한 값이다. 이스케이프하지 않으면 "%"만 넣어도 전체가 나온다.
  - 대소문자 무시는 `LOWER`를 양쪽에 씌운다. 영문 단지명("SK VIEW") 때문이다.
- 좌표가 없는 단지도 검색에는 나온다. 비교표는 좌표 없이도 의미가 있기 때문이다.

### 6.6 `GET /house/complexes/{complexId}` → `ComplexCardRdo`

- 단지가 없으면 404 `HOUSE_COMPLEX_NOT_FOUND`.
- `buildYear`는 `resolveBuildYear()`(사용승인연도, 없으면 실거래 건축년도)다.
- 좌표가 없어도 200이다(`latitude`/`longitude` null). 정보 카드는 지도 마커에서 열리므로 실제로는 좌표가 있다.
- `recentPrice`는 (단지, `area`) 통계 행에서 가져온다. 행이 없거나 `recent_price_manwon`이 null이면 `recentPrice: null`이다.

### 6.7 `GET /house/compare` → `CompareRdo`

`items` 파싱·검증 (`CompareItemsParser`, 순수 클래스):
- `,`로 나누고, 각 항목을 마지막 `:`로 `complexId`와 구간으로 나눈다.
- 1\~3개, 구간은 `B59`/`B84`, `complexId` 비어 있지 않음, 같은 (단지, 구간) 중복 금지.
- 위반은 `VALIDATION_FAILED` + `errors[]`(`field = "items[1]"`, `code` = `FORMAT`\|`TOO_MANY`\|`DUPLICATED`\|`INVALID_AREA`).

조립 (`HouseCompareQueryFlow`):
1. 단지 일괄 조회 → 없는 ID는 `missingIds`(요청 순서, 중복 없이)
2. (단지, 구간) 통계 일괄 조회
3. 학교: `house_complex_school` + `house_school` 일괄 조회, 학교급 E·M·H 순
4. 정비사업: `REDEVELOPMENT_SYNC` SUCCESS 기록이 없으면 모든 열에 `null`, 있으면 `house_complex_redevelopment` 조회 결과(없으면 `[]`)
5. 열 순서는 요청 순서이고, 없는 단지는 건너뛴다

열 필드 규칙:

| 필드 | 규칙 |
| --- | --- |
| `buildYear` | `approval_date`의 연도, 없으면 `build_year` |
| `ageYears` | `today.getYear() − buildYear`. `buildYear`가 없으면 null |
| `areaInfo.avgExclusiveArea` | `avg_area_12m`. null이면 `areaInfo`의 해당 값 null |
| `far` | `far_pct`가 null이면 `far: null` |
| `change1y`/`change3y` | 통계 행 값 + `SampleStatus.of(from건수, to건수)`. INSUFFICIENT면 `pct`, `changeManwon` null이고, `fromManwon`/`toManwon`은 건수가 0인 쪽만 null |

- 통계 행이 아예 없는 (단지, 구간)도 열은 낸다. 가격 관련 필드만 null이다.

### 6.8 `GET /house/rankings` → `RankingListRdo`

- `house_ranking`(type, area) `ORDER BY rank_no` + `house_complex` LEFT JOIN(좌표·연식·세대수) + 통계 LEFT JOIN(`avg_area_12m`).
- 연식은 `approval_date`의 연도, 없으면 `build_year`다(SQL `COALESCE`, 지도 목록과 같은 규칙).
- 좌표 보강에 실패한 단지는 `latitude`/`longitude`가 null이다[명세 §10].
- 랭킹 행이 0개면(통계가 한 번도 안 돌았음) `items: []`, `period: null`.

### 6.9 관리자 API

**`GET /house/admin/complexes/incomplete`** → `IncompleteComplexListRdo`
- 조건: `geocode_status = 'FAILED' OR register_status = 'FAILED'`
- 정렬: 최근 12개월 거래 건수 내림차순, 그다음 `id`
- 페이지: `PageRequestSupport`(기존 공통 유틸)로 `page`(1부터), `size`(1\~100, 기본 20)를 검증한다
- `trades12m`: `house_trade`에서 단지별 `COUNT(*) FILTER (NOT cancelled AND NOT removed AND deal_date > today − 12개월)`. 서브쿼리로 붙인다

**`PUT /house/admin/complexes/{complexId}/address-override`** ← `AddressOverrideUdo { roadAddress?, bonbun?, bubun? }`
- 검증:
  - 셋 중 하나 이상 필요
  - `roadAddress`는 trim 후 1\~200자
  - `bonbun`/`bubun`은 숫자 1\~4자리(저장 시 0 채움). `bubun`만 오는 것은 거부한다
- 반영 (`HouseComplexLogic.overrideAddress`, `@Transactional`):
  - `addressOverride`를 통째로 교체한다. 보낸 필드만 남고, 보내지 않은 필드는 보정 해제다
  - `roadAddress`가 있으면 → `geocodeStatus = PENDING`, `geocodeAttempts = 0`
  - `bonbun`이 있으면 → `registerStatus = PENDING`, `registerAttempts = 0`. 지번은 좌표 검색어 3순위에도 쓰이므로 좌표도 PENDING으로 돌린다
- 응답: 200 + 갱신된 `IncompleteComplexRdo` 한 행. 실제 재조회는 다음 야간 배치다[명세 §8].

---

## 7. 모듈 배치

`docs/module.md` 기준. 도메인 패키지명은 `house`다.

```text
slcn-spec/src/main/java/com/seoulchonnom/spec/house/
├── constant/
│   └── HouseConstant.java          # 구 25개(코드·이름·고정 중심 좌표), 면적 구간 기본 경계, 에러 메시지 상수
├── entity/
│   ├── HouseComplex.java
│   ├── HouseTrade.java
│   ├── HouseCollectUnit.java
│   ├── HouseBudgetPolicy.java
│   ├── HouseBatchRun.java
│   ├── HouseSchool.java
│   ├── HouseRedevelopment.java
│   └── vo/                         # 본문 §3.5의 enum·VO
├── facade/
│   ├── HouseBudgetPolicyFacade.java
│   ├── HouseMetaFacade.java
│   ├── HouseComplexFacade.java     # 목록, 묶음, 검색, 카드
│   ├── HouseCompareFacade.java
│   ├── HouseRankingFacade.java
│   ├── HouseAdminFacade.java
│   └── sdo/
│       ├── BudgetPolicyRdo.java          (+ PriceCapRdo, CostRatesRdo …)
│       ├── HouseMetaRdo.java
│       ├── ComplexMapListRdo.java        (+ ComplexMapItemRdo, RecentPriceRdo)
│       ├── ComplexSummaryRdo.java        (+ ComplexSummaryItemRdo)
│       ├── ComplexSearchRdo.java         (+ ComplexSearchItemRdo)
│       ├── ComplexCardRdo.java
│       ├── CompareRdo.java               (+ CompareColumnRdo, PriceChangeRdo, SchoolRdo, RedevelopmentRdo, AreaInfoRdo, FarRdo)
│       ├── RankingListRdo.java           (+ RankingItemRdo, PeriodRdo)
│       ├── IncompleteComplexListRdo.java (+ IncompleteComplexRdo)
│       └── AddressOverrideUdo.java
└── mapper/
    └── HouseBudgetPolicyMapper.java     # 정책 엔티티 → Rdo (MapStruct)

slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/house/
├── exception/
│   ├── HouseComplexNotFoundException.java          # 404
│   ├── HouseBudgetPolicyNotFoundException.java     # 503
│   ├── HouseDataSourceUnavailableException.java    # 배치 내부용(응답으로 나가지 않음)
│   └── HouseValidationException.java               # VALIDATION_FAILED + errors[]
├── collect/
│   ├── TradeGateway.java, TradeRecord.java, TradeFetchResult.java
│   ├── BuildingRegisterGateway.java, RegisterSummary.java
│   ├── SchoolGateway.java, SchoolRecord.java
│   ├── RedevelopmentGateway.java, RedevelopmentRecord.java
│   ├── Disabled*Gateway.java (4종)
│   ├── CallBudget.java, HouseSleeper.java
│   ├── DedupKeyFactory.java, ComplexNameNormalizer.java
│   └── ComplexGeocoder.java                        # AddressGateway + UtmkToWgs84Converter
├── stat/
│   ├── HouseStatWindows.java, OutlierMarker.java, AreaStatBuilder.java, RankingBuilder.java
│   ├── NearestSchoolCalculator.java, GeoDistance.java
│   └── CompareItemsParser.java
├── logic/
│   ├── HouseComplexLogic.java          # 생성·표시 갱신·보강 결과 반영·관리자 보정
│   ├── HouseTradeLogic.java            # applyUnit
│   ├── HouseCollectUnitLogic.java
│   ├── HouseReferenceLogic.java        # 학교·정비사업 교체, 최근접 재계산
│   ├── HouseStatLogic.java             # rebuild(today) 한 트랜잭션
│   ├── HouseBatchRunLogic.java         # BatchStepRecorder 저장
│   └── HouseNightlyScheduler.java
└── store/
    ├── HouseComplexStore.java, HouseTradeStore.java, HouseCollectUnitStore.java,
    │   HouseBudgetPolicyStore.java, HouseBatchRunStore.java, HouseReferenceStore.java,
    │   HouseStatStore.java (통계·랭킹·묶음 조회)
    ├── jpo/            # 원본 7종 + 파생 4종, jpo/converter/ (PriceCapListConverter, CostRatesConverter, AddressOverrideConverter, JsonMapConverter)
    ├── mapper/         # *JpoMapper (원본만)
    ├── projection/     # ComplexMapPdo, ComplexSummaryPdo, AreaStatPdo, RankingRowPdo, IncompleteComplexPdo
    └── repository/

slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/flow/house/
├── HouseQueryFlow.java            # budget-policy, meta, complexes, summary, search, card, rankings
├── HouseCompareQueryFlow.java     # 비교표 조립 (여러 저장소를 가로지른다)
├── HouseNightlyFlow.java          # 6단계 오케스트레이션, 트랜잭션 없음
├── BatchStepRecorder.java
└── HouseAdminFlow.java

slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/external/datagokr/
├── MolitTradeGateway.java
├── BuildingRegisterGatewayImpl.java
├── SchoolStandardDataGateway.java
├── DataGoKrResponseReader.java    # JSON 봉투·값 읽기, resultCode 분류
└── DataGoKrResultCode.java

slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/
├── DataGoKrConfiguration.java     # RestClient, 키 없으면 Disabled
└── HouseConfiguration.java        # houseClock, HouseSleeper 기본 구현, 면적 구간 경계 설정 바인딩

slcn-rest/src/main/java/com/seoulchonnom/rest/house/
├── HouseBudgetPolicyResource.java   GET /house/budget-policy
├── HouseMetaResource.java           GET /house/meta
├── HouseComplexResource.java        GET /house/complexes, /summary, /search, /{complexId}
├── HouseCompareResource.java        GET /house/compare
├── HouseRankingResource.java        GET /house/rankings
└── HouseAdminResource.java          GET/PUT /house/admin/complexes/…
```

- 명세 11장 트리와 다른 점: 비교 Facade/Resource를 분리했고(`HouseCompareFacade`), 수집 포트를 `collect/`, 통계 계산을 `stat/`에 나눴다. 오케스트레이션은 기존 규칙대로 `flow/house/`에 있다.
- `/house/complexes/summary`와 `/house/complexes/{complexId}`가 경로상 겹친다. Spring MVC는 리터럴 경로(`/summary`, `/search`)를 변수 경로보다 우선하므로 문제없다. 그래도 `HouseComplexResourceTest`에 `GET /house/complexes/summary`가 카드로 가지 않는지 고정한다. 단지 ID가 `HOUSE_COMPLEX-…` 형식이라 "summary"라는 ID는 생기지 않는다.

---

## 8. 설정 · 보안 · 공통 코드 변경

### 8.1 `application.yml` (`slcn.house`)

```yaml
slcn:
  house:
    # 야간 수집·통계. "-"면 끈다. 운영: "0 0 3 * * *" (Asia/Seoul)
    nightly-cron: "${SLCN_HOUSE_NIGHTLY_CRON:-}"
    data-go-kr:
      base-url: "${SLCN_DATA_ORG_BASE_URL:https://apis.data.go.kr}"
      # 학교 표준데이터 API만 호스트가 다르다.
      school-base-url: "${SLCN_DATA_ORG_SCHOOL_BASE_URL:https://api.data.go.kr}"
      # 공공데이터포털 "Decoding" 인증키. 계정 키 하나를 세 API가 같이 쓴다.
      # Encoding 키를 넣으면 이중 인코딩으로 키 오류가 난다. 비면 수집 단계를 모두 건너뛴다.
      service-key: "${SLCN_DATA_ORG_API_KEY:}"
      timeout-seconds: "${SLCN_DATA_ORG_TIMEOUT_SECONDS:10}"
    call-limit:
      # 하루 호출 상한. 활용신청한 API별 일일 한도보다 작게 둔다.
      trade: "${SLCN_HOUSE_CALL_LIMIT_TRADE:1000}"
      # 개발계정 한도 10,000의 절반. 수동 확인 호출 여유를 남긴다.
      building-register: "${SLCN_HOUSE_CALL_LIMIT_BUILDING_REGISTER:5000}"
      school: "${SLCN_HOUSE_CALL_LIMIT_SCHOOL:100}"
      juso: "${SLCN_HOUSE_CALL_LIMIT_JUSO:4000}"
    # 행안부 호출 사이 대기. 연달아 부르면 순간 호출 제한에 걸린다(본문 §4.4).
    juso-interval-millis: "${SLCN_HOUSE_JUSO_INTERVAL_MILLIS:1000}"
```

- 행안부 키는 기존 `slcn.geo.juso.*`를 그대로 쓴다.
- AGENTS.md "Security & Configuration Tips"의 환경변수 목록에 `SLCN_DATA_ORG_*`, `SLCN_HOUSE_*`를 추가한다. 로컬은 저장소 루트 `.env`(`.gitignore` 대상)에 `SLCN_DATA_ORG_API_KEY`를 둔다.

### 8.2 `SecurityConfiguration`

`anyRequest()` 앞, 기존 ADMIN 매처들과 같은 위치에 한 줄 추가한다.

```java
// 내집찾기 관리자 보강 API. 조회 API는 아래 anyRequest(USER)로 로그인 회원만 허용한다(비회원 공개 없음).
.requestMatchers("/house/admin/**").hasAuthority(ADMIN_AUTHORITY)
```

`permitAll`은 추가하지 않는다. 테스트로 고정한다: 비로그인 → 401, USER → 관리자 API 403, ADMIN → 200.

### 8.3 `HouseConstant`

- `DISTRICTS`: 25개 `(lawdCd, name, centerLat, centerLng)`. 중심 좌표는 각 구청 좌표로 한다. 구 경계 중심점보다 사람이 아는 위치이고, 행정구역 경계 데이터가 필요 없다. 값은 구현 시 행안부 좌표 API로 구청 주소를 한 번 조회해 상수로 적는다.
- 면적 구간 기본 경계(55.00, 65.00, 80.00, 90.00)
- 에러 메시지 상수(`HOUSE_COMPLEX_NOT_FOUND_ERROR_MESSAGE` 등. `InspectionConstant`와 같은 방식)

### 8.4 `ErrorCode` 추가

```java
HOUSE_COMPLEX_NOT_FOUND(HttpStatus.NOT_FOUND, HOUSE_COMPLEX_NOT_FOUND_ERROR_MESSAGE),          // "단지 정보를 찾을 수 없습니다."
HOUSE_BUDGET_POLICY_NOT_FOUND(HttpStatus.SERVICE_UNAVAILABLE, HOUSE_BUDGET_POLICY_NOT_FOUND_ERROR_MESSAGE), // "예산 계산 규칙을 불러올 수 없습니다."
```

`CommonExceptionHandlerTest`에 두 코드의 상태·`code` 렌더링을 추가한다.

### 8.5 공통 코드 변경 체크리스트

| 파일 | 변경 |
| --- | --- |
| `ErrorCode`, 상수 | 코드 2개 |
| `SecurityConfiguration` | 관리자 매처 1줄 |
| `application.yml` | `slcn.house` 블록 |
| `AGENTS.md` | 환경변수 목록 |
| `SchedulingConfiguration` | 변경 없음(이미 `@EnableScheduling`) |
| `inspection/geo/*` | 변경 없음(주입만 받는다) |

---

## 9. 영속성

### 9.1 공통

- 스키마 `slcn`. 테이블명은 명세 9장 그대로다.
- JSON 컬럼은 jsonb가 아니라 **TEXT + `AttributeConverter`**(`JsonUtil`)다. 기존 도메인(`TravelJpo.days`, `ViewedPropertyJpo.answers`)과 같다. `@Column(columnDefinition = "TEXT")`를 명시한다. 빠뜨리면 `varchar(255)`가 된다.
- 명세 9장 시드 SQL의 JSON 값은 문자열로 그대로 넣으면 된다(TEXT 컬럼).
- enum은 `@Enumerated(EnumType.STRING)`.
- 날짜는 `LocalDate`(`date`). 생성·수정 시각은 기존 규칙대로 `registeredTime`/`modifiedTime`(epoch ms, `bigint`).

### 9.2 원본 테이블 컬럼

| 테이블 | 컬럼 (JPA 타입) | 제약·인덱스 |
| --- | --- | --- |
| `house_complex` | 본문 §3.2 + `DomainEntityJpo` | `uk_house_complex_apt_seq`(apt_seq), `idx_house_complex_geo`(latitude, longitude), `idx_house_complex_district`(lawd_cd, umd_cd), `idx_house_complex_name`(apt_name) |
| `house_trade` | 본문 §3.3 + `DomainEntityJpo` | `uk_house_trade_dedup_key`(dedup_key), `idx_house_trade_stats`(complex_id, area_bucket, deal_date), `idx_house_trade_unit`(lawd_cd, deal_ym) |
| `house_collect_unit` | 본문 §3.4 | `uk_house_collect_unit`(lawd_cd, deal_ym) |
| `house_budget_policy` | 명세 9장 | `uk_house_budget_policy_effective_from`(effective_from) |
| `house_batch_run` | 본문 §3.4 | `idx_house_batch_run_step`(step, status, run_date) |
| `house_school` | `schoolCode`, `name`, `level`, `latitude`, `longitude`, `syncedDate` | `uk_house_school_code`(school_code) |
| `house_redevelopment` | 명세 9장 | `uk_house_redevelopment_source_key`(source_key) |

### 9.3 파생 테이블

| 테이블 | 비고 |
| --- | --- |
| `house_complex_area_stat` | 명세 9장 컬럼 + `avg_6m`(bigint), `cnt_6m`(int). 통계값은 nullable |
| `house_ranking` | `complex_id`, `value_num` nullable(NO_TRADE). `value_num`은 `numeric(12,2)` |
| `house_complex_school`, `house_complex_redevelopment` | 명세 9장 그대로 |

### 9.4 네이티브 쿼리 목록

JPQL로는 `ON CONFLICT`, `FILTER`, `= ANY(?)`를 쓸 수 없다. 네이티브 쿼리는 아래 목록으로 한정하고, 모두 Repository의 `@Query(nativeQuery = true)` 또는 `@Modifying`으로 둔다(`InspectionAreaRepository`와 같은 방식).

| 쿼리 | 위치 | 본문 |
| --- | --- | --- |
| 수집 단위 insert-if-absent | `HouseCollectUnitRepository` | §5.2.1 |
| 거래 upsert, removed 표시 | `HouseTradeRepository` | §5.2.3 |
| 이상치 초기화·표시 | `HouseTradeRepository` | §5.6.2 |
| 통계 DELETE + INSERT…SELECT | `HouseComplexAreaStatRepository` | §5.6.3 |
| 랭킹 DELETE | `HouseRankingRepository` | §5.6.4 |
| 영역 단지 목록·건수 | `HouseComplexRepository` | §6.3 |
| 구·동 묶음 | `HouseComplexRepository` | §6.4 |
| 메타 `dataAsOf` | `HouseCollectUnitRepository` | §6.2 |
| 보강 실패 목록 + `trades12m` | `HouseComplexRepository` | §6.9 |

### 9.5 정책 시드 (명세 9장 SQL의 확정본)

```sql
INSERT INTO slcn.house_budget_policy
  (id, entity_version, registered_time, modified_time, effective_from,
   dsr_ratio, stress_rate_pp, term_years, default_reserve_manwon, ltv_first_time, ltv_non_first,
   price_caps, cost_rates, source_note)
SELECT gen_random_uuid()::text, 0,
       (extract(epoch from now()) * 1000)::bigint, (extract(epoch from now()) * 1000)::bigint,
       DATE '2025-10-16', 0.400, 3.00, 30, 2000, 0.700, 0.400,
       '<명세 9장 price_caps JSON>', '<명세 9장 cost_rates JSON>',
       '10·15 대책(2025-10-16 시행), 지방세법, 서울시 중개보수 조례'
WHERE NOT EXISTS (SELECT 1 FROM slcn.house_budget_policy WHERE effective_from = DATE '2025-10-16');
```

`WHERE NOT EXISTS`로 두 번 실행해도 안전하다. 정책 행은 UUID라 `id_sequence` 행이 필요 없다.

### 9.6 `ddl-auto=update` 주의

- 신규 테이블 11개가 자동 생성된다. 기존 테이블 변경은 없다.
- **유니크 제약 7건**(본문 §9.2의 `uk_*`)이 실제로 생성됐는지 배포 후 `\d+`로 확인한다. 없으면 수동 `CREATE UNIQUE INDEX`를 건다. `uk_house_trade_dedup_key`가 없으면 `ON CONFLICT (dedup_key)`가 "no unique or exclusion constraint" 오류로 실패하고, 수집 단계 전체가 FAILED가 된다.
- 파생 테이블의 복합 PK도 확인한다.
- `id_sequence`의 `HOUSE_COMPLEX` 행은 첫 채번 때 자동으로 만들어진다(`IdGeneratorLogic.createSequence`). 시드가 필요 없다.

---

## 10. 동시성과 트랜잭션 경계

| 지점 | 경계 | 근거 |
| --- | --- | --- |
| 외부 호출 | 트랜잭션 밖(`HouseNightlyFlow`) | 호출 수 초 동안 커넥션·락을 잡지 않는다 |
| 수집 단위 반영 | 단위 1개 = 트랜잭션 1개 | 실패해도 그 단위만 롤백되고, 다른 단위의 진행은 남는다 |
| 단지 보강 결과 | 단지 1개 = 트랜잭션 1개 | 관리자 보정과의 충돌은 `@Version`으로 감지하고 건너뛴다 |
| 참조 데이터 교체 | 단계 1개 = 트랜잭션 1개 | 학교 목록과 최근접 결과가 어긋나지 않는다 |
| 통계·랭킹 | 한 트랜잭션 | 조회 API가 절반만 바뀐 통계를 보지 않는다 |
| 야간 배치 중복 실행 | 스케줄러 스레드 1개 | 인스턴스 1개이고 수동 실행 API가 없어 겹칠 경로가 없다. 운영자가 재기동해 같은 날 두 번 돌아도 upsert·교체 방식이라 결과가 같다 |

- 기본 스케줄러 스레드는 1개다. 야간 배치가 04:30까지 끝나지 않으면 `RawUploadCleanupScheduler`가 그만큼 늦게 시작한다. 명세 7장에서 허용했다.
- 첫 배포 후 며칠은 예산 때문에 배치가 오래 걸리지 않는다. 상한에 닿으면 멈추기 때문이다. 하루 작업 시간은 대략 호출 수 × 응답 시간(0.3\~1초)이다. 실거래 1,000 + 대장 5,000 + 행안부 4,000이면 1\~2시간대다. 대장 상한은 행안부 상한(단지 약 2,000개)에 맞춰 실제로는 4,000회 안팎만 쓴다.

---

## 11. 데이터가 채워지는 순서 (첫 배포 후)

| 일차 | 실거래 | 단지 좌표 | 건축물대장 | 화면 상태 |
| --- | --- | --- | --- | --- |
| 1 | 최근 3개월 + 백필 일부(상한 1,000) | 상위 약 2,000개(행안부 4,000회) | 상위 약 2,000개(호출 최대 5,000) | 기준일 표시, 묶음·지도 일부, 비교표에 세대수·용적률 빈칸 |
| 2 | 백필 완료 | 대부분 완료 | 계속 | 랭킹 RISE/FALL/LEADER 정상(1년 전·3년 전 창 확보) |
| 3\~7 | 매일 최근 3개월 | 신규만 | 완료 | 정상 |

- 정확한 일수는 단지 수와 실제 한도에 달려 있다. 0단계에서 확인한 한도로 상한을 올리면 줄어든다.
- 3년 변동은 3년 전 창 단위(2023-07\~10)가 백필되어야 나온다. 백필은 최근부터 과거 순이므로 가장 마지막에 채워진다.

---

## 12. 테스트 계획

### 12.1 spec

- `AreaBucket.of`: 54.99/55.00/64.99/65.00/79.99/80.00/89.99/90.00 경계
- `SampleStatus.of`: (0,5) INSUFFICIENT, (1,5) INSUFFICIENT, (2,5) LOW, (3,2) LOW, (3,3) OK
- JSON VO(`PriceCap`, `CostRates`, `AddressOverride`) equals/hashCode: 같은 값이면 같음

### 12.2 aggregate (순수 클래스)

| 대상 | 케이스 |
| --- | --- |
| `DedupKeyFactory` | 같은 필드 2건 → 순번 1·2로 키가 다름, 해제 여부가 바뀌어도 키 같음, null 필드 처리 |
| `ComplexNameNormalizer` | 공백·괄호 내용·"아파트" 제거, 영문 소문자 |
| `HouseStatWindows` | 2026-10-20, 말일(05-31, 03-31) 경계 |
| `OutlierMarker` | 창 4건 미판정 / 5건 판정, 정확히 ±40%는 정상, 짝수 중앙값, 앞뒤 6개월 창(상승기 정상 거래가 이상치로 찍히지 않음) |
| `RankingBuilder` | LEADER 단계적 완화 3단계, NO_TRADE 행 맨 뒤, RISE 20개 미만, FALL 정렬 방향, 동률 3단계, 단지명 코드 포인트 순 |
| `NearestSchoolCalculator`, `GeoDistance` | 알려진 두 지점 거리(허용 오차 1m), 학교급별 최근접 |
| `CompareItemsParser` | 정상, 4개, 중복, `B60`, 콜론 없음, 빈 ID |
| `CallBudget` | 소진 후 호출 안 함, 당일 사용량 차감 |
| `MolitTradeGateway` | 쉼표 금액, 해제 필드(`"26.08.28"`), 직거래, 숫자·문자열이 섞인 `bonbun`/`jibun`, 공백 한 칸 빈 값, `item`이 객체 1개, 다쪽 응답, `totalCount = 0`, 한도 초과 코드 → QUOTA_EXCEEDED, 키 오류 → 재시도 없음, 로그에 키가 없음(`OutputCaptureExtension`) |
| `BuildingRegisterGatewayImpl` | 총괄표제부 우선, 표제부 합산·최대 연면적 동, 둘 다 없음, 용적률 0 → null, 총괄표제부 타임아웃 → 표제부, 연속 3번 실패 후 건너뜀 |
| `DataGoKrResponseReader` | 정상 코드 `000`/`00`, `cmmMsgHeader` 오류 봉투, JSON이 아닌 본문 → UPSTREAM |
| `ComplexGeocoder` | 보정 주소 우선, 도로명 → 지번 대체, 결과 없음 즉시 FAILED, 503은 attempts 증가 |

### 12.3 aggregate (Flow, Mockito)

- `HouseNightlyFlow`: 키 없음 → 수집 단계 SKIPPED·통계는 실행, 한 단계 예외 → 다음 단계 진행, 단계별 기록 6행
- 수집 우선순위: 최근 3개월 → FAILED → NEVER(최근부터) → 1일에만 4\~12개월
- 예산 소진 시 PARTIAL, 단위 상태 그대로
- `HouseCompareQueryFlow`: `missingIds`, 열 순서, 정비사업 null/[] 구분(SUCCESS 기록 유무), 통계 없는 열
- `HouseQueryFlow`: 영역/`lawdCd` 택일, 0.2도·1.0도, DISTRICT에 영역 → 400, 검색어 이스케이프

### 12.4 네이티브 쿼리 (로컬 DB 직접 검증)

DB 통합 테스트가 없으므로 아래 시드로 로컬 PostgreSQL에서 직접 확인한다. 기대값은 psql로 먼저 계산해 두고 API 응답과 대조한다.

| 시나리오 | 확인 |
| --- | --- |
| 단지 1개, B59 거래: 3개월 안 5건(1건 해제, 1건 removed), 1년 전 창 3건 | `cnt_3m = 3`, 해제·removed 제외, `change_1y_pct`가 반올림된 평균으로 계산됨 |
| 3개월 0건, 6개월 2건 | `recent_window_months = 6`, `change1y` INSUFFICIENT |
| 같은 구간 이상치 1건(중앙값의 +50%) | 통계 제외, `volume_3m`에는 포함 |
| 같은 응답을 두 번 반영 | 거래 수 불변, `entity_version` 불변(값이 같으면 UPDATE 없음) |
| 두 번째 응답에서 1건 빠짐 | 그 행 `removed = true`, 다시 나타나면 false |
| 동 3개(하나는 단지 좌표가 영역 경계에 걸침) | 중심 기준 포함 여부, 필터를 바꿔도 중심 불변 |
| 구 하나에 좌표 있는 단지 없음 | DISTRICT 25행, 그 구 `count = 0`, 중심은 상수 |
| 최근 3개월 단위 75개 중 1개 NEVER | `dataAsOf = null` |
| 영역 경계에 걸친 단지, 가격 경계값(min = 가격) | 양 끝 포함 |

- 배포 전 운영과 같은 규모를 보려면 로컬에서 실제 키로 1개 구 40개월을 수집해 본다(호출 40회 남짓).

### 12.5 rest

- Resource별: 파라미터 바인딩, 오류 코드 렌더링(400/404/503), `summary`·`search` 리터럴 경로가 `{complexId}`로 가지 않음
- 보안: 비로그인 401, USER의 관리자 API 403, ADMIN 200, 조회 API USER 200
- `CommonExceptionHandlerTest`: 새 ErrorCode 2개

---

## 13. 명세와 달라진 점 (명세 반영 완료)

구현 설계 중에 명세 그대로는 운영이 어려운 곳을 바꿨다. FE 계약(API 응답)은 바뀌지 않는다. 아래 항목은 명세 7장·9장·11장에 반영했다.

| # | 명세 | 이 설계 | 이유 |
| --- | --- | --- | --- |
| 1 | 일일 호출 상한 하나(`slcn.house.daily-call-limit`) | API별 상한 4개(`slcn.house.call-limit.*`) | 공공데이터포털 한도는 API별이고, 행안부는 공공데이터포털이 아니다 |
| 2 | 매월 1일 전체 단지 건축물대장 재조회 | 매일 30일 지난 단지부터 남은 예산으로 | 단지 수천 × 최대 2회가 하루 상한을 넘는다. 주기는 같다 |
| 3 | `house_complex_area_stat`에 6개월 창 컬럼 없음 | `avg_6m`, `cnt_6m` 추가 | 최근 실거래가 6개월 대체의 근거값 |
| 4 | 학교 갱신은 180일 주기만 | 새로 좌표가 생긴 단지가 있으면 최근접만 재계산(수집 없음) | 신규 단지의 학교 정보가 최대 180일 비는 것을 막는다 |
| 5 | 정책 시드 SQL의 `'<생성 ID>'` | `gen_random_uuid()` + `WHERE NOT EXISTS` | 재실행 안전, `id_sequence` 불필요 |
| 6 | Facade 5개 | `HouseCompareFacade` 분리로 6개 | 비교표는 여러 저장소를 가로지르는 조립이라 Flow도 따로 둔다 |

---

## 14. 구현 순서 (PR 분할)

명세 12장 단계와 맞춘다. PR마다 `./gradlew test`를 통과해야 하고, 네이티브 쿼리가 들어가는 PR은 본문 §12.4의 해당 시나리오 확인 결과를 PR 본문에 적는다.

| PR | 내용 | 명세 단계 |
| --- | --- | --- |
| 1 | 공통: `ErrorCode` 2개, `HouseConstant`, `SecurityConfiguration` 매처, `application.yml`, `AGENTS.md` | 0 |
| 2 | 정책: `HouseBudgetPolicy*`, `GET /house/budget-policy`, 시드 SQL | 1 |
| 3 | 수집: 게이트웨이(실거래), `CallBudget`, 수집 단위, `HouseTradeLogic.applyUnit`, `HouseNightlyFlow` 단계 1·2, `house_batch_run` | 2 |
| 4 | 보강: `ComplexGeocoder`, 건축물대장 게이트웨이, 단계 3, 관리자 API 2종 | 2 |
| 5 | 통계: 이상치, `AreaStatBuilder`, 단계 6(랭킹 제외), `GET /house/complexes`, `/summary`, `/{complexId}`, `/house/meta` | 3 |
| 6 | 비교: 학교 게이트웨이·단계 4, 정비사업 포트(Disabled)·단계 5, `GET /house/compare`, `/search` | 4 |
| 7 | 랭킹: `RankingBuilder`, 단계 6에 랭킹 추가, `GET /house/rankings` | 5 |

### 14.1 배포 순서

1. 환경변수 설정: `SLCN_DATA_ORG_API_KEY`(Decoding 키), 호출 상한. cron은 아직 비워 둔다
2. 앱 배포 → 테이블 자동 생성 → 유니크 제약 7건·복합 PK 확인(본문 §9.6)
3. 정책 시드 SQL 실행 → `GET /house/budget-policy` 200 확인
4. `SLCN_HOUSE_NIGHTLY_CRON=0 0 3 * * *` 설정 후 재기동
5. 다음 날 아침 확인: `house_batch_run` 6행 상태, `house_collect_unit` 진행률, `/house/meta`

---

## 15. 확인이 필요한 사항

2026-10-21에 발급받은 키로 실제 호출해 확인했다.

| # | 항목 | 상태 | 결과 / 남은 일 |
| --- | --- | --- | --- |
| 1 | 실거래 `aptSeq` 유무, 필드 형식 | 확인 | 670건 중 누락 0건. 숫자·문자열 혼재, 공백 빈 값, `cdealDay` "yy.MM.dd" (§4.1, §4.2). 대체 키는 방어용으로만 남긴다 |
| 2 | JSON 지원, 정상 `resultCode` | 확인 | 세 API 모두 JSON. 실거래 `000`, 건축물대장·학교 `00`. XML 파서는 두지 않는다 (§4.1) |
| 3 | API별 일일 한도 | 일부 | 건축물대장 개발계정 10,000 → 상한 5,000. 실거래·학교 한도는 마이페이지에서 확인 후 조정 (§8.1) |
| 4 | 건축물대장 경로·필드 | 확인 | 총괄표제부·표제부 모두 정상(2026-11-03). 오래된 단지는 총괄표제부 값이 비어 필드 단위로 표제부에서 채운다 (§4.3) |
| 5 | 학교 경로·필드, 서울 필터 | 확인 | 호스트 `api.data.go.kr`, `cddcNm=서울특별시교육청` 필터 (§4.5) |
| 6 | 정비사업 데이터셋[명세 §13 #2] | 미정 | BE 조사. 정해질 때까지 Disabled (§4.6) |
| 7 | 이용허락범위(DB 저장 가능 여부) | 일부 | 건축물대장 제한 없음. 실거래·학교는 데이터셋 페이지에서 확인. 저장 불가면 그 소스는 쓰지 않는다 |

## 참고

- `docs/myseoulhouse/spec.md` — 기능 명세 (FE·BE 합의본)
- `docs/field_research/implementation_design.md` — 직전 구현 설계 (관례: `id_sequence`, JSON TEXT, `ddl-auto` 주의)
- `docs/field_research_map/02-legal-and-terms.md` — 지도·주소 API 저장 약관
- `slcn-aggregate/.../external/juso/JusoAddressGateway.java` — 외부 연동 로그·오류 분류 패턴
- `slcn-aggregate/.../file/logic/RawUploadCleanupScheduler.java` — 스케줄러 패턴
