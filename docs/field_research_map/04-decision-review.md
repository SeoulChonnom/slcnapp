# 04. 확정 결정 기준 재검토

> 작성일: 2026-10-08 (같은 날 2차 결정 D5·D6, 3차 결정 D7~D9, 4차 결정 D10, 5차 결정 D11~D14 반영). 01~03 문서는 선택지를 펼쳐 놓은 조사 문서다. 이 문서는 **아래 결정을 전제로 한 설계 기준**이며,
> 03 문서의 §2(모델링), §3(데이터·API), §7(단계)과 충돌하면 **이 문서가 우선**한다.
>
> 2026-10-08 보완: BE 코드(`slcnapp`)와 대조해 빠진 변경 지점을 §5.3~§5.5, §6.5~§6.6에 더했다.
> 검토 중 나온 결정 항목 P1~P4는 **D11~D14로 확정**했고, 선택지와 근거는 §9에 남겼다. FE 검토는 §10, 그에 대한 BE 재검토와 배포 절차는 §10.4에 있다. 구현은 한 브랜치에서 **커밋 단위**로 진행한다(§7).

## 0. 확정된 결정

| # | 결정 | 비고 |
|---|---|---|
| D1 | 좌표는 **행정안전부 도로명주소 API**(검색 API + 좌표제공 API)로만 얻어 저장한다 | SGIS, 카카오 로컬 API는 쓰지 않는다 |
| D2 | **GPS(브라우저 Geolocation)는 쓰지 않는다** | 출발지는 사용자가 주소로 입력하거나 생략한다 |
| D3 | "다녀오기 전" 계획은 **기존 `DRAFT` 임장**으로 표현한다 | 별도 계획 엔티티를 두지 않는다 |
| D4 | **다녀온 순서**를 기록하고, 나중에 임장 상세 화면에서 지도로 다시 볼 수 있어야 한다 | 신규 요구 |
| D5 | 임장은 **계획 수립 → 임장 완료** 순서로만 진행한다 | 2차 결정. DRAFT = 계획, COMPLETED = 완료 (§5) |
| D6 | **화면에 보이는 매물 순서대로** 경로를 그린다. 실제 걸은 길은 기록하지 않는다 | 2차 결정. D4의 "다녀온 순서" = 화면 순서 (§6) |
| D7 | 완료 뒤 되돌리기는 **A안(최초 완료 시각 `completedAt`)** 으로 처리한다 | 3차 결정 (§5.2) |
| D8 | **추천 순서로 정렬** 기능을 넣는다. 사용자가 **직접 입력한 현재 위치**에서 출발하는 최적 순서를 추천한다 | 3차 결정. GPS 미사용(D2) 유지, 현재 위치는 저장하지 않음 (§6.4) |
| D9 | 매물 수정(PUT) 시 FE가 **위치를 항상 함께 보낸다** | 3차 결정. 좌표가 아니라 주소 식별값을 보내고 BE가 판정 (§6.6) |
| D10 | FE와 BE는 **같은 날 함께 배포**한다 | 4차 결정. 앱 배포 순서는 두지 않되, DB SQL은 새 BE 기동 전에 실행한다 (§10.3, §10.4) |
| D11 | 계획 임장은 "다녀온 임장" 집계에 넣지 않는다. 매물 수 · `topProperty` · 지역 미완료 요약도 **완료 임장 기준**이며, 계획은 `plannedVisitCount`로 따로 센다 | 5차 결정. §9 P1 (b) |
| D12 | 안 간 매물은 1차에서 **삭제한 뒤 완료**한다. `excluded`(「빼고 완료하기」)는 기존 남은 작업으로 따로 구현한다 | 5차 결정. §9 P2 A |
| D13 | 추천 미리보기의 **"현재 위치 → 첫 매물" 구간은 직선**으로만 그린다. `walking-route`는 저장된 좌표만 쓴다 | 5차 결정. §9 P3 A |
| D14 | 지역 행 `plannedVisit`은 **미완료(`completedAt` 없음) 임장 중 `visitedAt`이 가장 이른 1건**이다. FE는 오늘 이후면 "예정", 지났으면 "미완료"로 표시한다 | 5차 결정. §9 P4 A |

## 1. 결정이 바꾸는 것 — 요약

| 영역 | 이전 안(03 문서) | 결정 후 | 효과 |
|---|---|---|---|
| 좌표 출처 | 카카오 키워드 검색 → 행안부 → SGIS | **행안부 검색 → 행안부 좌표제공** | 카카오 로컬 API를 아예 쓰지 않으므로 **약관 회색지대(사용자가 확정한 카카오 주소)가 사라진다.** SGIS의 "영업활동 사전 승낙" 조건도 사라진다 |
| 좌표계 | SGIS가 WGS84로 반환 | 행안부는 **EPSG:5179(UTM-K)** 로 반환 → **WGS84 변환이 필요** | BE에 좌표 변환 로직 추가 |
| 카카오 사용 범위 | 지도 SDK, 로컬 검색, 도보 경로 | **지도 SDK + 도보 경로만** | 두 API 모두 **우리가 보유한 좌표를 입력**으로 쓴다. 카카오 데이터는 저장하지 않는다 |
| 위치정보법 | 출발지 GPS 사용 여부에 따라 신고 리스크 | **GPS 미사용 → 신고 리스크 해소** (§4) | |
| 계획/기록 구분 | DRAFT 재사용 권장 | **DRAFT = 계획, COMPLETED = 완료**로 의미를 바꾸고, 완료 이력(`completedAt`)으로 집계한다 (§5) | BE 변경 필요 (집계 쿼리, 이관) |
| 순서 | `sortOrder`에 추천 순서 저장 | **기존 `sortOrder`(화면 순서) = 경로**. 별도 필드 없음 (§6) | 순서 API는 기존 것을 그대로 쓴다 |

---

## 2. 행안부 도로명주소 API 단일 사용 — 상세 검토

### 2.1 쓰는 API

| API | 엔드포인트 | 키 | 용도 |
|---|---|---|---|
| 도로명주소 검색 API | `business.juso.go.kr/addrlink/addrLinkApi.do` | 검색 API 승인키 | 키워드 → 주소 후보 (`roadAddr`, `jibunAddr`, `bdNm`, `bdMgtSn`, `admCd`, `rnMgtSn`, `udrtYn`, `buldMnnm`, `buldSlno` …) |
| 좌표제공 API | `www.juso.go.kr/addrlink/addrCoordApi.do` | **좌표 API 승인키 (별도 발급, 승인 필요)** | 위 5개 식별값(`admCd`, `rnMgtSn`, `udrtYn`, `buldMnnm`, `buldSlno`) → `entX`, `entY` |

- 두 키는 **따로 발급**받는다. 좌표제공 API는 **승인 대기**가 있다(다른 팀 사례에서 2주 이상 대기 기록). → **0단계에 바로 신청해야 하는 일정 리스크.**
- 운영 키는 서비스 용도를 "운영"으로 하고 **본인인증**을 거쳐 받는다. 개발 키로 먼저 연동해 볼 수 있다.
- **팝업 API는 쓰지 않는다.** 팝업 API(`addrCoordUrl.do`)는 `window.open`과 `returnUrl` 콜백 방식이어서 SPA와 모바일 화면에 맞지 않는다. **BE에서 검색 API와 좌표 API를 서버 간 호출**하고, FE는 자체 검색 UI를 그린다.
- 공공데이터포털 표기상 이용허락범위는 "제한 없음"이다. 결과를 저장할 수 있다는 근거다. 트래픽 한도와 출처 표시 문구는 운영키를 신청할 때 확인한다.

### 2.2 좌표 변환

- `entX`, `entY`는 **EPSG:5179 (GRS80 UTM-K)** 다. 지도와 도보 API는 WGS84(EPSG:4326)를 쓴다.
- 변환 정의:
  ```text
  +proj=tmerc +lat_0=38 +lon_0=127.5 +k=0.9996 +x_0=1000000 +y_0=2000000 +ellps=GRS80 +units=m +no_defs
  ```
- **변환은 BE에서 한 번만** 하고 WGS84로 저장한다(Java면 proj4j 계열). 원본 `entX`/`entY`도 함께 저장해 두면 나중에 다시 계산하거나 감사할 수 있다.
- FE에서는 변환 라이브러리(proj4js)가 필요 없다. 번들과 knip 부담이 없다.

### 2.3 좌표의 의미와 한계

| 한계 | 영향 | 대응 |
|---|---|---|
| 좌표는 **건물 출입구** 좌표(`ent` = entrance)다 | 오히려 도보 경로의 시작·끝점으로 적합하다 | — |
| 아파트 단지는 **건물군 단위로 주소**가 부여된다. 대표 동의 출입구가 나온다 | 대단지에서는 실제 보려는 동과 수백 m 차이가 날 수 있다 | 1차에서는 허용. 필요하면 나중에 "핀 미세조정"을 고려 (사용자가 옮긴 좌표는 카카오 검색 결과가 아니므로 저장 문제는 별도 검토) |
| **POI 검색이 안 된다** (역 이름, 상가명, "스타벅스" 등) | 출발지를 "○○역"으로 고르기 어렵다 | §2.4 |
| **단지명(건물명) 검색 품질은 검증되지 않았다.** 응답에 `bdNm`(건물명)과 `detBdNmList`는 있지만, "트리마제"처럼 단지명만 넣었을 때 잘 찾는지는 **실제 키로 확인해야 한다** | 단지명만 아는 사용자에게 불편할 수 있다 | 0단계에서 실제 단지명 10~20개로 검색 품질을 측정한다. 부족하면 "지역명 + 단지명"(예: "성수 트리마제") 입력을 안내한다 |
| 신축 등으로 **아직 도로명주소가 없는 단지** | 검색이 안 된다 | 좌표 없이 매물을 저장할 수 있게 하고(좌표는 선택 필드), 지도에서는 "위치 미지정"으로 따로 표시한다 |
| 좌표 API가 **승인되지 않거나 지연**될 때 | 1단계가 막힌다 | 같은 행안부의 **위치정보요약DB**(파일, 공공누리 1유형, 이용목적 심사)로 대체할 수 있다. D1(행안부만 사용)과도 맞는다 |

### 2.4 출발지

D1, D2에 따라 출발지 선택지는 다음과 같다.

| 안 | 설명 | 판단 |
|---|---|---|
| **A. 출발지 생략 (권장 기본값)** | 첫 매물에서 시작해 마지막 매물에서 끝나는 열린 경로로 순서를 추천한다 | 추가 입력이 없다. 임장은 보통 "역에서 내려 근처 단지들"이라 첫 매물 = 역에서 가장 가까운 곳이 되도록 하려면 B가 필요하다 |
| B. 출발지를 주소로 입력 | 행안부 검색으로 역 출구 근처 건물이나 역사 주소(예: "○○로 지하 ○○")를 고른다 | 지하철 역사가 도로명주소 검색에 잡히는지 **검증 필요** |
| C. 역 좌표 공공데이터 사용 | 철도 운영기관이 공개하는 역 위치 데이터 | **D1을 벗어나므로 이번 범위에서는 제외** |

→ D6(화면 순서 = 경로)에 따라 **A안으로 확정**한다. 출발지는 화면 첫 번째 매물이다. B안(출발지 주소 입력)은 나중에 필요하면 임장에 `startAddress`와 좌표를 추가해 확장한다.

---

## 3. 카카오 사용 범위 재확인

| API | 입력 | 저장 | 약관 판단 |
|---|---|---|---|
| 지도 JavaScript SDK | 우리 좌표로 마커와 폴리라인을 그린다 | — | "서비스가 자체 보유한 좌표를 카카오맵에 표시하는 것은 가능"(데브톡 답변) ✅ |
| 도보 경로 조회 (`/v2/routing/walk`) | 우리 좌표 | **응답 저장 금지**로 간주. 화면 세션 동안의 메모리 캐시(React Query)만 쓴다 | 실시간 호출 ✅. 세션 캐시는 "사용자 환경 개선 목적의 단기 캐시"로 보이나 **데브톡에 서면 확인 권장** |
| 로컬 API (키워드·주소 검색) | — | — | **사용하지 않는다** |

- 남은 카카오 확인 사항은 **도보 경로 응답을 세션 동안 캐시해도 되는지** 하나로 줄어든다.
- 무료 쿼터 주의(운영 앱에서 카카오맵을 먼저 활성화)는 그대로 유효하다.

---

## 4. GPS 미사용에 따른 위치정보법 판단

- 위치정보법상 "위치정보"는 이동성 있는 물건이나 개인의 위치를 **전기통신설비 등을 이용해 수집한** 정보다. 사용자가 **직접 입력한 주소**와 그 주소에서 얻은 좌표는 이 수집 방식에 해당하지 않는다고 보는 것이 일반적이다. → **위치기반서비스사업 신고 대상이 아니라고 판단한다**(법률 자문 아님).
- 다만 D4로 **"이 사용자가 ○월 ○일에 A → B → C 순서로 다녀왔다"는 이동 이력**이 저장된다. 이것은 위치정보법보다는 **개인정보보호법** 관점의 데이터다. 기존 임장 기록(지역, 방문일, 매물)과 같은 성격이라 새로운 유형의 리스크는 아니지만, 다음을 지킨다.
  - 외부 API(카카오 도보 경로)에는 **좌표만** 보내고 사용자나 임장 식별자를 붙이지 않는다.
  - 임장 삭제 시 순서·출발지 데이터도 함께 삭제한다(기존 `DELETE /inspection-visits/{id}`의 하위 데이터 삭제 범위에 포함).
- **GPS 미사용을 코드로 보장한다.**
  - `navigator.geolocation` 호출 금지(코드 리뷰 체크리스트, 가능하면 lint 규칙).
  - 카카오 SDK의 "현재 위치" 관련 기능이나 컨트롤을 붙이지 않는다.
- D8의 "현재 위치"는 사용자가 **주소로 직접 입력**하는 값이다. 단말에서 수집하지 않고 서버에 저장하지도 않는다(§6.4).

---

## 5. 생명주기: "계획 수립 → 임장 완료" 단방향 (D5)

### 5.1 가능한가 — 결론

**가능하다.** 오히려 1차 검토안(`visitedConfirmed` 플래그와 "다녀왔어요" 단계)보다 단순해진다.
지금도 임장은 **항상 `DRAFT`로 생성**되고 조건을 채우면 `COMPLETED`가 된다. 두 상태의 의미만 바꾸면 된다.

| 상태 | 기존 의미 | 새 의미 |
|---|---|---|
| `DRAFT` | 다녀왔는데 아직 덜 쓴 기록 | **계획 수립** (다녀온 뒤 결과를 채우는 중인 상태 포함) |
| `COMPLETED` | 작성 완료 | **임장 완료** — 이때부터 "다녀온 임장"으로 집계된다 |

- 사용자에게 보이는 흐름은 **등록 = 계획 수립 → (현장 방문) → 결과 작성 → 완료** 하나뿐이다.
- 안 간 매물은 **삭제한 뒤 완료**한다. 완료 조건이 이미 "등록된 매물 **전부** `COMPLETED`"이므로(api.md §5) 별도 정책이 필요 없다.
  - ⚠️ 임장 기록 갭 회신에서 「빼고 완료하기」용 `ViewedProperty.excluded` 플래그를 이미 결정해 두었다(`docs/field_research/api_gap_review.md` B-⑦, 미구현). 계획 기능이 생기면 "계획했지만 안 간 매물"이 바로 그 대상이 되므로 두 결정이 겹친다 → **D12: 1차는 삭제, `excluded`는 따로 구현**한다. `excluded`를 구현할 때 계획 대비 실제를 남기는 용도로 다시 연결한다.

### 5.2 막아야 하는 것 — "완료 → 계획"으로 되돌아가는 경로 (BE 코드 확인 결과)

`slcnapp` 코드상 완료된 임장이 다시 `DRAFT`가 되는 경로가 **세 가지** 있다. 새 의미에서는 이것이 "**다녀온 임장이 계획으로 돌아가는 것**"이 된다.

| # | 경로 | 위치 |
|---|---|---|
| ① | 사용자가 "작성 중으로 되돌리기"를 누른다 | FE `InspectionVisitEditSection.tsx` → `PATCH /inspection-visits/{id}/status` |
| ② | 완료된 임장에 **매물을 추가**하면 자동으로 DRAFT가 된다 | BE `ViewedPropertyFlow.registerViewedProperty` → `revertVisitToDraft` |
| ③ | 매물을 DRAFT로 되돌리면 임장도 자동으로 DRAFT가 된다 | BE `ViewedPropertyFlow.changeViewedPropertyStatus` → `revertVisitToDraft` |

두 가지 방식 중 하나를 고른다.

| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A. 완료 이력 기록 (권장)** | 임장에 `completedAt`(**최초 완료 시각**)을 추가한다. 처음 `COMPLETED`가 될 때 기록하고 **다시는 지우지 않는다.** 집계와 "계획" 표시는 `status`가 아니라 `completedAt` 유무로 판단한다 | ①②③을 그대로 두어도 된다. 완료 뒤에도 매물을 추가하거나 고칠 수 있다. 사용자 입장에서는 한 번 완료한 임장이 **절대 "계획"으로 돌아가지 않는다**(되돌리면 "수정 중"으로 보인다) | 필드 하나 추가. 표시 상태가 3가지(계획 / 수정 중 / 완료)가 된다 |
| B. 상태 자체를 단방향으로 | ① 버튼 제거, ② 완료된 임장에는 매물 추가 금지, ③ 완료된 임장의 매물은 DRAFT 전이 금지. 완료 뒤 수정은 조건을 유지하는 범위에서만 허용(이미 `revalidateIfCompleted`가 있다) | 상태가 정확히 2개다. 의미가 가장 단순하다 | **완료 뒤에 빠뜨린 매물을 추가할 수 없다**(새 매물은 문답이 비어 있어 바로 완료 상태가 될 수 없다). 필수 항목을 바꾸려면 값을 덮어쓰는 방식만 가능하다 |

→ **A안으로 확정 (D7).** 원하는 "계획 → 완료" 순서를 사용자 경험으로 보장하면서, 이미 만들어 둔 수정 흐름을 깨지 않는다.

**기록 위치:** 임장이 `COMPLETED`가 되는 경로는 `InspectionVisitFlow.changeInspectionVisitStatus` 하나뿐이다(매물 쪽 경로는 임장을 `DRAFT`로만 바꾼다).
그래도 Flow가 아니라 **엔티티 `InspectionVisit.changeStatus` 안에서** "`COMPLETED`이고 `completedAt`이 비어 있을 때만 기록"한다. 나중에 완료 경로가 늘어나도 빠뜨릴 수 없다.

```java
public void changeStatus(InspectionStatus status) {
	this.status = status;
	if (InspectionStatus.COMPLETED == status && this.completedAt == null) {
		this.completedAt = LocalDateTime.now();
	}
	this.modifiedTime = System.currentTimeMillis();
}
```

- `completedAt` 타입은 `visitedAt`과 같은 `LocalDateTime`으로 둔다. 집계 쿼리에서 `visited_at`과 나란히 쓰기 때문이다(`modifiedTime`은 epoch millis `long`이라 성격이 다르다).
- Jpo·JpoMapper·`InspectionVisitMapper`(Rdo)에 필드를 이어 준다. Rdo에는 FE 배지 판정용으로 노출한다(§5.6).

### 5.3 집계 보정 (A안 기준, BE 필수)

BE 확인 결과 지역 집계 쿼리(`InspectionAreaRepository`)는 **상태와 무관하게 모든 임장**을 센다. 계획을 등록하는 순간 아래가 틀어진다.

| 항목 | 현재 쿼리 | 보정 |
|---|---|---|
| 최근 방문순 정렬 · `lastVisitedAt` ("최근 MM.DD") | `MAX(v.visited_at)` | `MAX(CASE WHEN v.completed_at IS NOT NULL THEN v.visited_at END)` |
| 방문 횟수순 정렬 · `visitCount` | `COUNT(DISTINCT v.id)` | `COUNT(DISTINCT CASE WHEN v.completed_at IS NOT NULL THEN v.id END)` |
| 재방문 의사 필터 · `revisitIntentCounts` | 지역별 최신 회차 1건(`ORDER BY visited_at DESC`) | 최신 **완료** 회차 1건 |
| `totals.visitCount` | `COUNT(*)` | 완료된 임장만 |
| 키워드 검색 | 모든 임장·매물 | **그대로 둔다** (계획 단계 단지명으로도 검색되는 편이 낫다) |

- `firstVisitedAt`, `latestVisit` 등 지역 행을 만드는 나머지 계산도 같은 기준으로 맞춘다.

#### 코드 대조로 추가된 보정 지점

위 표는 네이티브 쿼리만 다룬다. 실제로는 **Java 집계와 서브쿼리**에도 같은 기준이 필요하다. 하나라도 빠지면 정렬은 맞는데 화면 숫자가 틀리는 식으로 어긋난다.

| # | 위치 | 현재 | 보정 |
|---|---|---|---|
| 1 | `InspectionAreaRepository` 지역 목록 쿼리 4종의 **재방문 의사 필터 서브쿼리** ("지역별 최신 회차", `ORDER BY iv2.visited_at DESC, iv2.id ASC LIMIT 1`) | 계획 포함 최신 회차 | `WHERE iv2.completed_at IS NOT NULL` 추가 |
| 2 | `InspectionAreaRepository.countAreasByLatestRevisitIntent` (`DISTINCT ON`) | 계획 포함 | CTE에 같은 조건 추가 |
| 3 | `InspectionAreaRepository.countAllVisits` (`totals.visitCount`) | 모든 임장 | 완료 임장만 |
| 4 | `InspectionAreaRepository.countAllProperties` (`totals.propertyCount`) | 모든 매물 | 완료 임장의 매물만 (D11) |
| 5 | `InspectionAreaQueryFlow.toAreaRdo` — `visits.size()`, 최초·최근 방문일(`min`/`max` `visitedAt`), `latest` 회차 | Java에서 전체 임장으로 계산 | 완료 임장만 걸러서 계산. `allProperties`(지역 매물 수, `topProperty`)도 완료 임장 기준 (D11) |
| 6 | `InspectionAreaQueryFlow.latestVisitIds` (최신 회차 태그) | 전체 임장 중 최신 | 완료 임장 중 최신 |
| 7 | `InspectionAreaQueryFlow` 지역 상세의 최신 회차 선택(`visitedAt` 내림차순 첫 행) | 계획이 미래 날짜면 계획이 "최신 회차"가 된다 | 기본 선택은 완료 임장 중 최신. 계획은 목록에 남기되 배지로 구분 |
| 8 | `InspectionSummarySupport.ofArea` — `draftVisitCount`, `draftPropertyCount` | `DRAFT`면 모두 "미완료" | "수정 중"(`completedAt` 있음 + `DRAFT`)만 미완료로 센다. 계획 임장과 그 매물은 `draftPropertyCount` · `unansweredRequiredCount`에서도 빼고, 계획 수는 `plannedVisitCount`로 따로 센다 (D11) |

- 임장 단건의 미완료 요약(`ofVisitWithProperties`)은 그대로 둔다. 계획 임장에서도 "완료하려면 무엇이 남았나"는 같은 의미다.
- ⚠️ 이 프로젝트에는 DB 통합 테스트가 없다. 단위 테스트가 통과해도 네이티브 쿼리는 검증되지 않으므로, **쿼리를 바꾼 커밋은 앱을 띄워 지역 목록 · 정렬 3종 · 재방문 필터 · totals를 실제로 호출해 본다.**
- 지역 행에 **`plannedVisit: { inspectionVisitId, visitedAt } | null`을 필수로 내려준다**(§10.2 F3). 계획만 있는 지역이 목록에서 "방문 0회"로만 보이지 않게 하기 위해서다.
  - 고르는 규칙(D14): `completedAt IS NULL`인 임장 중 `visitedAt` 오름차순, 같으면 `id` 오름차순 첫 건. 없으면 `null`.
  - "오늘"은 BE가 판정하지 않는다. FE가 `visitedAt`을 오늘과 비교해 "예정 MM.DD" / "미완료 MM.DD"로 표시한다.
- 임장 목록(`/inspection-visits`)은 계획도 보여주되 "계획" 배지로 구분한다. 정렬(`visitedAt` 내림차순)에 따라 미래 계획이 맨 위에 오는데, 이는 자연스러운 동작이다.

### 5.4 기존 데이터 이관

| 기존 상태 | 이관 | 결과 |
|---|---|---|
| `COMPLETED` | `completedAt = modifiedTime` (근사값) | 지금처럼 방문으로 집계된다 |
| `DRAFT` (기능 도입 전, 실제로는 **다녀온 뒤 덜 쓴** 기록) | 그대로 두면 **"계획"으로 보이고 집계에서 빠진다** | 배포 전에 건수를 확인한다. 몇 건이면 완료 처리하고, 많으면 일회성 이관 플래그를 검토한다 |

2인 서비스이므로(AGENTS.md Service Scale) 백업 테이블이나 범용 백필 스크립트 없이 **영향받는 행을 직접 조회한 뒤** 처리한다.

**새 BE를 띄우기 전에 실행한다(D10 같은 날 배포).** `ddl-auto=update`가 컬럼을 만들기를 기다렸다가 채우면, 그 사이에 기존 완료 임장이 모두 `completedAt` 없음이 된다. 이때 지역 집계에서 방문 기록이 전부 빠지고, FE 배지 규칙에 따라 "계획"으로 보일 수 있다. 컬럼을 SQL로 먼저 만들어 두면 `ddl-auto`는 이미 있는 컬럼을 건너뛴다.

```sql
-- 0) 컬럼을 먼저 만든다 (Jpo 매핑과 같은 타입)
ALTER TABLE slcn.inspection_visit ADD COLUMN IF NOT EXISTS completed_at timestamp(6);

-- 1) 대상 확인
SELECT id, status, visited_at, to_timestamp(modified_time / 1000.0) AS modified_at
FROM slcn.inspection_visit
ORDER BY status, visited_at;

-- 2) COMPLETED → completedAt 근사값 채우기
--    modified_time은 epoch millis(UTC 기준)다. visited_at과 같은 벽시계(Asia/Seoul)로 맞춘다.
--    modified_time이 비었거나 방문일보다 이르면(시드 데이터의 1 같은 값) visited_at을 쓴다.
--    앱이 기록하는 값과 같게 초 단위로 자른다.
UPDATE slcn.inspection_visit
SET completed_at = date_trunc('second', CASE
		WHEN modified_time IS NULL
			OR to_timestamp(modified_time / 1000.0) AT TIME ZONE 'Asia/Seoul' < visited_at
		THEN visited_at
		ELSE to_timestamp(modified_time / 1000.0) AT TIME ZONE 'Asia/Seoul'
	END)
WHERE status = 'COMPLETED' AND completed_at IS NULL;

-- 3) 기존 DRAFT는 1)의 결과를 보고 한 건씩 판단한다
--    (실제로 다녀온 기록이면 completed_at을 채운다. status는 그대로 DRAFT → "수정 중"으로 보인다)
```

- 앱 JVM과 DB 세션의 타임존 설정은 실행 전에 확인한다. `visited_at`이 어떤 벽시계로 저장되어 있는지와 맞아야 한다.

### 5.5 계획 단계 입력 완화 (BE 필수)

- `viewed_property.name` 컬럼이 `NOT NULL`이다(`ViewedPropertyJpo`). 계획 단계에서는 동·호수를 모를 수 있다.
  → **`name`을 nullable로 바꾼다.** 완료 조건(`missingFields`에 `name`)이 이미 있으므로 완료 시점 검증은 그대로 유지된다. "미정" 같은 임시 문자열은 단지 자동완성과 회차 연결(정확 일치)을 오염시키므로 쓰지 않는다.
  - ⚠️ **`ddl-auto=update`는 기존 컬럼의 `NOT NULL`을 풀지 않는다.** Jpo의 `nullable = false`를 지우는 것만으로는 DB가 바뀌지 않으므로 배포 때 직접 실행한다.
    ```sql
    ALTER TABLE slcn.viewed_property ALTER COLUMN name DROP NOT NULL;
    ```
  - `name`이 null일 때 BE 동작 (코드 확인 완료 — **BE 읽기 경로는 null에 안전하다**)
    - 키워드 검색(`ViewedPropertyRepository`의 `p.name ILIKE`)은 null이면 거짓이 되어 오류는 없다.
    - 지역 상세의 매물 필터(`InspectionAreaQueryFlow.matches`)는 `wantedName.equals(property.getName())`이라 null이어도 예외 없이 불일치가 된다. null끼리 같은 매물로 묶이지도 않는다.
    - AI 후기 제안 프롬프트(`ReviewSuggestionPromptBuilder.appendLine`)는 빈 값이면 그 줄을 생략한다.
    - 깨지는 쪽은 **FE 응답 스키마**다(§10.2 F1). C4와 FC1은 반드시 같은 배포에 넣는다.
    - **등록·수정 검증도 함께 바꿔야 한다.** `ViewedPropertyLogic.applyUpdate`가 `requireText(name, "매물명은 필수입니다.")`로 막고 있다. 길이 검사만 남기고 빈 값은 null로 저장한다. 완료 검증(`missingFields`의 `name`)은 그대로 둔다.
    - Rdo·FE 표시에서 null을 "동·호수 미정"으로 표시한다.
- `complexName`은 계획 단계에서도 필수로 둔다. 행안부 검색 결과의 `bdNm`(건물명)으로 미리 채워 주면 표기 흔들림도 줄어든다.
- 문답 스냅샷이 계획 시점에 생성되는 문제(질문이 바뀌면 이전 버전 문답이 남음)는 `isCurrentVersion`으로 표시되므로 1차에서는 허용한다.

### 5.6 화면 매핑

| 화면 | 역할 | 변경 |
|---|---|---|
| 임장 등록 마법사 (지역 → 기본 정보 → 확인 매물) | **계획 수립** | 2단계에서 결과 항목(재방문 의사, 한줄평, 장단점)을 숨기고 "예정일"만 받는다. 3단계 매물 추가에 행안부 주소 검색과 지도(순서·경로) 표시 |
| 임장 수정 | **결과 작성 → 완료** | 기존 그대로. 지도는 읽기용으로 표시 |
| 임장 상세 · 지역 상세(`selectedVisit`) | 결과 보기 | 지도(화면 순서대로 경로) 추가 |
| 배지 | | `completedAt` 없음 → **"계획"**, `completedAt` 있음 + DRAFT → **"수정 중"**, COMPLETED → 배지 없음 (기존 `DraftBadge` 확장) |

---

## 6. 경로 = 화면 표시 순서 (D6)

### 6.1 결론 — `visitOrder` 필드가 필요 없다

- 화면에 보이는 순서대로 다닌다면 **기존 `sortOrder`가 곧 동선**이다. 이전 안의 `visitOrder` 필드, 순서 확정 API, "다녀왔어요" 단계를 모두 **없앤다.**
- 순서 변경은 **기존 API**(`PUT /inspection-visits/{visitId}/properties/order`)를 그대로 쓴다. BE 코드상 이 API는 상태 전이를 일으키지 않고 **`COMPLETED` 임장에서도 허용**된다(`modifyViewedPropertyOrder`). 다녀온 뒤 순서를 고쳐도 완료 상태가 유지된다.
- 화면 순서를 바꾸면 경로도 즉시 바뀐다. **"보이는 순서 = 경로 = 다녀온 순서"** 하나의 규칙이다.

### 6.2 경로를 그리는 규칙

| 규칙 | 이유 |
|---|---|
| 순서는 `sortOrder` 값이 아니라 **API 응답 배열 순서**를 쓴다 | `sortOrder` 중복이 허용되고 서버가 2차 키로 순서를 정한다(api.md §6). FE가 다시 정렬하면 화면과 어긋날 수 있다 |
| 좌표가 없는 매물(주소 미입력, 주소 미부여 신축)은 **경로에서 건너뛴다** | 번호는 화면 순서 그대로 유지하고, 지도 밖 목록에 "위치 미지정"으로 표시한다 |
| **연속한** 같은 좌표(같은 단지의 여러 매물)는 한 지점으로 합친다. 마커는 "2·3"처럼 표시한다 | 길이 0인 구간 호출 낭비를 막는다 |
| 같은 단지가 **떨어져서** 나오면(A → B → A) 그대로 다시 돌아가는 경로로 그린다 | 화면 순서를 그대로 따른다는 원칙 |
| 출발지는 **첫 매물**이다 | §2.4 A안 |

### 6.3 표시 단계

| 단계 | 내용 | 외부 호출 |
|---|---|---|
| 기본 | 번호 마커 + 순서대로 **직선 연결선** + 구간 직선거리와 추정 도보 시간(직선 × 1.3 ÷ 4.5km/h) | 지도 SDK만 |
| "도보 경로 보기" | 카카오 도보 경로를 실시간으로 불러와 폴리라인과 실제 시간으로 교체한다. 7개 지점마다 1회 호출 | 카카오 도보 경로 (저장 안 함) |
| 오류 · 429 | 직선 표시를 유지하고 알림을 띄운다 | — |

- 지난 임장에서 보이는 도보 경로는 "**화면 순서대로 걸었을 때의 현재 기준 추천 경로**"다. 실제로 걸은 길은 기록하지 않는다(D2, 사용자 합의). 화면에 짧게 안내한다.

### 6.4 추천 순서로 정렬 — 현재 위치 기준 (D8)

#### 입력: "현재 위치"는 사용자가 직접 입력한다

- D2(GPS 미사용)를 지키기 위해 **현재 위치는 주소 검색으로 입력**받는다. 행안부 검색 API를 쓰므로 매물 주소 입력과 같은 UI를 재사용한다.
  - 예: 지금 서 있는 건물, 내린 역 출구 앞 건물, 도로명·지번 주소
  - 역 이름 자체로 검색되는지는 미확인이다(§2.3). 안내 문구로 "가까운 건물이나 주소를 입력하세요"라고 알린다.
- **현재 위치는 서버에 저장하지 않는다.**
  - BE는 좌표만 구해서(행안부 검색 → 좌표 API → WGS84) 돌려주고 버린다.
  - FE도 이 화면을 쓰는 동안 메모리에만 둔다.
  - 이유: 정렬 계산에만 필요한 값이다. 저장하지 않으면 "특정 시각에 이 사용자가 어디 있었는가"라는 데이터 자체가 생기지 않는다(§4).
- 단말 GPS로 수집하지 않고 사용자가 입력한 값이며 저장하지도 않으므로, 위치정보법상 신고 리스크는 없다고 판단한다(법률 자문 아님).

#### "가장 합리적인 경로"의 정의

| 항목 | 정의 |
|---|---|
| 출발 | 입력한 현재 위치 (고정) |
| 도착 | 자유. 마지막 매물에서 끝난다(열린 경로). 옵션 **"출발지로 돌아오기"** 를 켜면 현재 위치로 돌아오는 닫힌 경로로 계산한다(역으로 돌아가는 경우) |
| 비용 | 총 도보 거리 최소. 1차는 직선거리 × 1.3(도심 우회 보정) |
| 묶음 | 같은 좌표(같은 단지)의 매물은 **한 지점**으로 묶는다. 묶음 안의 순서는 현재 화면 순서를 유지한다 |
| 좌표 없는 매물 | 계산에서 빼고 **맨 뒤에** 현재 상대 순서대로 붙인다. 화면에 "위치 미지정 N건은 마지막에 배치됨"이라고 알린다 |

#### 알고리즘 (FE 순수 함수)

| 지점(단지) 수 | 방법 | 결과 |
|---|---|---|
| ≤ 8 | 전수 탐색 (최대 8! = 40,320가지) | 최적해 |
| 9 ~ 13 | Held-Karp 동적계획법 (13곳 기준 약 100만 연산) | 최적해 |
| 14 이상 | 최근접 이웃으로 초기해 → 2-opt 개선 | 근사해 (현실적으로 드물다) |

- 위치: `src/domains/inspection/utils/route-order.ts` + `__tests__`
  - 입력: 출발 좌표, 지점 목록, 돌아오기 여부
  - 출력: 순서
  - 외부 호출이 없어 결정적으로 테스트할 수 있다.
- 같은 거리의 순서가 여럿이면 현재 화면 순서에 가까운 쪽을 고른다. 다시 눌러도 결과가 흔들리지 않는다.

#### (선택) 2단계 보정 — 실제 도보 시간으로 재평가

- 직선거리는 **한강, 철길, 대단지 담장, 고가도로** 같은 장애물을 모른다. 임장 지역에서 실제로 자주 문제가 된다.
- 보정 방법:
  - 직선거리 기준 **상위 3개 후보 순서**를 뽑는다.
  - 각각 카카오 도보 경로 API로 `totalTime`을 받아 가장 짧은 후보를 고른다.
  - 비용은 후보당 `ceil(지점 수 / 6)`회다. 지점이 7개 이하면 3회다.
- 저장하는 것은 **우리가 고른 순서(`sortOrder`)뿐**이다. 카카오 응답(거리, 시간, 폴리라인)은 저장하지 않는다. "카카오 결과로 순서를 정해 저장하는 것"이 저장 금지에 해당하는지는 해당하지 않는다고 보지만, §3의 카카오 문의에 함께 넣는다.
- 1차 출시에서는 직선거리 기준만 하고, 체감 문제가 있으면 추가한다.

#### 화면 흐름

```text
[추천 순서로 정렬] → 현재 위치 입력(주소 검색) → (옵션) 출발지로 돌아오기
   → 미리보기: 지도에 "현재 위치 → 1 → 2 → …" 경로
              현재 순서와 추천 순서의 총거리 비교 (예: 3.4km → 2.1km)
   → [적용] 기존 PUT /properties/order 로 sortOrder 일괄 저장
   → [되돌리기] 적용 직전 순서로 복구 (같은 API)
```

- 적용 후에도 사용자가 드래그로 자유롭게 고칠 수 있다. 경로는 항상 **화면 순서**를 따른다(D6).
- **현재 위치 → 첫 매물** 구간은 이 화면 세션 동안에만 보인다. 다시 열면 첫 매물부터 그린다(현재 위치를 저장하지 않으므로).
  - ⚠️ 이 구간을 **도보 경로로 그릴 수 없다.** `walking-route`는 남용을 막으려고 저장된 매물 좌표만 쓰기 때문이다(§6.5). → **D13: 이 구간은 직선과 추정 시간만 표시**한다. 화면에 "현재 위치 구간은 직선 거리"라고 안내한다.
- 계획 단계(등록 3단계)와 당일(임장 상세) 양쪽에서 쓸 수 있다. 완료된 임장에서도 순서 API가 허용되지만, 다녀온 순서를 덮어쓰지 않도록 **완료 임장에서는 버튼을 숨긴다.**

### 6.5 API

| 메서드 | 경로 | 설명 | 신규 여부 |
|---|---|---|---|
| PUT | `/inspection-visits/{visitId}/properties/order` | 화면 순서 = 경로 순서 변경 | **기존** |
| GET | `/inspection-visits/{visitId}` | 매물에 `roadAddress`, `latitude`, `longitude` 추가. 임장에 `completedAt` 추가 | 응답 확장 |
| GET | `/geo/addresses?keyword=` | 행안부 검색 프록시 (후보에 좌표 조회용 식별값 포함) | 신규 |
| POST/PUT | `/inspection-visits/{visitId}/properties[/{id}]` | 요청에 선택한 주소의 식별값(`admCd`, `rnMgtSn`, `udrtYn`, `buldMnnm`, `buldSlno`, `bdMgtSn`)을 담는다. **BE가 좌표 API 호출과 WGS84 변환을 한 뒤 저장한다** | 요청 확장 |
| POST | `/inspection-visits/{visitId}/walking-route` | BE가 저장된 좌표를 **응답 순서대로** 읽어 카카오 도보 API를 호출(7점 단위 분할)하고 폴리라인을 반환한다. 저장하지 않는다 | 신규 |
| POST | `/geo/current-location` | (D8) 현재 위치 주소 → WGS84 좌표. **저장하지 않고 돌려주기만 한다** | 신규 |

- 매물 `PUT`에서 위치를 다루는 규칙은 §6.6에 정리했다(D9).
- 경로 API를 `visitId` 기반으로 두면 BE가 저장된 좌표만 쓰므로 우리 키로 임의 경로를 조회하는 남용을 막는다.
- **사용자별 rate limit은 두지 않는다.** 2인 서비스이고, 카카오 유료 설정을 켜지 않으면 일 1,000건을 넘을 때 `429`로 차단될 뿐 과금되지 않는다. 그 `429`가 곧 상한이다. 대신 **카카오 콘솔에서 유료 API 사용을 켜지 않는다**를 운영 규칙으로 둔다.
- `walking-route` 처리 규칙
  - 매물을 **상세 응답과 같은 순서**(`sortOrder`, 서버 2차 키)로 읽는다. 순서를 따로 계산하지 않고 상세 조회와 같은 정렬을 재사용한다.
  - 좌표 없는 매물은 건너뛰고, 연속한 같은 좌표는 하나로 합친다(§6.2).
  - 지점이 2개 미만이면 외부 호출 없이 빈 경로를 돌려준다.
  - 7점 단위로 나눌 때 **앞 구간의 끝점을 다음 구간의 시작점으로 겹친다.** 호출 수는 `ceil((k-1)/6)`이다.
  - 카카오 `429`는 그대로 `429`로, 그 밖의 실패는 `502`로 돌려준다. FE는 두 경우 모두 직선으로 대체한다.
  - 외부로는 **좌표만** 보낸다. 임장 ID, 사용자 정보, 주소 문자열은 보내지 않는다(§4).
- **경로 API는 본문이 없으므로 `GET`도 가능하다.** 다만 외부 유료 호출을 일으키므로 브라우저·프록시가 미리 불러오지 않도록 `POST`로 둔다.


### 6.6 매물 수정 시 위치 전송 규칙 (D9)

**결론: FE가 위치를 항상 함께 보낸다(전체 교체).** 다만 **좌표가 아니라 "주소 식별값"을 보내고, 좌표는 BE가 판정한다.**

#### 왜 "항상 전송"인가 (vs "생략하면 유지")

| 기준 | 항상 전송 (채택) | 생략 = 유지 |
|---|---|---|
| 기존 규칙과의 일관성 | 매물 PUT의 다른 스칼라 필드(단지명, 메모 등)와 같다. "생략 = null" 규칙 하나로 설명된다 | 태그·사진과 같다. 하지만 스칼라 중 위치만 예외가 된다 |
| 위치 삭제 | `location: null`로 명확하다 | 삭제용 별도 표현이 필요하다 |
| 누락 위험 | 빠뜨리면 위치가 지워진다 → **타입으로 막는다**(아래) | 빠뜨려도 안전하다 |
| 현재 FE 영향 | 매물 수정 호출부는 **`InspectionPropertyEditSection` 한 곳**(`useUpdateInspectionProperty`)뿐이다. 고칠 곳이 적다 | 없음 |

- 누락 위험은 TypeScript로 막는다. `ViewedPropertyUdo`에 `location`을 **선택(`?`)이 아닌 필수 키**(`location: PropertyLocationInput | null`)로 넣는다. 새 호출부가 생겨도 빠뜨리면 컴파일 에러가 난다.
- 매물 화면은 상세를 불러와 폼을 채우므로 FE는 이미 위치를 가지고 있다. 그대로 돌려보내면 된다.

#### 무엇을 보내는가 — 좌표를 믿지 않는다

```text
PropertyLocationInput            // 요청
  bdMgtSn       string           // 행안부 건물관리번호 — 같은 위치인지 판단하는 키
  roadAddress   string           // 표시용
  coordKey?     { admCd, rnMgtSn, udrtYn, buldMnnm, buldSlno }
                                 // 주소를 새로 골랐을 때만. 좌표 API 호출에 필요

PropertyLocation                 // 응답 (GET 상세)
  bdMgtSn, roadAddress, latitude, longitude
```

BE 판정 규칙:

| 요청 `location` | BE 동작 |
|---|---|
| `null` | 위치를 지운다 |
| `bdMgtSn`이 저장된 값과 같다 | **저장된 좌표를 유지한다.** 행안부를 다시 호출하지 않는다 |
| `bdMgtSn`이 다르다 (주소를 새로 고름) | `coordKey`로 행안부 좌표 API를 호출하고 WGS84로 변환해 저장한다. `coordKey`가 없으면 `400` |

- 요청에 좌표를 넣지 않으므로 **저장되는 좌표의 출처가 항상 행안부**로 보장된다(D1). FE 버그나 조작으로 다른 출처의 좌표가 섞이지 않는다.
- 저장 값과 같으면 외부 호출이 없으므로 일반 수정(메모 등)의 비용과 지연이 늘지 않는다.
- 상태 전이(`PATCH .../status`), 문답 저장(`PUT .../answers`), 순서 변경은 위치를 건드리지 않는다.
- 매물 등록(`POST`)도 같은 `location` 형태를 쓴다. 등록할 때는 생략하거나 `null`로 보내도 된다(위치 없이 등록 가능).
- 행안부 좌표 API가 실패하면 매물 저장 전체를 실패시킬지, 위치만 비우고 저장할지 정해야 한다. **전체 실패(`502`)를 권장한다.** 사용자가 고른 위치가 조용히 사라지면 안 된다.

#### BE 구현 시 주의 (코드 대조 결과)

- **등록 경로에서 위치가 빠지기 쉽다.** `ViewedPropertyFlow.registerViewedProperty`는 `ViewedPropertyCdo`를 `toUdo`에서 **필드 단위로 직접 복사**해 `applyUpdate`에 넘긴다. Cdo와 Udo에 `location`을 추가해도 `toUdo`에 한 줄을 빠뜨리면 등록 시 위치가 조용히 null이 된다. 이 경로를 검증하는 Flow 테스트를 함께 넣는다.
- **외부 호출은 변경보다 먼저 한다.** `modifyViewedProperty`는 `applyUpdate` → `revalidateIfCompleted` → 저장 순서다. 행안부 좌표 조회를 그보다 **앞에서** 끝내 두면, 실패(`502`) 시 엔티티를 전혀 건드리지 않은 채 끝난다. 트랜잭션 안에서 HTTP를 부르는 것은 2인 규모에서는 문제가 되지 않으므로 Flow를 쪼개지 않는다.
- **판정 로직은 한 곳에 둔다.** "null이면 삭제 / `bdMgtSn` 같으면 유지 / 다르면 조회" 규칙은 등록과 수정이 같이 쓰므로 Flow 사이에 복사하지 않고 하나의 헬퍼(예: `PropertyLocationResolver`)로 둔다.
- **저장 형태는 일반 컬럼으로 한다.** `bd_mgt_sn`, `road_address`, `latitude`, `longitude`, `ent_x`, `ent_y`를 모두 nullable 컬럼으로 추가한다. JSON 컬럼 값 객체로 묶으면 `equals`가 없을 때 쓰기마다 UPDATE가 두 번 나가는 문제가 이미 있었다.
- 응답(`ViewedPropertyDetailRdo`, 임장 상세의 매물 목록, 지역 상세의 매물 목록)에 `location`을 넣는다. 지도는 임장 상세 한 번의 조회로 그릴 수 있어야 한다.

---

## 7. 수정된 단계 계획

| 단계 | 범위 | BE | FE | 외부 |
|---|---|---|---|---|
| **0** | 행안부 검색 · 좌표 키 신청(**진행 중**), 카카오 앱 활성화(운영 앱 먼저), 단지명 검색 품질 측정, 화면 설계 | — | — | 행안부, 카카오 |
| **1** | 생명주기 전환 (§5) | `completedAt` 추가와 이관, 집계 보정(쿼리 + Java), `name` nullable | 등록 마법사를 "계획 수립"으로 바꾸고 결과 항목 숨김, 배지 3종(계획 / 수정 중 / 완료) | — |
| **2** | 주소 입력, 좌표 저장, 지도 마커 · 직선 경로 | 행안부 프록시, 좌표 조회와 5179→WGS84 변환, 위치 컬럼, PUT 규칙 | 주소 검색 UI, 카카오 SDK 로더, 지도 컴포넌트(등록 3단계 · 수정 · 상세), env 키 | 행안부, 카카오 지도 |
| **3** | 도보 경로 | `walking-route` 프록시(구간 분할) | 지연 조회, 429 폴백, 안내 문구 | 카카오 도보 경로 |
| **4** | 추천 순서로 정렬 (D8) | 현재 위치 좌표 변환 API(저장 없음) | 현재 위치 입력 UI, 순서 알고리즘(전수 탐색 / Held-Karp / 2-opt, 순수 함수 + 테스트), 미리보기와 되돌리기 | 행안부 (+ 선택: 카카오 도보로 상위 3개 후보 재평가) |

- 1단계를 지도보다 먼저 한다. 계획 임장을 만드는 순간 지역 목록의 "최근 방문일"과 방문 횟수가 틀어지기 때문이다(§5.3).
- 이전 안보다 줄어든 것: `visitedConfirmed`, `visitOrder`, 순서 확정 API, "다녀왔어요" 화면 단계.

### 7.1 BE 커밋 계획

PR로 나누지 않고 **한 브랜치(`feature/field-research-map`)에서 커밋 단위로** 진행한다. 커밋마다 `./gradlew test`가 통과하는 상태를 유지하고, 쿼리를 건드린 커밋은 앱을 띄워 확인한다.
메시지는 Conventional Commits(`feat:` / `fix:` / `chore:` / `docs:`)와 한국어 요약을 따른다.

| # | 커밋 (예시 메시지) | 범위 | 선행 조건 | 검증 |
|---|---|---|---|---|
| C1 | `feat: 임장 최초 완료 시각(completedAt) 기록` | `InspectionVisit.completedAt`, `changeStatus`에서 최초 1회 기록(§5.2), Jpo · JpoMapper. **Rdo 3종 모두 노출**: `InspectionVisitSummaryRdo`(지역 행 `latestVisit`, 지역 상세 `visits[]`), `InspectionVisitRdo`(임장 목록), `InspectionVisitDetailRdo`(§10.2 F2). 형식은 `visitedAt`과 같은 ISO 문자열 | — | 엔티티 단위 테스트(최초 완료만 기록, 되돌려도 유지, 재완료해도 바뀌지 않음), Mapper 테스트(Rdo 3종에 값이 실림) |
| C2 | `fix: 지역 집계를 완료된 임장 기준으로 보정` | §5.3 표 + 보정 지점 1~7, **지역 행 `plannedVisit` 필수 포함**(§10.2 F3, 규칙은 D14) | C1 | Flow 단위 테스트 + **앱 실행 확인**(정렬 3종, 재방문 필터, totals, 계획만 있는 지역) |
| C3 | `feat: 미완료 요약에서 계획 임장 분리` | §5.3 보정 지점 8, `plannedVisitCount` 추가 (D11) | C1 | `InspectionSummarySupport` 테스트 |
| C4 | `feat: 계획 단계에서 매물명(동·호수) 생략 허용` | `name` nullable, `applyUpdate` 검증 완화, null 표시(§5.5) | — | Logic 테스트(빈 이름 등록 가능, 완료는 차단). **배포 시 `ALTER TABLE` 수동 실행. FE FC1과 같은 배포 필수** |
| C5 | `chore: 행안부 도로명주소 연동 설정 추가` | `aggregate/external/juso/` 클라이언트(`RestClient`), 키 없으면 비활성 구현(Gemini 설정 패턴), env 추가(§7.2) | 검색 키 | 응답 파싱 테스트(정상 · 오류 코드 · 빈 결과) |
| C6 | `feat: 주소 검색 API 추가` | `GET /geo/addresses?keyword=`, 후보에 `bdMgtSn` · `coordKey` 포함 | C5 | Resource 테스트, 실제 키로 단지명 검색 품질 측정(§8) |
| C7 | `feat: 행안부 좌표를 WGS84로 변환` | proj4j로 EPSG:5179 → 4326 변환기 | — | 알려진 좌표 1~2개로 단위 테스트(오차 수 m 이내) |
| C8 | `feat: 매물 위치 저장과 수정 판정 규칙` | 위치 컬럼, Cdo/Udo/Rdo `location`, `PropertyLocationResolver`, 등록 `toUdo` 복사(§6.6) | C5, C7, **좌표 키 승인** | Flow 테스트: null 삭제 / 같은 `bdMgtSn` 유지(외부 호출 0회) / 다른 값 조회 / `coordKey` 없음 400 / 외부 실패 502 시 미저장 / **등록 시 위치 유지** |
| C9 | `feat: 임장 도보 경로 조회 API 추가` | `aggregate/external/kakao/` 도보 경로 클라이언트, `POST /inspection-visits/{visitId}/walking-route`(§6.5 처리 규칙, 출발지 파라미터 없음 — D13) | C8 | 분할 · 병합 · 건너뛰기 단위 테스트, 429/502 매핑 테스트 |
| C10 | `feat: 현재 위치 주소를 좌표로 변환하는 API 추가` | `POST /geo/current-location` (저장 없음) | C5, C7 | Resource 테스트, 저장소 호출이 없음을 검증 |
| C11 | `docs: 임장 지도 API 명세 반영` | `docs/field_research/api.md`, AGENTS.md env 목록 | 각 기능 커밋 | — |

- C1~C4는 외부 키 없이 바로 시작할 수 있다. 좌표 키 승인을 기다리는 동안 이 묶음을 끝낸다.
- C5~C7은 검색 키(즉시 발급)와 개발 키로 진행할 수 있다. **C8만 좌표 키 승인에 묶여 있다.**
- 결정 항목(D11~D14)이 모두 확정되어 C1~C4는 막힌 것 없이 바로 진행할 수 있다.
- API 계약이 바뀌는 커밋(C1, C6, C8~C10)은 커밋 메시지 본문에 요청·응답 예시를 적는다(나중에 PR 설명으로 옮긴다).

### 7.2 외부 연동 배치 (BE)

| 항목 | 결정 | 근거 |
|---|---|---|
| 패키지 | `slcn-aggregate`의 `external/juso/`, `external/kakao/`. 인터페이스는 도메인 쪽(예: `inspection/geo/`)에 두고 구현만 `external/`에 둔다 | 기존 `external/gemini/` + `inspection/suggestion/ReviewSuggestionGenerator` 구조와 같다 |
| 키가 없을 때 | 비활성 구현을 주입하고, 호출하면 "기능 꺼짐" 예외(`503`)로 응답한다. 앱 기동과 테스트는 키 없이 돈다 | `GeminiConfiguration`과 같은 방식 |
| HTTP 클라이언트 | Spring `RestClient` (Boot 3.5, `spring-web`에 이미 포함). 연결·읽기 타임아웃을 설정값으로 둔다 | 새 의존성 없음 |
| 좌표 변환 | `org.locationtech.proj4j:proj4j` 추가 | 투영식을 직접 구현하지 않는다 |
| 프록시 Resource | `slcn-rest`에 `GeoResource`(`/geo/**`), 경로는 기존 `InspectionVisitResource`에 추가 | Resource는 얇게, 조합은 Flow(AGENTS.md) |
| env | `SLCN_JUSO_SEARCH_KEY`, `SLCN_JUSO_COORD_KEY`, `SLCN_KAKAO_REST_KEY` (+ 타임아웃). 비어 있으면 기능 꺼짐 | AGENTS.md "Security & Configuration Tips"에 함께 적는다 |

- 행안부 응답은 **오류도 HTTP 200**으로 오고 `results.common.errorCode`로 구분하는 형식으로 알려져 있다. 특수문자나 SQL 예약어가 들어간 키워드를 거부한다는 보고도 있다. **C5에서 실제 키로 확인**하고, 파서가 HTTP 상태가 아니라 `errorCode`를 본다.
- 카카오 지도 JavaScript 키는 FE env에만 둔다. BE에는 REST 키만 둔다.

## 8. 남은 확인 사항

- [ ] 행안부: 좌표제공 API 승인 소요 기간, 트래픽 한도, 출처 표시 문구
- [ ] 행안부: 단지명만 넣었을 때 검색되는 비율 (실제 키로 측정)
- [ ] 카카오: 도보 경로 응답을 **세션 메모리에 캐시**해도 되는지 (데브톡 서면 확인)
- [x] 결정: §5.2 → **A안** (D7)
- [ ] BE: 기존 `DRAFT` 임장 건수 확인과 이관 방법 (§5.4)
- [x] 결정: 매물 PUT 위치 규칙 → **FE가 항상 함께 전송** (D9, §6.6)
- [ ] 카카오: 도보 경로 결과로 고른 **순서**만 저장하는 것이 저장 금지에 해당하지 않는지 (§6.4 2단계 보정을 넣을 경우)
- [ ] 화면 설계: 등록 3단계 · 수정 · 상세의 지도 배치, 데스크톱과 모바일, "추천 순서로 정렬" 포함 여부
- [ ] 행안부: 오류 응답 형식(`errorCode`)과 키워드 금지 문자 실제 확인 (C5)
- [ ] BE: 앱 JVM · DB 세션 타임존 확인 후 `completed_at` 이관 SQL 실행 (§5.4)
- [ ] BE: 배포 시 `viewed_property.name` `DROP NOT NULL` 수동 실행 (§5.5)
- [x] BE: `name`이 null인 매물의 회차 간 연결 동작 확인 — BE 읽기 경로는 null에 안전 (§5.5)
- [ ] 운영: 카카오 콘솔에서 유료 API 사용을 켜지 않았는지 확인 (§6.5)
- [x] 결정: P1 · P2 · P3 → 권장안 확정 (D11 · D12 · D13)
- [x] 결정: P4 `plannedVisit` 선택 규칙 → A안 확정 (D14)
- [x] BE: C1에 `completedAt`을 목록용 Rdo까지 노출, C2에 `plannedVisit` 필수 포함 (§10.2 F2·F3) — §7.1 표에 반영
- [ ] 배포: 새 BE 기동 전에 DB SQL 실행 (§10.4 배포 절차)

## 9. 결정 기록 (P1~P4 → D11~D14 확정)

코드와 대조하면서 나온 결정 항목이다. **모두 권장안으로 확정**했다(2026-10-08). 선택지와 근거는 나중에 다시 판단할 때를 위해 남긴다.

| 항목 | 확정 | 결정 번호 |
|---|---|---|
| P1 계획 임장 집계 | (b) 완료 임장 기준, 계획은 `plannedVisitCount`로 분리 | D11 |
| P2 `excluded`와 삭제 | A. 1차는 삭제, `excluded`는 따로 | D12 |
| P3 현재 위치 구간 | A. 직선 | D13 |
| P4 `plannedVisit` 규칙 | A. 미완료 중 예정일이 가장 이른 것 | D14 |

### P1. 계획 임장을 집계와 미완료 요약에서 어떻게 셀 것인가 (C2, C3)

| 항목 | 선택지 | 권장 |
|---|---|---|
| `totals.propertyCount`, 지역 행의 매물 수 | (a) 모든 매물 (b) 완료 임장의 매물만 | **(b)** — "본 매물 수"이므로 아직 안 간 매물을 세지 않는다 |
| 지역 행의 `topProperty`(관심도 최고 매물) | (a) 모든 매물 (b) 완료 임장의 매물만 | **(b)** — 계획 매물은 관심도가 비어 있어 실질적 차이는 작지만 기준을 하나로 맞춘다 |
| 지역 미완료 요약 `draftVisitCount` | (a) `DRAFT` 전부 (b) "수정 중"(`completedAt` 있음 + `DRAFT`)만 세고, 계획은 `plannedVisitCount`로 따로 | **(b)** — 계획을 "덜 쓴 기록"으로 보이게 하면 D5의 의미가 흐려진다 |
| 지역 미완료 요약 `draftPropertyCount`, `unansweredRequiredCount` | (a) 모든 임장 (b) 계획 임장의 매물 제외 | **(b)** — 위와 같은 이유 |

### P2. 「빼고 완료하기」(`excluded`)와 "삭제 후 완료"의 관계 (§5.1)

| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A. 1차는 삭제, `excluded`는 기존 백로그대로 (확정, D12)** | 이번 범위에서는 안 간 매물을 삭제한다. `excluded`는 임장 기록 갭 대응의 남은 작업으로 따로 구현한다 | 이번 범위가 늘지 않는다 | 계획에 있던 매물 기록이 사라진다 |
| B. 이번에 `excluded`를 함께 구현 | 안 간 매물은 "제외"로 남기고 완료한다. 지도와 경로에서도 제외 매물을 건너뛴다 | "계획 대비 실제"가 남는다 | 완료 검증 · 미완료 요약 · 집계 · 경로 규칙에 제외 처리가 모두 따라붙는다(C2 · C3 · C9 범위 증가) |

### P3. "현재 위치 → 첫 매물" 구간을 어떻게 그릴 것인가 (§6.4, C9)

| 안 | 내용 | 장점 | 단점 |
|---|---|---|---|
| **A. 직선으로만 그린다 (확정, D13)** | 현재 위치 구간은 직선과 추정 시간만 표시한다. `walking-route`는 저장된 좌표만 쓴다 | 경로 API가 단순하고 남용 여지가 없다 | 첫 구간만 실제 길이 아니다 |
| B. 경로 API가 출발지를 받는다 | `walking-route` 요청 본문에 출발지 `coordKey`(선택)를 받는다. BE가 행안부로 다시 좌표를 구해 맨 앞에 붙인다 | 미리보기 전체가 실제 길이다 | 요청마다 행안부 호출이 하나 늘고, 좌표 출처는 여전히 행안부라 D1은 지킨다 |

### P4. 지역 행 `plannedVisit`은 어느 임장인가 (C2, §10.2 F3)

"가장 가까운 계획 1건"만으로는 정의가 모자라다. D5에 따라 **"다녀왔지만 아직 한 번도 완료하지 않은 임장"도 `completedAt`이 없어 계획으로 분류**되기 때문이다. 예정일이 지난 계획이 생긴다.

| 안 | 고르는 규칙 | 장점 | 단점 |
|---|---|---|---|
| **A. 미완료 중 예정일이 가장 이른 것 (확정, D14)** | `completedAt IS NULL`인 임장 중 `visitedAt` 오름차순 첫 건. FE는 오늘 이후면 "예정 MM.DD", 지났으면 "미완료 MM.DD"로 표시한다 | 규칙 하나로 끝난다. 밀린 계획(다녀왔는데 결과를 안 쓴 임장)이 먼저 보인다 | 지난 계획과 앞으로의 계획이 둘 다 있으면 앞으로의 계획은 목록에서 안 보인다 |
| B. 오늘 이후 중 가장 가까운 것, 없으면 지난 것 중 가장 최근 | 두 단계로 고른다 | "예정" 의미에 가장 가깝다 | "오늘"을 서버 시각으로 판정해야 하고 규칙이 두 갈래다 |

- 어느 안이든 응답 형태는 `plannedVisit: { inspectionVisitId, visitedAt } | null`로 같다. FE 표시만 다르다.
- 개수는 P1의 `plannedVisitCount`(C3)로 따로 내려가므로, 여러 건이 있다는 사실은 그쪽에서 보인다.

## 10. FE 관점 추가 검토 (BE 보완분 대조)

> 2026-10-08. BE 에이전트가 더한 §5.2~§5.5, §6.5~§6.6, §7.1~§7.2, §9를 FE 코드와 BE 코드 양쪽에 대조했다.

### 10.1 BE 보완분 검증 결과 — 모두 코드와 일치

| 주장 | 확인 위치 | 결과 |
|---|---|---|
| 임장이 `COMPLETED`가 되는 경로는 하나뿐 | `InspectionVisitFlow.java:101` (그 밖의 `changeStatus`는 매물용이거나 `DRAFT`로만 바꾼다) | ✅ |
| 등록 · 수정 검증이 `name`을 필수로 막는다 | `ViewedPropertyLogic.java:154` `requireText(…, "매물명은 필수입니다.")` | ✅ |
| `ddl-auto=update` | `slcn-boot/.../application.yml:20` | ✅ |
| 지역 행을 Java에서 전체 임장으로 계산한다 | `InspectionAreaQueryFlow.toAreaRdo` (`visits.size()`, `min`/`max` `visitedAt`), `latestVisitIds` | ✅ |
| `excluded` 결정이 이미 있다 | `docs/field_research/api_gap_review.md` B-⑦ — **BE 저장소에만 있는 문서다.** FE 저장소에서 읽을 때는 BE 저장소 기준 경로로 본다 | ✅ |
| `visitedAt`을 미래 날짜로 넣을 수 있다 (계획 등록의 전제) | FE 등록 폼과 BE 모두 미래 날짜 제한이 없다 | ✅ |

P1~P3의 권장안(P1 (b), P2 A, P3 A)에 **FE도 동의한다.**

### 10.2 FE 쪽에서 새로 찾은 항목

| # | 중요도 | 내용 | 대응 |
|---|---|---|---|
| F1 | **필수** | FE zod 스키마에서 매물 `name`이 `z.string()`이다(`inspection-schemas.ts`의 매물용 스키마 4개: `viewedPropertyBriefSchema`(`topProperty`, 이전·다음 매물), `matchedPropertySchema`, `areaViewedPropertySchema`, `viewedPropertyDetailSchema`). BE C4 이후 `name`이 `null`로 오면 `parseOrThrow`가 실패해 **임장 상세 · 지역 목록 · 매물 상세 화면이 통째로 깨진다** | 매물 `name`이 들어가는 스키마를 `z.string().nullable()`로 바꾸고 화면에서 "동·호수 미정"으로 표시한다. nullable로 넓히는 것은 기존 응답과도 호환된다 |
| F2 | **필수** | 배지(계획 / 수정 중 / 완료)는 **목록 화면**에서 판정한다. 지역 목록 행(`AreaListRow`의 `latestVisit.status`), 지역 상세 회차 목록(`VisitPanel`), 임장 목록이 그렇다. 상세 Rdo에만 `completedAt`이 있으면 목록에서 계획과 수정 중을 구분할 수 없다 | C1 범위에 `completedAt`을 **`InspectionVisitSummaryRdo`(지역 행 `latestVisit`, 지역 상세 `visits[]`), 임장 목록 항목, 임장 상세 모두**에 넣는다고 명시한다 |
| F3 | **필수** | 보정 지점 5·6에 따라 `latestVisit`이 "최신 **완료** 회차"가 되면, **계획만 있는 지역**(첫 임장을 계획으로 등록한 지역)은 지역 목록에서 "방문 0회, 최근 없음"으로만 보인다. 계획이 있다는 사실이 목록에서 사라진다 | §5.3의 `plannedVisit`(가장 가까운 계획 1건: `visitId`, `visitedAt`)을 "필요하면"이 아니라 **C2 범위의 필수 응답 필드**로 둔다. FE는 "예정 10.12" 표시를 붙인다 |
| F4 | 권장 | §6.4의 **2단계 보정**(상위 3개 후보를 도보 시간으로 재평가)은 **아직 저장하지 않은 순서**의 경로가 필요하다. 그런데 `walking-route`는 저장된 순서만 쓴다(§6.5). 둘이 맞지 않는다 | 1차는 2단계 보정을 넣지 않으므로 C9는 그대로 둔다. 도입할 때 `walking-route`에 선택 파라미터 `propertyIds`(이 임장 매물의 **순열**만 허용)를 추가한다. 저장된 좌표만 쓰므로 남용 방지 원칙은 유지된다 |
| F5 | 권장 | 상태 의미가 바뀌면서 **문구**가 바뀐다. "작성 중으로 되돌리기"(`InspectionVisitEditSection`), "작성 중" 배지(`DraftBadge`), "아직 작성 중입니다"(`CompletionChecklist`), 등록 2단계의 "방문일" | 각각 "완료 해제하고 수정", 배지 3종, "아직 완료 조건이 남았습니다", "예정일"로 바꾼다. 다녀온 날짜가 예정일과 다르면 수정 화면에서 `visitedAt`을 고친다고 안내한다 |
| F6 | 확인 | 임장 목록 API의 `status=DRAFT` 필터는 이제 "계획 + 수정 중"을 뜻한다 | FE는 현재 이 필터를 노출하지 않는다(코드 확인). **영향 없음** |
| F7 | 정리 | 03 문서 §3.3의 "사용자별 rate limit" 문장은 04 §6.5("두지 않는다")로 대체되었다 | 03 문서 상단의 대체 목록에 반영했다 |

### 10.3 FE 커밋 계획 (BE §7.1과 짝)

BE와 같은 방식으로 한 브랜치에서 커밋 단위로 진행한다. **FE와 BE는 같은 날 함께 배포**하므로 배포 순서는 따로 두지 않는다. "선행(BE)"은 개발할 때 붙여 볼 BE 커밋이다. 커밋마다 `npx @biomejs/biome check --write src/`, `pnpm typecheck`, `pnpm test`, `pnpm run knip`이 통과해야 한다. UI가 바뀌는 커밋은 Playwright로 1440px(`/main`)과 390px(`/mobile`)을 캡처한다.

| # | 커밋 (예시) | 범위 | 선행(BE) |
|---|---|---|---|
| FC1 | `fix: 매물명(동·호수) 빈 값 응답 허용` | F1. 스키마 nullable, 매퍼와 타입, "동·호수 미정" 표시, 계획 단계 등록 폼에서 `name` 선택 입력 | — |
| FC2 | `feat: 임장 계획 · 수정 중 · 완료 배지 구분` | `completedAt` 스키마, `DraftBadge` 3종, 문구(F5) | C1 (F2 반영본) |
| FC3 | `feat: 지역 목록에 예정 임장 표시` | `plannedVisit`(오늘 이후 "예정 MM.DD" / 지났으면 "미완료 MM.DD", D14), `plannedVisitCount` 표시, 등록 마법사의 계획 모드(결과 항목 숨김, 예정일) | C2, C3 |
| FC4 | `feat: 매물 주소 검색과 지도 표시` | 주소 검색 UI, `location` 필수 키 타입(D9), 카카오 SDK 로더, `VITE_KAKAO_MAP_JS_KEY`, 지도(마커와 직선, 등록 3단계 · 수정 · 상세) | C6, C8 |
| FC5 | `feat: 도보 경로 보기` | 지연 조회, 429/502 시 직선 유지, 안내 문구 | C9 |
| FC6 | `feat: 현재 위치 기준 추천 순서 정렬` | `route-order.ts`(+ 테스트), 현재 위치 입력, 미리보기, 적용과 되돌리기, 완료 임장에서 숨김 | C10 |

- 의존성 추가는 FC4의 지도 래퍼 하나뿐이다(직접 감싸면 0). proj4js는 필요 없다(§2.2).
- FC1은 BE 작업과 무관하게 바로 시작할 수 있다. 다만 FC1이 빠진 채 BE C4가 배포되면 화면이 깨지므로 **같은 배포에 반드시 포함**한다.

### 10.4 BE 재검토 (FE 보완분 대조)

> 2026-10-08. §10의 F1~F7과 D10을 BE 코드에 다시 대조했다.

| # | FE 항목 | BE 판단 | 반영 |
|---|---|---|---|
| F1 | 매물 `name` null 시 FE 스키마 실패 | **동의.** BE 읽기 경로(키워드 검색, 지역 상세 매물 필터, AI 후기 프롬프트)는 null에 안전함을 확인했다. 깨지는 곳은 FE뿐이다 | §5.5 갱신, C4에 "FC1과 같은 배포" 명시 |
| F2 | `completedAt`을 목록 Rdo까지 | **동의.** 임장 Rdo는 `InspectionVisitSummaryRdo` · `InspectionVisitRdo` · `InspectionVisitDetailRdo` 3종이고, 셋 다 `visitedAt`을 문자열로 내린다. `completedAt`도 같은 형식으로 맞춘다 | §7.1 C1 범위 갱신 |
| F3 | `plannedVisit` 필수 | **동의.** 다만 "가장 가까운 계획"의 정의가 모자라다. 예정일이 지난 미완료 임장도 계획으로 분류되기 때문이다 | §9 **P4** 신설, C2 선행 조건에 추가 |
| F4 | 2단계 보정 시 `propertyIds` | 동의. 검증 규칙은 "이 임장 매물 ID의 부분집합, 중복 없음"이다. 저장된 좌표만 쓰는 원칙은 유지된다 | 1차 범위 밖이라 C9는 그대로 |
| F5 | 문구 변경 | FE 단독 작업. BE 오류 메시지 중 "작성 중"을 쓰는 곳이 있는지는 C1에서 함께 확인한다 | — |
| F6 | `status=DRAFT` 필터 의미 변화 | 동의. BE 필터 구현은 바꾸지 않는다 | — |
| F7 | 03 문서 rate limit | 확인. 03 본문 옆에 달았던 철회 메모 대신 상단 대체 목록으로 옮긴 것을 확인했다 | — |

**D10(같은 날 배포)에 따른 BE 배포 절차**

FE와 BE 중 어느 쪽이 먼저 떠도 깨지지 않는다. 새 FE가 보내는 `location`은 옛 BE가 무시하고(Spring Boot의 Jackson 기본값은 모르는 필드를 무시한다), 옛 FE가 null `name`을 받는 일은 FC1과 C4를 함께 배포하면 생기지 않는다.
**순서가 필요한 것은 DB뿐이다.**

1. 새 BE를 띄우기 **전에** SQL을 실행한다.
   - `completed_at` 컬럼 생성과 채우기(§5.4 0~2)
   - `viewed_property.name DROP NOT NULL`(§5.5)
2. 새 BE를 띄운다. 이후 C8에서 생기는 위치 컬럼은 모두 nullable이므로 `ddl-auto`가 만들어도 된다.
3. 새 FE를 배포한다.
4. 기존 `DRAFT` 임장을 §5.4 3)대로 한 건씩 판단한다. 계획으로 남길 것과 다녀온 기록을 가른다.

## 출처

- [주소정보 API 연계 — 주소기반산업지원서비스](https://business.juso.go.kr/jst/jstAddressApiList)
- [행정안전부 실시간 주소정보 조회(검색API) — 공공데이터포털](https://www.data.go.kr/data/15057017/openapi.do)
- [행정안전부 실시간 주소정보 조회(팝업API) — 공공데이터포털](https://www.data.go.kr/data/15056797/openapi.do?recommendDataYn=Y)
- [행정안전부 실시간 주소별 좌표정보 조회(팝업API) — 공공데이터포털](https://www.data.go.kr/data/15057559/openapi.do)
- [행정안전부 도로명주소 위치정보 요약DB](https://www.data.go.kr/data/15050410/fileData.do)
- [도로명 주소 API에서 위·경도 구하기 (EPSG:5179 변환)](https://velog.io/@park0219/%EB%8F%84%EB%A1%9C%EB%AA%85-%EC%A3%BC%EC%86%8C-API%EC%97%90%EC%84%9C-%EC%9C%84%EA%B2%BD%EB%8F%84-%EA%B5%AC%ED%95%98%EA%B8%B0)
- [woowacourse 2026-jachwi-sunbae #250 — 좌표제공 API 승인 대기, EPSG:5179](https://github.com/woowacourse-teams/2026-jachwi-sunbae/issues/250)
- [카카오 데브톡 — 자체 보유 좌표의 카카오맵 표시 허용](https://devtalk.kakao.com/t/local-api/149619)
- [카카오 데브톡 — 아파트 건물군 단위 주소 부여](https://devtalk.kakao.com/t/api/136147)
- [Kakao Developers — 카카오맵 REST API (도보 경로 조회)](https://developers.kakao.com/docs/ko/kakaomap/rest-api)
