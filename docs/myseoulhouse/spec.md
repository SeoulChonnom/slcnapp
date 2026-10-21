# 서울 아파트 내집찾기 — 개발 명세서

Oct 20, 2026 · @유일권

## 1. 개요

SLCN(BE `slcnapp`, FE `slcnfront`)에 "서울 아파트 내집찾기" 기능을 추가한다. 사용자는 예산을 계산하고, 그 예산으로 살 수 있는 단지를 카카오맵에서 찾고, 최대 3개 단지를 비교하고, 서울 랭킹으로 시장을 공부한다.

이 명세는 유튜브 영상(시골쥐의 도시생활, 「같은 돈으로 "제일 좋은 집" 사는법」)의 화면 캡처 분석과 Gemini 초안 검토 결과를 합쳐 작성했고, 현재 BE·FE 코드 구조에 맞춰 다시 정리했다. FE/BE Agent는 이 문서를 기준으로 상세 설계를 진행한다. 문서와 코드가 다르면 코드를 먼저 확인한다.

**범위**

- 지역: 서울 25개 자치구 아파트 매매만 수집·저장
- 면적: 전용 59㎡·84㎡ 구간 중심으로 노출, 저장은 전용면적 원값
- 지도: 카카오맵 JS SDK (지도 표시만. 카카오 로컬 API는 쓰지 않는다)
- 데이터: 공공 OpenAPI 기반 자체 DB 적재, 사용자 요청 시 외부 API 실시간 호출 없음
- 접근: 로그인한 회원만. 공유 링크를 받아도 비회원은 아무것도 볼 수 없다

**비범위 (1차)**

- 전월세, 분양권, 오피스텔, 서울 외 지역
- 회원별 서버 저장 (조건·비교 저장 없음)
- 투자 추천, 매수 권유, LLM이 생성한 단지 사실 정보

**영상 대비 의도적 변경**

| 항목 | 영상 | 본 명세 |
| --- | --- | --- |
| 후보 표시 | 지도 가격 마커 | 지도 마커 + 지도와 동기화되는 후보 리스트 패널 추가 |
| 축소 지도 | 구별 대장 단지 가격 | 구·동별 예산 내 후보 수 묶음 마커. 클릭하면 해당 구·동으로 확대 (4장). 구별 대장은 랭킹 탭에만 |
| 학군·호재 | 직선거리, 정비사업 표기 | 동일. LLM 생성 금지, 공공데이터로만 산출 |
| 대출 규제 수치 | 화면에 고정값 | 정책 테이블로 분리, 시행일 버전 관리 |
| 예산 계산 | (서버/클라이언트 불명) | 브라우저에서 계산. 입력값은 서버와 URL로 보내지 않는다 |
| 부대비용 | 5.67억에 973만 (산식 미공개) | 취득세·생애최초 감면·중개보수·등기비용을 법정 요율로 계산 (3장) |
| 랭킹 표본 | 건수만 표기 | 최소 표본 기준 미달 시 제외 또는 배지 |

**공통 규칙**

- 금액: 만원 단위 정수 (DB `bigint`, API `number`), 화면은 억 단위 소수 둘째 자리 반올림 (지도 라벨만 첫째 자리). 표기 규칙은 11장 "금액 표시"
- 면적: ㎡ 소수 둘째 자리
- 기간: 모든 "최근 N개월"은 **오늘 기준**이다. 통계는 야간 배치가 만들므로 "오늘"은 배치 실행일이다 (7장)
- 모든 화면에 면책 문구 고정: "참고용이며 투자·대출 조언이 아닙니다. 정확한 한도는 금융기관에 문의하세요."
- 데이터 기준일과 정책 기준일을 화면에 항상 표기
- 가격을 보여 주는 곳에는 항상 짧은 기간 라벨을 붙인다 (예: "최근 3개월 · 5건"). 같은 단지가 화면마다 다른 숫자로 보일 때 이유를 알 수 있어야 한다

## 2. 화면 구조와 사용자 흐름

한 페이지(`/main/house`, `/mobile/house`)에 4개 섹션을 두고, 예산 결과가 지도 필터로 흘러가며, 지도·리스트·랭킹에서 담은 단지가 비교로 모인다. 상단 네비는 `내 예산` · `집 찾기` · `매물 비교` · `공부 레퍼런스` 앵커와 `링크 복사` 버튼이다.

```text
┌ 상단 네비: 내 예산 · 집 찾기 · 매물 비교 · 공부 레퍼런스 · [링크 복사] ┐
│                                                                      │
│ ① 내 예산 ── 탐색 범위 [0.8P, P] ──┐                                  │
│   (브라우저 계산, 세션 저장)         ▼                                  │
│ ② 집 찾기   지도(구 묶음 ↔ 동 묶음 ↔ 단지) + 후보 리스트 ─ [비교 담기] ┐ │
│                 ▲                                                  │ │
│                 └── [지도에서 위치 보기] ──┐                          ▼ │
│ ④ 공부 레퍼런스  랭킹 4종 ─────────────────┴──── [비교 담기] ──▶ ③ 매물 비교 │
│                                                    (최대 3개, URL cmp) │
└──────────────────────────────────────────────────────────────────────┘
```

- 예산 계산 전에도 지도와 랭킹은 바로 쓸 수 있다. 예산이 없으면 단지 레이어는 가격 필터 없이 영역 안 단지를 모두 보여 주고, "예산을 계산하면 살 수 있는 단지만 보여 드려요" 안내를 띄운다.
- 평형 토글(59/84)은 페이지 상단에 하나만 두고 지도·후보 리스트·랭킹이 함께 쓴다. 비교함 항목의 평형은 담을 때 고정되며 토글을 바꿔도 따라 바뀌지 않는다 (5장).
- URL에는 평형, 비교 단지, 지도 중심·레벨, 랭킹 탭만 담는다. **예산 입력값과 예산 결과는 URL에 싣지 않는다** (11장).
- `링크 복사`는 현재 URL을 복사하고 "예산 입력값은 링크에 포함되지 않습니다" 토스트를 띄운다.

## 3. 모듈 1 — 내 예산 구하기

예상대출 = min(DSR 한도, LTV 한도, 주택가격별 상품 상한)이다. LTV와 부대비용이 매수가에 비례하므로 매수가를 수치 계산으로 푼다.

**계산 위치**: 브라우저(FE)에서 계산한다. BE는 계산 규칙(정책 수치)만 `GET /house/budget-policy`로 내려 주고, 사용자의 현금·소득 같은 입력값은 서버로 보내지 않는다. 입력값은 탭 단위 `sessionStorage`에만 둔다 (새로고침하면 유지되고, 탭을 닫으면 사라진다).

- 규제 수치는 코드에 넣지 않고 정책 테이블(`house_budget_policy`)에서 시행일 기준으로 내려받는다. 수치가 바뀌면 행만 추가하면 되고 FE를 배포할 필요가 없다.
- 산식 자체(아래 알고리즘)가 바뀌면 FE를 배포해야 한다.
- 계산은 입력 변경 즉시 동기로 한다. 서버 호출이 없으므로 계산 자체에는 디바운스가 필요 없다. 단, 계산 결과(탐색 범위)를 모듈 2 지도 필터로 넘길 때는 300ms 디바운스를 둔다(키 입력마다 지도 API가 호출되지 않게).
- 결과가 입력 오류·미입력·`NO_BUDGET`이면 지도 필터는 "필터 없음"으로 돌아간다.
- 정책 조회가 실패하면(`HOUSE_BUDGET_POLICY_NOT_FOUND` 503, 네트워크 오류) 예산 섹션만 "예산 계산 규칙을 불러오지 못했어요" + `[다시 시도]`로 바꾸고, 지도·비교·랭킹은 그대로 쓸 수 있다.

### 입력

| 필드 | key | 단위·타입 | 영상 예시 | 검증 |
| --- | --- | --- | --- | --- |
| 보유현금 | `cashManwon` | 만원, 정수 | 20,000 | 0 이상 10,000,000 이하 |
| 부부합산 연소득 | `annualIncomeManwon` | 만원, 정수 | 10,000 | 0 이상 1,000,000 이하 |
| 예상 대출금리 | `interestRatePct` | %, 소수 첫째 자리까지 | 5.0 | 0.1 \~ 20.0 |
| 기존대출 월 상환 | `existingMonthlyPaymentManwon` | 만원/월, 정수 | 0 | 0 이상 100,000 이하 |
| 생애최초 | `firstTimeBuyer` | boolean | 예 | — |
| 안전여유금 | `reserveManwon` | 만원, 정수 | 2,000 | 0 이상. 기본값은 정책의 `defaultReserveManwon`, 사용자 수정 가능 |

- 영상의 "가구 상황(신혼부부)", "지역(규제지역·서울)", "관심 가격대" 입력은 1차에서 계산에 영향이 없으므로 넣지 않는다. 지역은 서울(전역 규제지역)로 고정하고, 가격대는 알고리즘이 모든 구간을 풀어서 정한다.
- 기본값: `existingMonthlyPaymentManwon` = 0, `firstTimeBuyer` = false, `reserveManwon` = 비어 있음(= 정책의 `defaultReserveManwon` 사용). 나머지는 비어 있다.
- 빈 값 처리:
  - 안전여유금을 비우면 정책 기본값으로 계산한다. 입력란 placeholder에 기본값을 보여 준다.
  - 보유현금·연소득·금리 중 하나라도 비어 있으면 오류가 아니라 미입력 상태다. 계산하지 않고 결과 패널에 "보유현금, 연소득, 예상 금리를 입력하면 예산을 계산해 드려요"를 보여 준다.
  - 기존대출 월 상환을 비우면 0으로 본다.
- 입력 오류 메시지 (결과 패널에 항목별로 표시):
  - 금리 범위: "예상 대출금리는 0.1%에서 20% 사이로 입력해 주세요" (영상 문구)
  - 금리 소수: "예상 대출금리는 소수 첫째 자리까지 입력해 주세요"
  - 정수 필드: "{필드명}은(는) 0 이상의 숫자로 입력해 주세요"
- 한 필드에 오류가 여럿이면 범위 오류를 먼저 하나만 보여 준다 (예: 0.05는 범위 오류).
- 오류가 있으면 계산하지 않고 결과 패널을 비운다.

### 알고리즘

1. **정책 선택**: 응답 정책의 `ltv`에서 `firstTimeBuyer`가 참이면 `FIRST_TIME`, 아니면 `NON_FIRST` 값을 쓴다.
2. **DSR 한도**. 산정금리 = 입력금리 + 스트레스 가산, 원리금균등, 만기 = 정책값. 연 상환 여력이 음수면 0으로 본다(기존 상환이 여력보다 큰 경우).

```latex
C_{year} = \max(0,\; Income \times DSR - Existing_{month} \times 12)
```

```latex
L_{DSR} = \frac{C_{year}}{12} \times \frac{(1+i)^{n} - 1}{i(1+i)^{n}}, \quad i = \frac{r_{input} + r_{stress}}{100 \times 12}, \; n = 12 \times term
```

3. **구간별 풀이**. 상품 상한은 가격 구간마다 다르다(불연속). 구간 k = (lo_k, hi_k], 상한 Cap_k마다 아래 f를 정의한다.

```latex
f_k(P) = Cash - Reserve - cost(P) + \min(L_{DSR},\; LTV \times P,\; Cap_k) - P
```

   - `cost(P)`가 늘어나는 방향으로만 불연속이고 대출 항의 기울기가 LTV(<1) 이하이므로, f_k는 P에 대해 **엄격히 감소**한다. 그래서 이분법으로 푼다(고정점 반복은 수렴이 느리고 횟수 상한이 필요해서 쓰지 않는다).
   - f_k(lo_k 바로 위) < 0 이면 구간 k에서는 살 수 없다.
   - hi_k가 있고 f_k(hi_k) ≥ 0 이면 P_k = hi_k다. 돈이 남지만 구간 상한을 넘으면 상품 상한이 바뀌므로 경계에서 멈춘다. 남는 돈은 `surplus`로 보여 준다.
   - 그 밖에는 (lo_k, hi_k] 안에서 이분법으로 f_k(P) ≥ 0인 최대 P를 구한다. 구간 폭이 0.001만원 미만이 되면 멈추고 하한 쪽 값을 쓴다. 마지막 구간의 상한은 `Cash − Reserve + Cap_k`로 둔다.
   - 첫 구간에서 `Cash − Reserve ≤ 0`이면 예산이 없다. `NO_BUDGET` 상태로 결과 패널에 "보유현금이 안전여유금보다 적어 매수 예산을 계산할 수 없습니다"를 보여 준다.
4. **선택**: 해가 있는 구간 중 P_k가 가장 큰 것을 채택한다.
5. **출력 반올림**. 모든 금액은 만원 단위로 내림한다.
   - `budget = floor(P)`, `dsrLimit = floor(L_DSR)`, `ltvLimit = floor(LTV × P)`, `appliedLoan = floor(min(...))`
   - `bindingConstraint` = 셋 중 최솟값 항목. 같으면 DSR → LTV → CAP 순으로 고른다
   - 구간 경계에서 멈춘 경우: `costs = ceil(cost(P))`, `surplus = Cash + appliedLoan − Reserve − costs − budget`
   - 그 밖의 경우: `surplus = 0`, `costs = Cash + appliedLoan − Reserve − budget` (내림 차이를 부대비용이 흡수해 자금 구성 합계가 항상 맞는다)
   - 탐색 범위 = [floor(0.8 × budget), budget]

### 부대비용 cost(P)

59㎡·84㎡는 모두 전용 85㎡ 이하이므로 농어촌특별세는 없다.

| 항목 | 계산 | 정책 필드 |
| --- | --- | --- |
| 취득세 | P ≤ 6억: 1% / 6억 < P ≤ 9억: 세율 = (P(억) × 2/3 − 3) × 1/100, **이 비율을** 소수 다섯째 자리에서 반올림해 넷째 자리까지 (7억 → 0.0167 = 1.67%) / 9억 초과: 3% | `costRates.acquisitionTax` |
| 지방교육세 | 감면 전 취득세 × 10% | `costRates.acquisitionTax.localEducationRatio` |
| 생애최초 감면 | 생애최초이고 P ≤ 12억이면 min(취득세, 200만원)을 뺀다 | `costRates.firstTimeRelief` |
| 중개보수 (서울 조례 상한) | 5천만 미만 0.6%(한도 25만) / 2억 미만 0.5%(한도 80만) / 9억 미만 0.4% / 12억 미만 0.5% / 15억 미만 0.6% / 15억 이상 0.7%, 부가세 10% 포함 | `costRates.brokerage` |
| 등기비용 | P × 0.2% (국민주택채권 할인 매도, 법무사 보수, 인지세의 근사치) | `costRates.registrationRatePct` |

- 취득세 누진 구간 산식(6\~9억)은 법 조문 형태라 코드에 둔다. 경계 금액과 요율은 정책값으로 받는다.
- 누진 요율 반올림은 지방세법 제11조 제1항 제8호 나목을 따른다. 조문 산식은 `(취득당시가액 × 2/3억원 − 3) × 1/100`이고, "소수점이하 다섯째자리에서 반올림하여 소수점 넷째자리까지 계산한다"는 `× 1/100`을 곱한 **비율**에 적용된다. 그래서 7억은 0.0167(1.67%), 8억은 0.0233(2.33%)이다. 많은 민간 계산기가 쓰는 1.6667%(% 값을 넷째 자리까지)는 조문과 다르다.
- 구현은 부동소수 오차를 피하려고 만분율 정수로 계산한다: `rateBp = Math.round(P / 150 − 300)`, 세율 = `rateBp / 10000` (P는 만원). P = 60,000이면 100bp(1%), 90,000이면 300bp(3%)로 양쪽 구간과 이어진다.
- 반올림 때문에 세율이 계단식이지만 P가 커질 때 줄지 않으므로 f_k가 엄격히 감소한다는 전제는 유지된다.
- 부가세는 중개사무소가 일반과세자라고 가정한다(보수적으로 예산을 적게 잡는 쪽).

### 출력 (결과 패널)

- 예상 매수 가능 예산 P
- 대출 한도 판정: DSR 한도, LTV 한도(비율 표기), 주택가격별 상품 상한, 실제 적용 예상대출(최솟값 항목 강조)
- 매수 자금 구성: 보유현금 + 예상대출 = 합계, − 부대비용(취득세·중개·등기 펼쳐 보기), − 안전여유금, (− 남는 금액: 구간 경계에서 멈춘 경우만)
- DSR 산정금리 설명: "8.0% = 입력 5.0% + 스트레스 3.0%p"
- 탐색 범위: P의 80% \~ 100% (모듈 2 필터로 전달)
- 정책 기준일, 면책 문구

### 정책 시드 (2026-10 기준)

| 항목 | 시드값 | 근거 |
| --- | --- | --- |
| DSR 비율 | 40% | 영상 화면 |
| 스트레스 가산 | 3.0%p | 영상 화면, 10·15 대책 수도권·규제지역 3.0% |
| 만기 | 30년, 원리금균등 | 영상 화면 |
| LTV (생애최초) | 70% | 영상 화면 |
| LTV (생애최초 아님, 무주택) | 40% | 10·15 대책: 서울 전역 규제지역, LTV 40% |
| 상품 상한 | 15억 이하 6억 / 15억 초과\~25억 이하 4억 / 25억 초과 2억 | 10·15 대책 |
| 취득세 | 6억 이하 1%, 6억 초과\~9억 이하 산식(비율 소수 넷째 자리 반올림), 9억 초과 3%, 지방교육세 10% | 지방세법 제11조 제1항 제8호 (2026-07-01 시행본 확인) |
| 생애최초 감면 | 12억 이하, 한도 200만원 | 지방세특례제한법. 40세 미만 300만원 상향안은 입법예고 단계라 확정 후 반영 |
| 중개보수 | 위 표 | 서울시 주택 중개보수 조례 |
| 등기비용 | 0.2% | 근사치 |
| 안전여유금 기본값 | 2,000만원 | 영상 화면 |

### 검증 케이스 (FE 단위 테스트 필수, 허용오차 0)

산식과 정책 시드가 정해져 있으므로 기대값은 정확히 일치해야 한다. 공통 입력: 금리 5.0, 여유금 2,000. 금액 단위는 만원.

| # | 입력 | 기대 출력 |
| --- | --- | --- |
| 1 | 현금 20,000 / 소득 10,000 / 기존상환 0 / 생애최초 | DSR 45,427 · LTV 40,138 · 상한 60,000 · 대출 40,138(LTV) · 부대비용 798 · **예산 57,340** · 탐색 45,872\~57,340 |
| 2 | 1과 같고 생애최초 아님 | LTV 11,661 · 대출 11,661(LTV) · 부대비용 507 · **예산 29,154** |
| 3 | 1과 같고 소득 0 | DSR 0 · 대출 0(DSR) · 부대비용 142 · **예산 17,858** (감면이 취득세보다 커서 취득세 0) |
| 4 | 1과 같고 기존상환 400/월 | 연 상환 여력 음수 → DSR 0 · **예산 17,858** |
| 5 | 현금 25,000 / 소득 20,000 / 생애최초 | 6\~9억 누진 구간(세율 0.0175) · 대출 49,869(LTV) · 부대비용 1,627 · **예산 71,242** · 탐색 56,993\~71,242 |
| 6 | 현금 50,000 / 소득 30,000 / 생애최초 | 상한 적용 · 대출 60,000(CAP) · 부대비용 4,012 · **예산 103,988** |
| 7 | 현금 105,000 / 소득 30,000 / 생애최초 | 15억 경계에서 멈춤 · 대출 60,000 · 부대비용 6,405 · 남는 금액 6,595 · **예산 150,000** |
| 8 | 현금 125,000 / 소득 30,000 / 생애최초 | 15\~25억 구간 · 대출 40,000(CAP) · 부대비용 6,676 · **예산 156,324** |
| 9 | 현금 300,000 / 소득 10,000 / 생애최초 | 25억 초과 구간 채택 · 대출 20,000(CAP) · 부대비용 13,023 · **예산 304,977** |
| 10 | 1과 같고 소득 5,000 | DSR 22,713 · 대출 22,713(DSR) · 부대비용 499 · **예산 40,214** |
| 11 | 현금 2,000 (= 여유금) | `NO_BUDGET` |
| 12 | 금리 0.05 / 금리 5.25 | 각각 범위 오류 / 소수 오류 |

**영상값과의 차이**: 영상의 5.67억은 부대비용 973만(약 1.72%)을 쓴 결과다. 생애최초 감면을 빼고 계산하면 56,710(5.67억)으로 영상과 거의 같다. 따라서 영상은 감면을 반영하지 않은 것으로 보고, 본 명세는 감면을 반영한 57,340(5.73억)을 기대값으로 삼는다. DSR(4.54억)과 LTV 비율(70%)은 영상과 같다.

## 4. 모듈 2 — 예산에 맞는 서울 아파트 (지도)

모듈 1의 탐색 범위(예산의 80\~100%)에 최근 실거래가가 들어오는 단지를 카카오맵에 표시하고, 같은 목록을 지도 옆 리스트로 보여 준다. 축소 상태에서는 단지를 하나하나 그리지 않고 구·동별 후보 수로 묶어 보여 준다. 서울 전체에서 "내 예산으로 어느 동네가 가능한지"를 먼저 보고, 원하는 곳을 눌러 들어가는 흐름이다.

### 지도 레이어

| 레이어 | 표시 조건 (카카오 level, 실측 후 조정) | 마커 | 데이터 |
| --- | --- | --- | --- |
| 구 묶음 | level ≥ 8, 또는 동 묶음 레벨인데 영역 한 변이 1.0도 초과 | 구 중심에 원형 마커 "강북구 · 42" | `GET /house/complexes/summary?groupBy=DISTRICT` (영역 없이, 항상 서울 전체 25행) |
| 동 묶음 | (level 6\~7, 또는 단지 레벨인데 영역 한 변이 0.2도 초과) 이고 영역 한 변이 1.0도 이하 | 동 중심에 원형 마커 "미아동 · 12" | `GET /house/complexes/summary?groupBy=DONG` + 영역 |
| 단지 | level ≤ 5 이고 영역 한 변이 0.2도 이하 | 단지 가격 라벨 (억, 소수 첫째 자리) | `GET /house/complexes` |

- 숫자는 예산이 있으면 예산 내 후보 수, 없으면 좌표가 있는 전체 단지 수다. 후보가 0개인 구·동도 회색 "0" 마커로 보여 준다(숨기면 데이터가 없는 건지 살 수 있는 단지가 없는 건지 구분할 수 없다).
- 묶음 마커 클릭: 구 → 그 구 중심으로 이동 + 동 묶음 레벨(7)로 확대, 동 → 그 동 중심으로 이동 + 단지 레벨(5)로 확대. 후보 리스트도 새 영역 기준으로 갱신된다.
- "0.2도"는 지도에 보이는 영역의 위경도 폭이다. 서울 위도에서 경도 0.2도는 약 17.6km다. 같은 level이라도 모니터가 넓으면 보이는 영역이 넓어지므로, 단지 레벨에서도 영역이 0.2도를 넘으면 단지 조회 대신 동 묶음을 보여 준다(BE의 0.2도 검증에 걸리지 않게).
- 영상 화면의 "ZOOM 14+ → COMPLEX LAYER"는 웹 메르카토르 줌 기준으로 보인다. 카카오맵 level은 숫자가 작을수록 확대이므로 임계값은 실측 후 정한다.
- 구별 대장 단지는 지도에 표시하지 않고 랭킹 탭(6장)에서만 보여 준다.
- 초기 지도: URL에 `lat`·`lng`·`level`이 없으면 서울시청(37.5665, 126.9780), level 8(구 묶음)로 연다.
- 가격 라벨·묶음 마커는 `CustomOverlay`, 단지 레이어에서 라벨이 겹치면 `MarkerClusterer`(SDK `libraries=clusterer`). 행정구역 묶음은 클러스터러가 아니라 BE 집계로 만든다(클러스터러는 화면 거리로만 묶고, 단지는 최대 300건만 내려오므로 개수가 정확하지 않다).
- 지도 `idle` 이벤트마다 영역 조회, 300ms 디바운스. 영역 좌표는 소수 셋째 자리로 **바깥쪽으로** 맞춘다(sw는 내림, ne는 올림). 맞춘 값을 요청 파라미터와 react-query 캐시 키(+ 평형 + 가격 범위 + 레이어)에 똑같이 쓴다.
- 단지 마커 클릭 → 정보 카드(`GET /house/complexes/{complexId}`): 단지명, 구·동, 최근 실거래가(기간 라벨·건수), 연식, 세대수, `[비교 담기]`.

### 필터

- 평형 토글: `59㎡` / `84㎡` (영상), 기본 59. 페이지 상단 토글 하나를 지도·리스트·랭킹이 함께 쓴다. 구간 정의는 8장.
- 가격 범위: 모듈 1 결과가 있으면 \[0.8P, P\] (양 끝 포함), 없으면 필터 없음.
- 후보 개수 표기: "N개 후보" (영상: "34/34 Budget Candidates"의 앞 숫자는 화면에 그린 수, 뒤 숫자는 영역 안 전체 수). 단지 레이어에서 `truncated: true`면 "{total}개 중 300개 표시 · 지도를 확대하면 모두 볼 수 있어요".

### 최근 실거래가 규칙 (지도·리스트·정보 카드·비교표 공통)

같은 단지가 화면마다 다른 숫자로 보이지 않도록 지도와 비교표는 같은 값을 쓴다.

1. 해당 단지·평형 구간의 최근 3개월 실거래 평균 (해제·이상치·원천 삭제 제외). 라벨 "최근 3개월 · N건".
2. 3개월 내 거래가 0건이면 최근 6개월 평균. 라벨 "최근 6개월 · N건".
3. 6개월 내에도 거래가 없으면 가격을 비우고 "최근 6개월 거래 없음"으로 안내한다. 이 단지는 지도 후보(가격 필터)에서 빠지고, 예산이 없을 때의 단지 레이어에서는 가격 없는 회색 점으로 표시한다.
4. 거래 건수 3건 미만이면 라벨에 "표본 적음" 배지를 붙인다.

랭킹의 구별 대장만 최근 12개월 평균을 쓰고, 화면에 "최근 12개월 평균"으로 표기한다 (6장).

### 후보 리스트 패널 (영상 대비 추가)

- 단지 레이어: 지도 현재 영역의 후보를 최근 실거래가 내림차순으로 표시, 항목 클릭 시 지도 이동 + 정보 카드.
- 구·동 묶음 레이어: 구·동 이름과 후보 수를 후보 수 내림차순으로 표시, 항목 클릭은 묶음 마커 클릭과 같다(확대).
- 데스크톱은 지도(좌) + 리스트(우) 2열, 모바일은 지도 아래 바텀시트.
- 단지 항목: 단지명, 구, 최근 실거래가(기간 라벨), 연식, 세대수, `[비교 담기]`. `[비교 담기]`는 현재 평형 토글 값으로 담는다.

### 지도 없는 모드

카카오 JS 키가 없으면(로컬·테스트) 지도 대신 리스트만 보여 준다. 영역 좌표가 없으므로 다음 순서로 동작한다.

1. 구 묶음 리스트(`summary?groupBy=DISTRICT`)를 보여 준다.
2. 구를 고르면 `GET /house/complexes?lawdCd={구 코드}`로 그 구의 후보 리스트를 보여 준다.

## 5. 모듈 3 — 매물 비교

최대 3개 단지를 열 단위로 나란히 놓고 9개 지표를 비교한다. 단지는 지도·리스트·랭킹의 `[비교 담기]` 또는 비교 섹션의 단지 검색으로 추가한다.

### 동작

- 비교 트레이: 최대 3칸, 4번째 추가 시 "최대 3개까지 비교할 수 있어요" 토스트.
- 평형: 담을 때의 평형 토글 값으로 고정한다. 이후 상단 토글을 바꿔도 담긴 항목은 바뀌지 않는다. 트레이 항목마다 59/84 전환 버튼을 둔다.
- 같은 단지의 59와 84를 따로 담을 수 있고, 각각 한 칸씩 쓴다("같은 단지에서 평형을 키우면 얼마나 더 드나" 비교). 같은 단지·같은 평형은 중복 추가하지 않고 "이미 비교함에 있어요" 토스트를 띄운다. 전환 버튼으로 중복이 되는 경우도 같다.
- 단지 검색: 단지명 자동완성 (`GET /house/complexes/search`), 2글자 이상, 결과 10건. 검색으로 담으면 현재 평형 토글 값으로 담는다.
- 상태는 URL 쿼리 `cmp={complexId}:59,{complexId}:84`에 둔다 → 상단 `링크 복사`로 공유. URL은 `59`/`84`, API는 `B59`/`B84`이므로 변환은 `compare-param.ts`가 맡는다.
- 공유 링크의 단지가 삭제됐거나 없으면(응답 `missingIds`) 그 항목을 `cmp`에서 빼고 "찾을 수 없는 단지 N개를 비교에서 뺐어요" 토스트를 띄운다. 나머지 열은 그대로 보여 준다.
- 표 하단 안내: "온라인 비교는 손품 단계입니다. 현장 확인 후 매수 준비가 끝난 단계에서 중개사무소에 연락하세요."

### 비교 지표

| # | 행 이름 | 표시 예 | 산출 기준 | 출처 |
| --- | --- | --- | --- | --- |
| 1 | 최근 실거래가 | 4.5억 · 최근 3개월 5건 | 4장 최근 실거래가 규칙 (3개월 → 6개월 → 거래 없음) | 실거래 |
| 2 | 세대수 | 380세대 | 총괄표제부 세대수, 없으면 표제부 합 | 건축물대장 |
| 3 | 연식 | 1999년 · 27년차 | 사용승인연도(없으면 실거래 건축년도), 년차 = 올해 − 사용승인연도 | 건축물대장 / 실거래 |
| 4 | 평형 | 59.8㎡ 평균 · 59 구간 | 해당 구간 최근 12개월 거래의 전용면적 평균 + 구간명 | 실거래 |
| 5 | 용적률 | 365.74% · 총괄표제부 | 총괄표제부 우선, 없으면 표제부 | 건축물대장 |
| 6 | 교육·통학 참고 | 초 OO초 · 약 604m (초·중·고 각 1개) | 단지 좌표에서 가장 가까운 학교 직선거리 | 학교 위치 표준데이터 |
| 7 | 호재·정비사업 | 직접 연관 사업 없음 / OO재개발 · 관리처분 · 약 320m | 반경 500m 안 정비사업. 데이터 미적재(`null`)면 "정보 없음", 적재됐는데 없으면(`[]`) "직접 연관 사업 없음" | 서울시 정비사업 데이터 (13장) |
| 8 | 1년 실거래 변동 | 4.3억 → 4.5억 (+4.65%) | 1년 전 3개월 평균 → 최근 3개월 평균 | 실거래 |
| 9 | 3년 실거래 변동 | 4.0억 → 4.5억 (+12.50%) | 3년 전 3개월 평균 → 최근 3개월 평균 | 실거래 |

- 기간 정의(오늘 = 배치 실행일, 예: 2026-10-20):
  - 최근 3개월 = 2026-07-21 \~ 2026-10-20
  - 1년 전 3개월 = 2025-07-21 \~ 2025-10-20
  - 3년 전 3개월 = 2023-07-21 \~ 2023-10-20
- 변동률 = (최근 − 과거) ÷ 과거 × 100, 소수 셋째 자리에서 반올림. 등락가격 = 최근 − 과거 (만원).
- 6번 하단에 "직선거리 · 학교 배정 보장 아님" 고정 표기 (영상과 동일).
- 8·9번은 양쪽 구간의 표본 건수를 작은 글씨로 병기. 한쪽이라도 3건 미만이면 "낮은 표본" 배지, 2건 미만이면 변동률 대신 "표본 부족".
- 상승은 파랑 계열, 하락은 빨강 계열 (영상과 동일, 한국 금융 관례와 반대이므로 범례 표기).
- 값이 없는 칸은 "정보 없음".

## 6. 모듈 4 — 서울 공부 레퍼런스 (랭킹)

랭킹 4종은 야간 배치가 미리 계산해 `house_ranking`에 저장하고, API는 조회만 한다. 모든 랭킹은 평형 토글(59/84)을 공유하고, 하단에 집계 기간을 표기한다.

| 탭 | 대상 | 산출 기준 | 최소 표본 | 정렬·개수 |
| --- | --- | --- | --- | --- |
| 구별 대장 | 25개 구 각 1행 | 최근 12개월 해당 평형 평균 실거래가 1위 단지 | 3건 (미달 처리 아래) | 가격 내림차순, 항상 25행 |
| 1년 상승 TOP | 서울 전체 | 1년 실거래 변동률(5장 8번) > 0 | 양쪽 3건 | 상승률 내림차순, 최대 20행 |
| 1년 하락 TOP | 서울 전체 | 1년 실거래 변동률 < 0 | 양쪽 3건 | 하락률 큰 순, 최대 20행 |
| 거래 활발 TOP | 서울 전체 | 최근 3개월 거래 건수 (해제·원천 삭제 제외, 이상치는 포함) | — | 건수 내림차순, 20행 |

- **20개 미만**: 상승·하락 단지가 20개보다 적으면 있는 만큼만 보여 준다. 반대쪽 단지로 채우지 않는다.
- **동률**: 주 정렬값이 같으면 1년 변동률(높은 순, 하락 TOP은 낮은 순) → 1년 등락가격 절댓값(큰 순) → 단지명 가나다순으로 정한다. 변동률을 계산할 수 없는 단지는 해당 비교에서 뒤로 보낸다. 마지막 기준(단지명)은 순위를 결정적으로 만들기 위한 것이다.
- **구별 대장 표본 미달**: 구는 25개가 모두 보여야 "구별" 표가 빈 곳 없이 읽힌다. 그래서 행을 빼지 않고 단계적으로 완화한다.
  1. 최근 12개월 3건 이상인 단지 중 평균가 1위
  2. 없으면 1건 이상인 단지 중 1위 + "표본 적음" 배지
  3. 그래도 없으면 "최근 12개월 거래 없음" 행 (단지 없음, 지도 마커 없음)
- 구별 대장 기준은 영상 문구("최근 월 자치구별 평균 실거래 총액 1위")가 모호해서 위 기준으로 정했다.
- 행 구성: 순위, 구, 단지명, 연식 · 평형 · 세대수, 값(가격 또는 % 또는 건수) + 기간 라벨, `[지도에서 위치 보기]`, `[비교 담기]`.
- `[지도에서 위치 보기]` → 지도를 해당 단지로 이동하고 단지 레이어(level 5)로 확대, 정보 카드 오픈. 좌표가 없는 단지(보강 실패)와 "거래 없음" 행은 버튼을 비활성화하고 "위치 정보 없음"을 표시한다.
- 하락 TOP 상단 안내: "싸 보이는 단지가 저평가인지 저가치인지, 입지·연식·세대수 공통점을 비교해 보세요."
- 구별 대장은 지도에 쓰지 않는다. 지도 축소 상태는 구·동별 후보 수 묶음이다 (4장).

## 7. 데이터 소스와 수집 배치

모든 외부 데이터는 배치로 자체 DB에 적재하고, 사용자 요청은 DB만 조회한다. 저장할 수 없는 출처(카카오 로컬, 브이월드 등)는 쓰지 않는다 (`docs/field_research_map/02-legal-and-terms.md`).

### 데이터 소스

| 데이터 | 출처 (API/데이터셋) | 쓰는 필드 | 갱신 | 저장 |
| --- | --- | --- | --- | --- |
| 아파트 매매 실거래 | 공공데이터포털, 국토교통부 아파트 매매 실거래가 상세 자료 (`RTMSDataSvcAptTradeDev`), 요청 `LAWD_CD`(구 5자리) + `DEAL_YMD`(YYYYMM) | `aptSeq`(단지 일련번호), `aptNm`, `sggCd`, `umdCd`, `umdNm`, `bonbun`, `bubun`, `roadNm` 계열, `excluUseAr`, `dealAmount`, `dealYear/Month/Day`, `floor`, `aptDong`, `buildYear`, `cdealType`, `cdealDay`, `dealingGbn` | 매일 | 활용신청 시 이용허락범위 확인 |
| 건축물대장 | 공공데이터포털, 국토교통부 건축물대장정보 서비스 (총괄표제부, 표제부), 요청 = 시군구코드 + 법정동코드 + 번 + 지 | 세대수, 용적률, 건폐율, 사용승인일 | 신규 단지 시, 이후 월 1회 | 활용신청 시 확인 |
| 단지 좌표 | 행안부 도로명주소 검색 API + 좌표제공 API (기존 `AddressGateway`) | 출입구 좌표(UTM-K) → WGS84 | 신규 단지 시 | 저장 가능 (이용허락범위 제한 없음) |
| 학교 위치 | 공공데이터포털, 전국초중등학교위치표준데이터 | 학교ID, 학교명, 학교급, 위도·경도 | 반기 | 활용신청 시 확인 |
| 정비사업 | 서울시 정비사업 공개 데이터 (데이터셋 확정 필요, 13장) | 사업명, 유형, 진행단계, 위치 | 월 1회 | 확정 시 확인 |

- 필드명은 0단계에서 실거래 API를 한 번 호출해 실제 응답으로 확인한다. 특히 `aptSeq`가 모든 행에 채워지는지 확인한다(8장 설계의 전제). 거래금액은 쉼표 포함 문자열(예: "82,500")이므로 정수로 바꾼다.
- 공동주택 단지 목록·기본정보(K-apt)는 1차에서 쓰지 않는다. 의무관리 단지만 있어 소규모 단지가 빠지고, 이름 매칭이 필요해지기 때문이다. 세대수·사용승인일은 건축물대장으로 얻는다.
- 영상 화면의 출처 표기는 "Seoul Real Estate Portal"이다. 국토부 API로 부족하면 서울 부동산정보광장을 대체 소스로 검토한다 (1차는 국토부만).

### 야간 배치 (`HouseNightlyScheduler`)

별도 실행기나 Spring Batch를 두지 않는다. 기존 `@Scheduled` 하나(`cron = ${slcn.house.nightly-cron}`, `zone = "Asia/Seoul"`, 운영 기본 03:00)가 아래 단계를 순서대로 실행한다. 단계별 결과는 `house_batch_run`에 남기고, 한 단계가 실패해도 다음 단계는 진행한다(통계 단계는 이전 데이터로 다시 만든다).

| 단계 | 내용 | 외부 호출 |
| --- | --- | --- |
| 1. 수집 단위 준비 | 25개 구 × 최근 40개월(3년 전 3개월 창을 덮는 범위)의 (구, 계약년월) 행을 `house_collect_unit`에 보장 | — |
| 2. 실거래 수집 | 아래 우선순위로 단위를 골라 수집, 일일 호출 상한(`slcn.house.daily-call-limit`, 기본 1,000)에 닿으면 멈춤 | 공공데이터포털 |
| 3. 단지 보강 | 새 단지 또는 보강 실패(재시도 3회 미만) 단지의 좌표·건축물대장 조회. 매월 1일에는 전체 단지의 건축물대장 재조회 | 행안부, 공공데이터포털 |
| 4. 학교 동기화 | `SCHOOL_SYNC`의 마지막 SUCCESS가 없거나 180일 지났으면 학교 갱신 + 단지별 최근접 학교 재계산. 아니면 SKIPPED | 공공데이터포털 |
| 5. 정비사업 동기화 | `REDEVELOPMENT_SYNC`의 마지막 SUCCESS가 없거나 30일 지났으면 정비사업 갱신 + 단지별 반경 500m 연결 재계산. 데이터셋 미확정(13장)이면 SKIPPED | 서울시 |
| 6. 통계 | 이상치 표시 → 단지·평형 통계 → 랭킹 4종. 통계·랭킹 테이블은 한 트랜잭션에서 통째로 교체 | — |

**실거래 수집 우선순위** (한 실행 안에서 위에서부터):

1. 최근 3개월(이번 달 포함) 단위: 매일 다시 받는다. 신고 기한(계약 후 30일) 때문에 최근 월은 계속 늘어난다.
2. 이전 실행에서 실패한 단위
3. 아직 한 번도 받지 않은 단위(백필): 최근 월부터 과거로. 첫 배포 후 며칠에 걸쳐 자동으로 채워지므로 별도 백필 잡이나 수동 실행 API를 두지 않는다.
4. 매월 1일에만: 4\~12개월 전 단위 재수집 (늦게 들어온 해제 신고 반영)

- 전체 백필 규모: 25 × 40 = 1,000 단위 + 페이지. 상한 1,000이면 1\~2일 걸린다.
- 이 작업은 기본 스케줄러 스레드 하나에서 돈다. 04:30 업로드 정리 작업(`RawUploadCleanupScheduler`)보다 오래 걸리면 정리 작업이 그만큼 늦게 시작한다. 이 정도 지연은 허용한다.
- 운영 중 즉시 실행이 필요하면 cron 값을 바꿔 재시작한다. 수동 실행 API는 두지 않는다.
- `SLCN_DATA_GO_KR_SERVICE_KEY`가 비어 있으면 배치 전체를 건너뛰고 로그만 남긴다 (Gemini·행안부 키와 같은 패턴). 개발 환경 cron 기본값은 `-`(비활성)이다.

### 수집 규칙

1. **중복 제거**: 거래 고유 ID가 없으므로 `dedup_key` = SHA-256(`aptSeq`, `aptDong`, `floor`, `excluUseAr`, 계약일, `dealAmount`, 출현 순번)로 upsert한다. 출현 순번은 같은 응답 안에서 앞의 값이 모두 같은 행에 1부터 붙이는 번호다. 같은 날 같은 동·층·면적·가격 거래가 실제로 두 건일 때 하나로 합쳐지지 않게 한다.
2. **해제**: 해제 거래는 삭제하지 않고 `cancelled = true` + 해제일을 기록하고, 모든 통계에서 제외한다. 해제되면 `cdealType`만 바뀌고 키는 그대로라 같은 행이 갱신된다.
3. **원천 삭제**: 다시 받은 (구, 계약년월) 응답이 모든 페이지까지 성공했는데 DB에 있는 행이 응답에 없으면 `removed = true`로 표시하고 통계에서 제외한다. 신고 정정(금액 수정)은 "기존 행 removed + 새 행 추가"로 반영된다. 응답이 일부라도 실패하면 removed 판정을 하지 않는다.
4. **이상치**: 같은 단지·평형 구간에서 그 거래 계약일 앞뒤 6개월(총 12개월) 거래(해제·원천 삭제 제외, 자기 자신 포함)의 중앙값 대비 ±40% 밖이면 `outlier = true`.
   - 창 안 거래가 5건 미만이면 판정하지 않는다(`outlier = false`). 표본이 적으면 중앙값 자체를 믿기 어렵다.
   - 앞뒤 창을 쓰는 이유는 3년 동안 시세가 움직이기 때문이다. 과거 12개월만 보면 상승기의 정상 거래가 이상치로 찍힌다.
   - 직거래 저가 거래(가족 간 거래 등)는 대부분 여기서 걸러진다.
   - 통계 단계마다 수집 범위(최근 40개월) 전체를 다시 판정한다. 범위가 정해져 있어 건수는 늘지 않는다(서울 기준 약 20만 행).
5. **호출 실패**: 지수 백오프(1초·2초·4초) 3회 재시도. 그래도 실패하면 단위를 `FAILED`로 두고 다음 실행에서 우선순위 2로 다시 받는다.
6. **로그**: 서비스 키가 URL에 들어가므로 예외 메시지·cause를 로그에 남기지 않는다(`JusoAddressGateway`와 같은 규칙). 단계별 호출 수는 `house_batch_run`에 남긴다.

## 8. 단지 식별과 보강 규칙

### 단지 식별

- 단지 마스터(`house_complex`)는 실거래의 `aptSeq`로 만든다. 실거래에 처음 나온 `aptSeq`면 단지를 새로 만들고, 거래는 항상 단지에 연결된다(미매칭 거래 없음).
- 0단계 확인에서 `aptSeq`가 비어 있는 행이 있으면, 그 행은 (`sggCd`, `umdCd`, `bonbun`, `bubun`, 정규화 단지명)으로 만든 대체 키로 단지를 찾는다. 정규화 = 공백 제거, 괄호 내용 제거, "아파트" 제거, 영문 소문자화.
- 단지명·지번 등 표시 정보는 그 단지의 가장 최근 거래 값으로 갱신한다.

### 좌표 (행안부)

1. 실거래의 도로명 필드(`roadNm` + 건물 본번·부번)로 도로명주소 검색. 결과가 없으면 지번주소("서울특별시 {구} {umdNm} {bonbun}-{bubun}")로 검색.
2. 첫 후보의 좌표 조회 키(`CoordKey`)로 좌표제공 API를 불러 출입구 좌표(UTM-K)를 받고, 기존 `UtmkToWgs84Converter`로 위경도로 바꾼다.
3. 단지당 좌표 1개(대표 출입구). 실패하면 `geocode_status = FAILED`로 두고 지도·학교 거리에서 제외한다. 관리자 보완 목록(10장)에 나온다.
4. 관리자가 주소를 보정(`address_override`)하면 다음 야간 배치에서 그 주소로 다시 조회한다.

### 건축물대장

- 실거래의 (`sggCd`, `umdCd`, `bonbun`, `bubun`)으로 총괄표제부를 먼저 조회하고, 없으면 표제부를 조회한다.
- 세대수: 총괄표제부 세대수. 없으면 표제부 중 주용도가 공동주택인 동의 세대수 합.
- 용적률·건폐율: 총괄표제부 우선, 없으면 표제부(동이 여럿이면 연면적이 가장 큰 동). 출처를 `far_source`(RECAP | TITLE)에 남긴다.
- 사용승인일: 위와 같은 우선순위. 없으면 실거래 `buildYear`로 연식만 표시한다.
- 조회 결과가 없으면 `register_status = FAILED`, 해당 칸은 "정보 없음"이다. 대표 지번과 대장 지번이 다른 단지는 관리자가 지번을 보정한다.

### 면적 구간

| 구간 | 전용면적 범위 | 비고 |
| --- | --- | --- |
| `B59` | 55.00 ≤ x < 65.00 ㎡ | 화면 표기 59㎡ |
| `B84` | 80.00 ≤ x < 90.00 ㎡ | 화면 표기 84㎡ |
| `OTHER` | 그 외 | 저장만, 1차 화면 미노출 |

- 구간 경계는 설정값이다. 거래 행에는 전용면적 원값과 구간을 함께 저장한다.
- 한 단지에 같은 구간 타입이 여럿이면(59A, 59B) 구간 단위로 합산하고, 비교표에는 거래 전용면적 평균을 표기한다.

## 9. 영속성 모델

기존 PostgreSQL `slcn` 스키마에 `house_` 접두어 테이블로 추가한다. 테이블은 `ddl-auto: update`가 JPO에서 만든다. 손으로 쓴 DDL이나 마이그레이션 도구는 쓰지 않는다. 그래서 부분 인덱스·체크 제약처럼 JPA가 만들지 못하는 것은 쓰지 않는다.

- 원본 테이블(단지, 거래, 정책, 수집 단위)은 기존 `DomainEntityJpo`(String `id`, `@Version`, `registeredTime`/`modifiedTime`)를 상속한다.
- 파생 테이블(통계, 랭킹, 단지-학교, 단지-정비사업)은 배치가 통째로 다시 만드는 데이터라 복합 키 단순 엔티티로 둔다.
- JSON 컬럼 값 객체는 `equals`/`hashCode`를 구현한다. 없으면 쓰기마다 UPDATE가 두 번 나가고 `@Version`이 2씩 오른다.
- 정합성이 걸린 곳은 DB 유니크 제약으로 막는다: `dedup_key`, `apt_seq`, 정책 `effective_from`, 수집 단위 (`lawd_cd`, `deal_ym`).

| 테이블 (JPO) | 주요 컬럼 | 제약·인덱스 |
| --- | --- | --- |
| `house_complex` (`HouseComplexJpo`) | `apt_seq`, `lawd_cd`(5), `umd_cd`(5), `umd_name`, `bonbun`, `bubun`, `apt_name`, `road_address`, `address_override`, `latitude`, `longitude`, `geocode_status`(PENDING\|DONE\|FAILED), `geocode_attempts`, `build_year`, `approval_date`, `households`, `far_pct`, `far_source`(RECAP\|TITLE), `bcr_pct`, `register_status`(PENDING\|DONE\|FAILED), `register_attempts` | UK `apt_seq`, IDX (`latitude`, `longitude`), IDX `apt_name`, IDX (`lawd_cd`, `umd_cd`) (구·동 묶음 집계) |
| `house_trade` (`HouseTradeJpo`) | `dedup_key`(64), `complex_id`, `lawd_cd`, `deal_ym`(YYYYMM), `deal_date`, `exclusive_area`, `area_bucket`(B59\|B84\|OTHER), `amount_manwon`, `floor`, `apt_dong`, `build_year`, `dealing_type`(BROKER\|DIRECT\|UNKNOWN), `cancelled`, `cancelled_date`, `outlier`, `removed` | UK `dedup_key`, IDX (`complex_id`, `area_bucket`, `deal_date`), IDX (`lawd_cd`, `deal_ym`) |
| `house_collect_unit` (`HouseCollectUnitJpo`) | `lawd_cd`, `deal_ym`, `status`(NEVER\|DONE\|FAILED), `last_success_date`, `attempts` | UK (`lawd_cd`, `deal_ym`) |
| `house_complex_area_stat` | PK (`complex_id`, `area_bucket`), `avg_area_12m`, `recent_price_manwon`, `recent_window_months`(3\|6\|null), `recent_sample`, `avg_3m`/`cnt_3m`, `avg_3m_1y_ago`/`cnt_3m_1y_ago`, `avg_3m_3y_ago`/`cnt_3m_3y_ago`, `avg_12m`/`cnt_12m`, `change_1y_pct`, `change_1y_manwon`, `change_3y_pct`, `change_3y_manwon`, `volume_3m`, `computed_date` | — |
| `house_ranking` | PK (`ranking_type`, `area_bucket`, `rank_no`), `lawd_cd`, `complex_id`(구별 대장 "거래 없음" 행은 null), `value_num`, `change_manwon`, `sample_count`, `badge`(LOW_SAMPLE\|NO_TRADE\|null), `period_from`, `period_to`, `computed_date` | — |
| `house_school` | `school_code`, `name`, `level`(E\|M\|H), `latitude`, `longitude`, `synced_date` | UK `school_code` |
| `house_complex_school` | PK (`complex_id`, `level`), `school_id`, `distance_m` | — |
| `house_redevelopment` | `source_key`, `name`, `project_type`, `stage`, `latitude`, `longitude`, `source_url`, `synced_date` | UK `source_key` |
| `house_complex_redevelopment` | PK (`complex_id`, `redevelopment_id`), `distance_m` | — |
| `house_budget_policy` (`HouseBudgetPolicyJpo`) | `effective_from`, `dsr_ratio`, `stress_rate_pp`, `term_years`, `default_reserve_manwon`, `ltv_first_time`, `ltv_non_first`, `price_caps`(JSON), `cost_rates`(JSON), `source_note` | UK `effective_from` |
| `house_batch_run` | `run_date`, `step`(아래 목록), `status`(SUCCESS\|PARTIAL\|FAILED\|SKIPPED), `api_calls`, `detail`(JSON), `started_time`, `finished_time`, `error` | IDX (`step`, `status`, `run_date`) |

- `house_batch_run.step` 값 (7장 야간 배치 단계와 1:1, 한 실행에서 단계마다 1행):

  | step | 7장 단계 | 이 기록을 읽는 곳 |
  | --- | --- | --- |
  | `COLLECT_UNIT_PREPARE` | 1. 수집 단위 준비 | — |
  | `TRADE_COLLECT` | 2. 실거래 수집 | `/house/meta`의 `dataAsOf` |
  | `COMPLEX_ENRICH` | 3. 단지 보강 | — |
  | `SCHOOL_SYNC` | 4. 학교 동기화 | 다음 갱신 시점(180일) 판단 |
  | `REDEVELOPMENT_SYNC` | 5. 정비사업 동기화 | 다음 갱신 시점(30일) 판단, 비교표 `redevelopments` null/[] 판단 |
  | `STATS_BUILD` | 6. 통계 | `/house/meta`의 `statsComputedDate` |

  SKIPPED(갱신 시점 전, 키 없음, 데이터셋 미확정)는 SUCCESS로 치지 않는다. 그래서 정비사업을 한 번도 받지 않았으면 계속 `null`이다.
- 지도 영역 조회는 위경도 B-tree 인덱스로 충분하다(서울 아파트 단지 수천 개). PostGIS는 쓰지 않는다.
- `house_complex_area_stat.avg_3m`은 최근 실거래가가 3개월 창일 때 `recent_price_manwon`과 같은 값이다. 비교표 1행과 8행 "최근"이 서로 다르게 보이지 않는다.
- 쿼리는 단위 테스트로 검증되지 않는다(DB 통합 테스트 없음). 통계·랭킹 SQL은 로컬 DB에 백필 데이터를 넣고 직접 실행해 결과를 확인한다.

**정책 시드 (배포 시 1회 수동 실행)**

```sql
INSERT INTO slcn.house_budget_policy
  (id, entity_version, registered_time, modified_time, effective_from,
   dsr_ratio, stress_rate_pp, term_years, default_reserve_manwon, ltv_first_time, ltv_non_first,
   price_caps, cost_rates, source_note)
VALUES
  ('<생성 ID>', 0, <epoch ms>, <epoch ms>, '2025-10-16',
   0.400, 3.00, 30, 2000, 0.700, 0.400,
   '[{"maxPriceManwon":150000,"capManwon":60000},
     {"maxPriceManwon":250000,"capManwon":40000},
     {"maxPriceManwon":null,"capManwon":20000}]',
   '{"acquisitionTax":{"lowRatePct":1.0,"lowMaxManwon":60000,"highRatePct":3.0,"highMinManwon":90000,"localEducationRatio":0.1},
     "firstTimeRelief":{"capManwon":200,"maxPriceManwon":120000},
     "brokerage":{"tiers":[{"belowManwon":5000,"ratePct":0.6,"capManwon":25},
                           {"belowManwon":20000,"ratePct":0.5,"capManwon":80},
                           {"belowManwon":90000,"ratePct":0.4,"capManwon":null},
                           {"belowManwon":120000,"ratePct":0.5,"capManwon":null},
                           {"belowManwon":150000,"ratePct":0.6,"capManwon":null},
                           {"belowManwon":null,"ratePct":0.7,"capManwon":null}],
                  "vatRatio":0.1},
     "registrationRatePct":0.2}',
   '10·15 대책(2025-10-16 시행), 지방세법, 서울시 중개보수 조례');
```

컬럼명은 JPO 필드명이 확정된 뒤 실제 테이블과 맞춘다. 넣기 전에 같은 `effective_from` 행이 없는지 조회해 확인한다.

## 10. API 명세

기존 BE 규칙을 따른다.

- 경로에 `/api` 접두어가 없다(FE는 `VITE_API_URL=/api` 아래로 호출하고 프록시가 넘긴다). 모든 경로는 `/house` 아래에 둔다.
- 인증: 모든 경로는 기존 `anyRequest().hasAuthority(USER_AUTHORITY)` 규칙으로 로그인 회원만 허용한다. **`permitAll` 경로를 추가하지 않는다.** 관리자 경로(`/house/admin/**`)는 `SecurityConfiguration`에 `hasAuthority(ADMIN_AUTHORITY)` 매처를 추가한다.
- 응답: `ResponseEntity<*Rdo>` 본문을 그대로 낸다(감싸는 객체 없음). 목록은 `{ "items": [...] }` 형태다.
- 오류: `ErrorResponse { title, status, code, errors }`. FE는 `code`(ErrorCode 이름)로 분기한다(FE에서는 이 값이 `AppError.apiCode`에 들어간다, 11장). 입력 검증은 `VALIDATION_FAILED` + `errors[]`(field, code, message)를 쓴다.
- 금액은 만원 정수, 면적 구간은 `B59`/`B84`, 날짜는 `yyyy-MM-dd` 문자열.
- 응답의 구 이름(`districtName`)은 BE가 `lawdCd`로 채운다(25개 구 상수).

### 엔드포인트

| 메서드 | 경로 | 용도 | 파라미터 |
| --- | --- | --- | --- |
| GET | `/house/budget-policy` | 예산 계산 규칙 (모듈 1) | — |
| GET | `/house/meta` | 데이터 기준일, 통계 계산일 | — |
| GET | `/house/complexes` | 지도 영역 단지 (모듈 2) | (`swLat`, `swLng`, `neLat`, `neLng`) 또는 `lawdCd` 중 하나, `area`, `minPrice`?, `maxPrice`? |
| GET | `/house/complexes/summary` | 구·동 묶음 후보 수 (모듈 2) | `groupBy=DISTRICT\|DONG`, `area`, `minPrice`?, `maxPrice`?, 영역 좌표(DONG만 필수. DISTRICT는 받지 않음) |
| GET | `/house/complexes/search` | 단지명 자동완성 | `keyword`(2자 이상), `size`(기본 10, 최대 20) |
| GET | `/house/complexes/{complexId}` | 정보 카드 | `area` |
| GET | `/house/compare` | 비교표 (모듈 3) | `items={complexId}:B59,{complexId}:B84` (1\~3개) |
| GET | `/house/rankings` | 랭킹 (모듈 4) | `type=LEADER\|RISE\|FALL\|VOLUME`, `area` |
| GET | `/house/admin/complexes/incomplete` | 좌표·건축물대장 보강 실패 단지 | `page`(1부터), `size`(기본 20) |
| PUT | `/house/admin/complexes/{complexId}/address-override` | 주소·지번 보정 (다음 배치에서 재조회) | 본문 `{ "roadAddress"?, "bonbun"?, "bubun"? }` |

### 오류 코드 (ErrorCode에 추가)

| 코드 | HTTP | 언제 |
| --- | --- | --- |
| `VALIDATION_FAILED` (기존) | 400 | `area` 누락·오류, 영역 좌표 누락·뒤집힘, `/house/complexes`에서 영역과 `lawdCd`가 둘 다 없거나 둘 다 있음, `/house/complexes` 영역 한 변이 0.2도(경도 기준 약 17.6km) 초과, `summary` DONG에서 영역 누락 또는 한 변이 1.0도 초과, `summary` DISTRICT에 영역 좌표가 있음, `minPrice > maxPrice`, `keyword` 2자 미만, `items` 0개·4개 이상·형식 오류·중복 |
| `HOUSE_COMPLEX_NOT_FOUND` | 404 | 정보 카드의 `complexId`가 없음. 비교표는 404를 내지 않고 `missingIds`로 알린다 |
| `HOUSE_BUDGET_POLICY_NOT_FOUND` | 503 | 오늘 시행 중인 정책 행이 없음 (시드 누락) |

### 예산 계산 규칙

```json
// GET /house/budget-policy
{
  "policyId": "…",
  "effectiveFrom": "2025-10-16",
  "dsrRatio": 0.40,
  "stressRatePp": 3.0,
  "termYears": 30,
  "defaultReserveManwon": 2000,
  "ltv": { "FIRST_TIME": 0.70, "NON_FIRST": 0.40 },
  "priceCaps": [
    { "maxPriceManwon": 150000, "capManwon": 60000 },
    { "maxPriceManwon": 250000, "capManwon": 40000 },
    { "maxPriceManwon": null,   "capManwon": 20000 }
  ],
  "costRates": {
    "acquisitionTax": {
      "lowRatePct": 1.0, "lowMaxManwon": 60000,
      "highRatePct": 3.0, "highMinManwon": 90000,
      "localEducationRatio": 0.1
    },
    "firstTimeRelief": { "capManwon": 200, "maxPriceManwon": 120000 },
    "brokerage": {
      "tiers": [
        { "belowManwon": 5000,   "ratePct": 0.6, "capManwon": 25 },
        { "belowManwon": 20000,  "ratePct": 0.5, "capManwon": 80 },
        { "belowManwon": 90000,  "ratePct": 0.4, "capManwon": null },
        { "belowManwon": 120000, "ratePct": 0.5, "capManwon": null },
        { "belowManwon": 150000, "ratePct": 0.6, "capManwon": null },
        { "belowManwon": null,   "ratePct": 0.7, "capManwon": null }
      ],
      "vatRatio": 0.1
    },
    "registrationRatePct": 0.2
  },
  "sourceNote": "10·15 대책(2025-10-16 시행), 지방세법, 서울시 중개보수 조례"
}
```

- `priceCaps`는 `maxPriceManwon` 오름차순이고 마지막 항목만 `null`(상한 없음)이다. 구간은 (이전 max, max]이다.
- `brokerage.tiers`는 `belowManwon` 오름차순이고 마지막 항목만 `null`이다. 구간은 [이전 below, below)이다(15억 정확히는 0.7%).
- FE zod 스키마는 위 구조를 그대로 검증하고, 정렬·마지막 `null` 규칙이 깨지면 `INVALID_RESPONSE`로 처리해 예산 섹션 오류 상태로 보낸다.

`effective_from <= 오늘`인 행 중 가장 최근 행 하나를 내려준다.

### 메타

```json
// GET /house/meta
{ "dataAsOf": "2026-10-20", "statsComputedDate": "2026-10-20" }
```

`dataAsOf` = 최근 3개월 수집 단위가 모두 성공한 마지막 날짜.

### 지도 영역 단지

```json
// GET /house/complexes?swLat=37.540&swLng=126.980&neLat=37.620&neLng=127.100&area=B59&minPrice=45872&maxPrice=57340
{
  "total": 34,
  "truncated": false,
  "items": [
    { "complexId": "…", "name": "OO아파트", "lawdCd": "11230", "districtName": "동대문구", "umdName": "OO동",
      "latitude": 37.5789, "longitude": 127.0451,
      "recentPrice": { "priceManwon": 45000, "windowMonths": 3, "sample": 5 },
      "buildYear": 1999, "households": 380 }
  ]
}
```

- 좌표가 없는 단지(`geocode_status ≠ DONE`)는 빠진다.
- 가격 범위가 있으면 최근 실거래가가 있는 단지만 대상이다. 없으면 가격이 없는 단지도 `recentPrice: null`로 포함한다.
- 최대 300건, 최근 실거래가 내림차순(가격 없는 단지는 뒤). 넘치면 `truncated: true`, `total`은 전체 수다.
- `minPrice`·`maxPrice`는 양 끝 포함(`minPrice ≤ 최근 실거래가 ≤ maxPrice`)이다.
- `lawdCd`로 부르면 영역 대신 그 구 전체가 대상이다(지도 없는 모드).

### 구·동 묶음

```json
// GET /house/complexes/summary?groupBy=DONG&area=B59&minPrice=45872&maxPrice=57340&swLat=37.540&swLng=126.980&neLat=37.620&neLng=127.100
{
  "groupBy": "DONG",
  "items": [
    { "code": "1130510100", "name": "미아동", "lawdCd": "11305", "districtName": "강북구",
      "count": 12, "centerLat": 37.6131, "centerLng": 127.0298 }
  ]
}
```

- `code`: DISTRICT는 `lawdCd`(5자리), DONG은 `lawdCd` + `umdCd`(10자리). DONG의 `name`은 `umdName`이다.
- `count`: 좌표가 있는 단지 중 조건에 맞는 수. 가격 범위가 있으면 최근 실거래가가 범위 안인 단지만, 없으면 전부.
- `centerLat`·`centerLng`: 필터와 무관하게 정해져 있어 필터를 바꿔도 마커 위치가 흔들리지 않는다.
  - DISTRICT: `HouseConstant`의 구별 고정 중심 좌표 25개. 좌표 보강 초기처럼 어떤 구에 좌표 있는 단지가 없어도 항상 값이 있다.
  - DONG: 그 동의 좌표 있는 **전체** 단지 좌표 평균.
- `count`가 0인 묶음도 내려준다. DONG은 좌표 있는 단지가 하나라도 있는 동만, 중심이 영역 안에 있는 것만 내려준다.
- DISTRICT는 영역 파라미터를 받지 않고 항상 서울 전체 25행을 내려준다(`count`도 구 전체 기준). 가장 위 단계라 더 올릴 묶음이 없으므로, 지도를 아무리 축소해도 영역 검증에 걸리지 않게 하기 위해서다. 화면 밖 구 마커는 카카오맵이 알아서 그리지 않는다.

### 단지 검색

```json
// GET /house/complexes/search?keyword=래미안&size=10
{ "items": [ { "complexId": "…", "name": "래미안OO", "districtName": "성동구", "umdName": "OO동", "buildYear": 2014 } ] }
```

공백을 뺀 단지명 부분 일치, 단지명 가나다순.

### 정보 카드

```json
// GET /house/complexes/{complexId}?area=B59
{
  "complexId": "…", "name": "OO아파트", "districtName": "동대문구", "umdName": "OO동",
  "latitude": 37.5789, "longitude": 127.0451,
  "buildYear": 1999, "households": 380,
  "recentPrice": { "priceManwon": 45000, "windowMonths": 3, "sample": 5 }
}
```

### 비교표

```json
// GET /house/compare?items=c1:B59,c2:B84,c9:B59
{
  "missingIds": ["c9"],
  "columns": [
    { "complexId": "c1", "name": "OO아파트", "districtName": "동대문구", "umdName": "OO동", "area": "B59",
      "recentPrice":  { "priceManwon": 45000, "windowMonths": 3, "sample": 5 },
      "households":   380,
      "buildYear":    1999, "ageYears": 27,
      "areaInfo":     { "avgExclusiveArea": 59.8, "bucket": "B59" },
      "far":          { "pct": 365.74, "source": "RECAP" },
      "schools":      [ { "level": "E", "name": "OO초등학교", "distanceM": 604 } ],
      "redevelopments": [
        { "name": "OO재정비촉진구역", "projectType": "재개발", "stage": "관리처분인가",
          "distanceM": 320, "sourceUrl": "https://…" }
      ],
      "change1y":     { "fromManwon": 43000, "toManwon": 45000, "pct": 4.65, "changeManwon": 2000,
                        "sampleFrom": 3, "sampleTo": 5, "sampleStatus": "OK" },
      "change3y":     { "fromManwon": 40000, "toManwon": 45000, "pct": 12.50, "changeManwon": 5000,
                        "sampleFrom": 2, "sampleTo": 5, "sampleStatus": "LOW" }
    }
  ]
}
```

- `sampleStatus`: 양쪽 모두 3건 이상 `OK`, 한쪽이라도 3건 미만 `LOW`, 한쪽이라도 2건 미만 `INSUFFICIENT`(`pct`, `changeManwon`, `fromManwon`·`toManwon` 중 없는 쪽은 null).
- `recentPrice.windowMonths`가 6이면 최근 3개월 거래가 없다는 뜻이므로 `change1y`·`change3y`는 `INSUFFICIENT`다.
- 열 순서는 요청 `items` 순서를 따른다(없는 단지는 건너뜀). 값이 없는 필드는 null이고, FE가 "정보 없음"으로 표시한다.
- `missingIds`: 요청했지만 없는 `complexId` 목록. 없으면 `[]`. 전부 없으면 `columns: []`. 비교표는 이 경우에도 200이다.
- `redevelopments`: 정비사업 데이터가 아직 적재되지 않았으면 `null`, 적재됐는데 반경 500m 안에 없으면 `[]`.
  - "적재됨"은 `house_batch_run`에 `step = REDEVELOPMENT_SYNC`, `status = SUCCESS` 기록이 한 번이라도 있는지로 판단한다(9장 step 목록). 테이블이 비었는지로 판단하지 않는다. 그러면 동기화는 성공했는데 사업이 0건인 경우와 미적재를 구분할 수 없다.
- `complexId`에는 `:`와 `,`가 들어가지 않는다(`items`·`cmp` 구분자).

### 랭킹

```json
// GET /house/rankings?type=RISE&area=B84
{
  "type": "RISE", "area": "B84",
  "period": { "from": "2025-07-21", "to": "2026-10-20" },
  "items": [
    { "rank": 1, "complexId": "…", "name": "OO아파트", "lawdCd": "11680", "districtName": "강남구",
      "latitude": 37.49, "longitude": 127.06,
      "buildYear": 2014, "avgExclusiveArea": 84.9, "households": 1466,
      "value": 12.40, "changeManwon": 26000, "sample": 6, "badge": null }
  ]
}
```

- `value`의 의미: LEADER = 최근 12개월 평균가(만원), RISE/FALL = 1년 변동률(%), VOLUME = 최근 3개월 거래 건수.
- `period`: LEADER는 최근 12개월, RISE/FALL은 1년 전 3개월 시작일 \~ 오늘, VOLUME은 최근 3개월.
- LEADER의 "거래 없음" 구는 `complexId`·`name`·`value`·좌표·단지 정보가 null이고 `badge: "NO_TRADE"`다.
- 좌표 보강에 실패한 단지도 랭킹에는 나올 수 있다. 이때 `latitude`·`longitude`는 null이다.

### 관리자

```json
// GET /house/admin/complexes/incomplete?page=1&size=20
{
  "totalCount": 12,
  "items": [
    { "complexId": "…", "aptSeq": "11230-123", "name": "OO", "districtName": "동대문구", "umdName": "OO동",
      "bonbun": "123", "bubun": "4", "roadAddress": null, "addressOverride": null,
      "geocodeStatus": "FAILED", "registerStatus": "DONE", "trades12m": 7 }
  ]
}
```

최근 12개월 거래 건수 내림차순(화면에 자주 나올 단지부터 고친다).

## 11. FE · BE 구조

### BE (`slcnapp` 멀티모듈)

기존 `inspection` 도메인과 같은 배치를 따른다. 도메인 이름은 `house`다.

```text
slcn-spec/…/spec/house/
  constant/   HouseConstant (구 코드↔이름, 구별 고정 중심 좌표, 면적 구간 경계 기본값)
  entity/     HouseComplex, HouseTrade, HouseBudgetPolicy …  (JPA 애너테이션 없음)
  entity/vo/  AreaBucket, RankingType, SampleStatus, PriceCap, CostRates …
  facade/     HouseBudgetPolicyFacade, HouseComplexFacade, HouseRankingFacade, HouseMetaFacade, HouseAdminFacade
  facade/sdo/ BudgetPolicyRdo, ComplexMapListRdo, ComplexSummaryRdo, ComplexSearchRdo, ComplexCardRdo, CompareRdo,
              RankingListRdo, HouseMetaRdo, IncompleteComplexListRdo, AddressOverrideUdo …

slcn-aggregate/…/aggregate/
  house/exception/  HouseComplexNotFoundException, HouseBudgetPolicyNotFoundException
  house/collect/    TradeGateway, BuildingRegisterGateway, SchoolGateway (포트),
                    Disabled*Gateway (키 없을 때), TradeCollector, ComplexEnricher, DedupKeyFactory
  house/stat/       OutlierMarker, AreaStatBuilder, RankingBuilder
  house/logic/      HouseComplexLogic, HouseBudgetPolicyLogic, HouseRankingLogic, HouseNightlyScheduler
  house/store/      *Store, store/jpo/*Jpo, store/repository/*Repository, store/mapper/*JpoMapper
  flow/house/       HouseQueryFlow, HouseCompareQueryFlow, HouseNightlyFlow, HouseAdminFlow
  external/datagokr/ MolitTradeGateway, BuildingRegisterGatewayImpl, SchoolStandardDataGateway
  config/           DataGoKrConfiguration (RestClient + 타임아웃, 키 없으면 Disabled 구현)

slcn-rest/…/rest/house/
  HouseBudgetPolicyResource, HouseComplexResource, HouseRankingResource, HouseMetaResource, HouseAdminResource

slcn-boot/
  SecurityConfiguration: /house/admin/** → ADMIN_AUTHORITY
  application.yml: slcn.house.* (아래)
```

- Resource는 얇게 두고 Flow에 위임한다. 외부 호출 동안 트랜잭션을 잡지 않는다(`GeoQueryFlow`와 같은 규칙).
- 좌표는 기존 `AddressGateway`와 `UtmkToWgs84Converter`(`aggregate/inspection/geo`)를 그대로 주입받아 쓴다. 두 도메인이 같이 쓰게 되므로, 구현 중 공용 패키지로 옮길지는 BE Agent가 판단한다.
- 외부 호출은 `RestClient` + `SimpleClientHttpRequestFactory` 타임아웃(기존 `JusoConfiguration` 패턴). 타임아웃 1 미만은 시작 시 오류로 막는다.
- 통계 계산은 SQL 집계(윈도우 함수) 우선이다. 이상치 중앙값처럼 SQL로 어려운 것은 단지·구간 그룹 단위로 Java에서 계산해도 된다(범위가 최근 40개월로 정해져 있음).
- 조회 캐시는 두지 않는다. 2인 서비스라 DB 직접 조회로 충분하다. 실제 병목이 확인되면 그때 추가한다.

**설정 (application.yml / 환경변수)**

| 키 | 환경변수 | 기본 | 설명 |
| --- | --- | --- | --- |
| `slcn.house.data-go-kr.service-key` | `SLCN_DATA_GO_KR_SERVICE_KEY` | (빈 값) | 비면 배치 전체 SKIP |
| `slcn.house.data-go-kr.base-url` | `SLCN_DATA_GO_KR_BASE_URL` | `https://apis.data.go.kr` | |
| `slcn.house.data-go-kr.timeout-seconds` | `SLCN_DATA_GO_KR_TIMEOUT_SECONDS` | 10 | |
| `slcn.house.nightly-cron` | `SLCN_HOUSE_NIGHTLY_CRON` | `-` (운영 `0 0 3 * * *`) | |
| `slcn.house.daily-call-limit` | `SLCN_HOUSE_DAILY_CALL_LIMIT` | 1000 | 공공데이터포털 호출 상한 |

행안부 키(`SLCN_JUSO_SEARCH_KEY`, `SLCN_JUSO_COORD_KEY`)는 기존 값을 그대로 쓴다. 키를 추가하면 AGENTS.md의 환경변수 목록도 갱신한다.

**테스트 (BE)**

- `DedupKeyFactory`: 출현 순번, 해제 시 같은 키 유지
- `OutlierMarker`: 5건 미만 미판정, ±40% 경계, 앞뒤 6개월 창
- `AreaStatBuilder`/`RankingBuilder`: 3개월 → 6개월 → 없음 대체, 표본 배지, 동률 정렬, 구별 대장 단계적 완화, 20개 미만
- `MolitTradeGateway`: 쉼표 금액 파싱, 오류 응답, 키가 로그에 남지 않음
- Resource: 오류 코드, `items` 검증, 관리자 권한, `/house/complexes` 영역·`lawdCd` 택일 검증
- 비교표: 없는 단지는 `missingIds`로 빠지고 200, `redevelopments` null/[] 구분(동기화 SUCCESS 기록 기준)
- 구·동 묶음: 0건 묶음 포함, DISTRICT 항상 25행·영역 파라미터 거부, DONG 영역 필수, 중심 좌표가 필터와 무관
- 통계 SQL은 로컬 DB에서 직접 실행해 확인한다(9장)

### FE (`slcnfront`)

기존 `src/domains/*` 구조와 페이지 분리(main/mobile)를 따른다.

```text
src/domains/house/
  api/        house-api.ts, house-schemas.ts (zod 응답 검증)
  components/ BudgetSection, BudgetForm, BudgetResult, MapSection, SummaryLayer, ComplexLayer,
              PriceOverlay, AreaToggle, CandidateList, ComplexInfoCard, CompareSection, ComplexPicker,
              CompareTable, RankingSection, RankingTabs, RankingList, Disclaimer, DataAsOf
  hooks/      useBudgetPolicy, useBudgetInput, useComplexesInBounds, useComplexSummary, useCompareTray,
              useHouseUrlState, useRankings
  mappers/    house-mappers.ts
  utils/      calculate-budget.ts, budget-cost.ts, format-eok.ts, compare-param.ts, map-bounds.ts
  types.ts
src/lib/kakao/            kakao-loader.ts, kakao-maps.d.ts (새로 만든다. 임장 지도 FC4b도 나중에 이 로더를 쓴다)
src/lib/env/env.ts        VITE_KAKAO_MAP_JS_KEY 추가
src/lib/routing/route-builders.ts  buildDeviceHousePath 추가
src/pages/main/HouseFinderPage.tsx, src/pages/mobile/HouseFinderPage.tsx
src/pages/shared/HomeHubPage.tsx  진입 카드 추가
src/lib/api/query-keys.ts   houseQueryKeys 추가
```

- **라우트**: `/main/house`, `/mobile/house`. `route-constants.ts`의 `MAIN_/MOBILE_ROUTE_PATTERNS`와 `route-manifest.tsx`에 추가하고, `route-builders.ts`에 `buildDeviceHousePath(device)`를 추가한다. `RESERVED_SHOE_BRAND_SEGMENTS`에 `'house'`를 넣는다(신발 브랜드 경로와 충돌 방지). 홈 허브(`src/pages/shared/HomeHubPage.tsx`)에 진입 카드를 추가한다.
- **인증**: 기존 라우터 인증 가드 아래에 둔다. 비로그인으로 공유 링크를 열면 로그인 화면으로 이동하고(`redirect` 쿼리), 로그인 후 같은 링크로 돌아온다. 가드는 `redirect`에 pathname·search·hash를 모두 담으므로(`guards.tsx`) `cmp`도 유지된다. BE도 401을 돌려주므로 비회원은 데이터를 받을 수 없다.
- **URL 상태** (`useHouseUrlState`, react-router `useSearchParams`): `area`(59\|84), `cmp`(`{complexId}:59,…`), `lat`, `lng`, `level`, `rank`(LEADER\|RISE\|FALL\|VOLUME). 새로고침·링크 공유 시 그대로 복원된다.
  - 모든 갱신은 `setSearchParams(next, { replace: true })`로 한다. 지도 `idle`마다 기록이 쌓이지 않고, 뒤로가기는 이 페이지를 떠난다.
  - `lat`·`lng`는 소수 다섯째 자리까지만 쓴다. 잘못된 값은 무시하고 기본값(4장 초기 지도)을 쓴다.
- **섹션 앵커**: `#budget`(내 예산), `#find`(집 찾기), `#compare`(매물 비교), `#study`(공부 레퍼런스). 상단 네비는 `scrollIntoView`로 이동하고, 해시는 URL에 남기지 않는다.
- **예산 상태** (`useBudgetInput`): zustand 스토어 + `persist`(sessionStorage, 키 `house-budget-input`). URL과 서버로 보내지 않는다. "입력값 지우기" 버튼을 둔다.
  - 입력값은 **입력한 문자열 그대로** 저장하고 계산할 때 파싱한다. "5."처럼 입력 중인 값과 오류 메시지를 다루기 위해서다.
  - `reserveManwon`이 빈 문자열이면 정책 기본값을 쓴다. 정책이 늦게 도착해도 사용자 입력을 덮어쓰지 않는다.
  - 계산 결과는 저장하지 않고 입력 + 정책에서 매번 만든다.
- **예산 계산** (`calculate-budget.ts`): 정책 응답과 입력을 받아 3장 결과를 돌려주는 순수 함수다. React·네트워크에 의존하지 않는다. 3장 검증 케이스 12개를 `__tests__/calculate-budget.test.ts`에 그대로 고정한다. 정책은 react-query로 받고 `staleTime`을 길게 둔다(하루에 한 번 바뀔 일도 드물다).
- **서버 상태**: react-query + `houseQueryKeys`. 응답은 zod 스키마로 검증하고 mapper로 화면 타입으로 바꾼다(`inspection`과 같은 패턴). 서버 오류는 `AppError.apiCode`(서버 ErrorCode 이름)로 분기한다. `AppError.code`는 FE 분류(`HTTP_ERROR`\|`NETWORK_ERROR`\|`INVALID_RESPONSE`)다.
- **카카오 SDK**: 지금 FE에는 카카오 코드가 없으므로 `src/lib/kakao/kakao-loader.ts`를 새로 만든다. house와 임장 지도(FC4b)가 함께 쓰므로 도메인이 아니라 `lib`에 둔다. 앱 전체에서 1회만 로드한다(`autoload=false` + `kakao.maps.load`, 진행 중인 로드는 같은 Promise를 돌려준다). 로더 URL에는 `libraries=clusterer`를 넣는다(SDK는 한 번만 로드되므로 라이브러리도 한 번에 정해야 한다). `kakao-maps.d.ts`에 쓰는 타입(`Map`, `LatLng`, `LatLngBounds`, `CustomOverlay`, `MarkerClusterer`, `event`)만 선언한다. 새 npm 의존성은 넣지 않는다.
- **카카오 키**: `VITE_KAKAO_MAP_JS_KEY`(선택). `src/lib/env/env.ts`의 zod 스키마와 `AppEnv`에 optional로 추가해 `getAppEnv().kakaoMapJsKey`로 읽는다(빈 문자열은 없음으로 본다). 없으면 4장 "지도 없는 모드"로 동작한다. 도메인은 카카오 개발자 콘솔에 등록한다.
- **지도 영역** (`map-bounds.ts`): 영역을 소수 셋째 자리로 바깥쪽으로 맞추고(sw 내림, ne 올림), 레벨·영역 폭으로 레이어(DISTRICT/DONG/COMPLEX)를 고르는 순수 함수. 영역 폭 규칙은 BE 검증과 맞춘다: 단지는 0.2도 이하, 동 묶음은 1.0도 이하. 넘으면 한 단계 위 묶음으로 올린다(COMPLEX → DONG → DISTRICT). DISTRICT는 영역 없이 부르므로 폭 제한이 없다. 레이어 임계값은 상수 한 곳에 둔다(실측 후 조정).
- **섹션 간 연동**: 예산 결과 → (300ms 디바운스) 지도 가격 필터 / 마커·리스트·랭킹의 `[비교 담기]` → `cmp` (현재 평형 토글 값) / 구·동 묶음 클릭 → `lat`·`lng`·`level` 갱신 / 랭킹 `[지도에서 위치 보기]` → `lat`·`lng`·`level` 갱신 + 정보 카드 오픈 + `#find`로 스크롤.
- **반응형**: 데스크톱은 섹션 세로 배치이고, 집 찾기 섹션 안에서만 지도(좌) + 후보 리스트(우) 2열이다. 모바일은 세로 스택과 후보 리스트 바텀시트다.
- **금액 표시**: `format-eok.ts` 하나로 통일한다.
  - 1억 이상: 억 단위, **반올림**, 소수 자리 고정. 일반은 둘째 자리, 지도 라벨은 첫째 자리.
  - 1억 미만: "N,NNN만", 소수 없음.
  - 정수 연산으로 반올림한다: 일반 `Math.round(abs / 100) / 100`, 지도 `Math.round(abs / 1000) / 10`. 음수는 절댓값을 반올림한 뒤 부호를 붙인다(JS `Math.round(-0.5)`는 `-0`이 돼서 반올림 방향이 어긋난다).
  - 반올림한 값이 1억 이상인지가 아니라 **원값**이 10,000만원 이상인지로 억/만 표기를 고른다.

  | 원값(만원) | 일반 | 지도 라벨 |
  | --- | --- | --- |
  | 57,340 | 5.73억 | 5.7억 |
  | 57,350 | 5.74억 | 5.7억 |
  | 150,000 | 15.00억 | 15.0억 |
  | 10,000 | 1.00억 | 1.0억 |
  | 9,500 | 9,500만 | 9,500만 |

- **등락 표시** (`formatChangeManwon`, `formatChangePct`): 0이 아니면 부호를 항상 붙이고, 0이면 부호 없이 쓴다. 금액 규칙은 위와 같다. 변동률은 소수 둘째 자리 고정.

  | 값 | 표기 |
  | --- | --- |
  | +2,000만원 | +2,000만 |
  | −12,000만원 | -1.20억 |
  | 0 | 0만 (변동 없음) |
  | +4.65% / −3.1% / 0% | +4.65% / -3.10% / 0.00% |

  색은 5장 규칙(상승 파랑, 하락 빨강, 0은 기본색)을 따른다.
- **디자인**: 프로토타입(`docs/SeoulChonnom_Prototype.html`)에 이 화면이 없으므로 `DESIGN.md` 토큰과 기존 컴포넌트로 구성한다. 시안이 생기면 그 시안을 따른다.
- **테스트 (FE)**: `calculate-budget` 검증 케이스, `format-eok`(위 두 표 전부), `compare-param`(직렬화, `59`↔`B59` 변환, 같은 단지 59·84 허용, 같은 평형 중복 거부, 최대 3개), `map-bounds`(바깥쪽 반올림, 레이어 선택, 0.2도 초과 시 DONG, 1.0도 초과 시 DISTRICT, DISTRICT 요청에 영역 파라미터 없음), `useBudgetInput`(빈 여유금은 정책 기본값, 범위 오류 우선), zod 스키마(`costRates` 정렬 규칙), msw로 지도 없는 모드의 구 리스트 → 후보 리스트, 비교표 `missingIds` 처리.

## 12. 로드맵과 작업 분해

6단계로 나누고, 각 단계는 완료 기준을 통과해야 다음으로 간다. 예산 계산(1단계)은 외부 데이터 없이 끝낼 수 있어 수집(2단계)과 병렬 진행 가능하다.

### 0단계 — 사전 준비

- [ ] 공공데이터포털 활용신청: 아파트 매매 실거래가 상세, 건축물대장정보, 전국초중등학교위치표준데이터. 각 API의 이용허락범위(저장 가능 여부)와 일일 호출 한도 확인
- [ ] 실거래 API 1회 호출로 응답 필드 확인, 특히 `aptSeq`가 모든 행에 채워지는지 확인(8장 전제)
- [ ] 카카오 개발자 앱: JS 키(지도), 서비스 도메인 등록. REST 키(로컬 API)는 쓰지 않는다
- [ ] 행안부 검색·좌표 키가 운영 환경에 설정돼 있는지 확인
- [ ] 13장 확인 필요 항목 결정

### 1단계 — 예산 계산

- [ ] BE: `HouseBudgetPolicyJpo`, 정책 시드 SQL, `GET /house/budget-policy`
- [ ] FE: `calculate-budget.ts` + 검증 케이스 12개, `BudgetForm`, `BudgetResult`, sessionStorage 저장, 입력 오류 안내

* 완료 기준: 3장 검증 케이스 12개 통과, 케이스 1 입력으로 화면에 예산 5.73억 · DSR 4.54억 · LTV 4.01억 표시, URL에 예산 값이 없음

### 2단계 — 수집과 단지 보강

- [ ] BE: `MolitTradeGateway`, `TradeCollector`, `house_collect_unit` 우선순위, dedup·해제·원천 삭제 처리
- [ ] BE: 행안부 좌표, 건축물대장 보강, 관리자 보강 목록·주소 보정 API
- [ ] BE: `HouseNightlyScheduler` 단계 1\~3, `house_batch_run` 기록

* 완료 기준: 40개월 백필 완료(`house_collect_unit` 전부 DONE), 최근 12개월 거래 기준 좌표 보강률 95% 이상, 해제·원천 삭제 거래 통계 제외 확인

### 3단계 — 지도 탐색

- [ ] BE: 이상치 판정, `AreaStatBuilder`(최근 실거래가), `GET /house/complexes`(영역·`lawdCd`), `GET /house/complexes/summary`, `GET /house/complexes/{complexId}`, `GET /house/meta`
- [ ] FE: 카카오 로더(`src/lib/kakao`), 구·동 묶음 / 단지 레이어 전환, 묶음 클릭 확대, 가격 오버레이, 평형 토글, 후보 리스트, 정보 카드, 지도 없는 모드, 레이어 level 실측

* 완료 기준: 예산 결과 변경 시 1초 안에 마커 갱신, 후보 300개에서 지도 이동이 끊기지 않음, 기간 라벨 표시, 1920px 화면에서도 단지 조회 400 오류 없음

### 4단계 — 단지 비교

- [ ] BE: 학교 데이터 적재 + 단지별 최근접 거리, (데이터셋 확정 시) 정비사업 적재 + 단지 연결, `GET /house/compare`, `GET /house/complexes/search`
- [ ] FE: 비교 트레이, 단지 검색, 9개 지표 표, 낮은 표본 배지, `cmp` URL

* 완료 기준: 3개 단지 비교표가 공유 URL로 동일하게 복원(로그인 후), 비로그인 접근 시 로그인 화면으로 이동

### 5단계 — 랭킹과 마무리

- [ ] BE: `RankingBuilder` LEADER·RISE·FALL·VOLUME, 동률 정렬, `GET /house/rankings`
- [ ] FE: 랭킹 탭, 지도 연동, 면책·기준일 표기, 모바일 레이아웃, 홈 허브 진입

* 완료 기준: 구별 대장 항상 25행, 상승·하락 최대 20행(부족하면 있는 만큼), 야간 배치 후 기준일 갱신

## 13. 확인 필요 사항

결정 전에는 기본안을 적용한다.

| # | 이슈 | 기본안 | 담당 |
| --- | --- | --- | --- |
| 1 | `aptSeq`가 모든 실거래 행에 있는지 | 있다고 보고 설계. 빈 행은 대체 키(8장) | BE (0단계 확인) |
| 2 | 정비사업 데이터셋 | 미확정이면 7번 지표 "정보 없음"으로 출시 | BE |
| 3 | 카카오맵 레이어 전환 level | 구 묶음 ≥ 8, 동 묶음 6\~7, 단지 ≤ 5 (영역 0.2도 초과 시 동 묶음, 1.0도 초과 시 구 묶음), 실측 후 조정 | FE |
| 4 | 공공데이터포털 API별 일일 호출 한도 | 상한 1,000(설정값), 한도 확인 후 조정 | BE |

**결정 완료**

- DB는 PostgreSQL(기존), 좌표는 행안부, 비회원 접근 불가
- 예산은 브라우저 계산, 입력값 URL 미포함, 생애최초 아닌 무주택 LTV 40%
- 기간은 오늘 기준, 랭킹 20개 미만은 있는 만큼, 별도 실행기 없음
- 소규모 단지: 세대수와 무관하게 모두 노출 (거래가 적으면 표본 배지로 구분)
- 직거래: 통계에 포함하고 이상치 판정으로 저가 직거래를 제외, `dealing_type`은 저장
- 관리자 보강: 1차는 API만 (Swagger로 사용), FE 화면 없음
- 축소 지도: 구별 대장 대신 구·동별 예산 내 후보 수 묶음, 클릭 시 확대. 구별 대장은 랭킹 탭에만
- FE 리뷰 추가 API (BE 합의 완료): 구·동 묶음 API(`/house/complexes/summary`, 구 중심은 고정 좌표), `/house/complexes?lawdCd=`, 비교표 `missingIds`·`districtName`·`umdName`, `redevelopments` null/[] 구분(`REDEVELOPMENT_SYNC` SUCCESS 기록 기준, 학교와 단계 분리), 영역 폭 초과 시 상위 묶음으로 전환(0.2도 → 동, 1.0도 → 구)
- 평형 토글: 페이지에 하나, 지도·리스트·랭킹 공유. 비교함 항목은 담을 때 평형 고정 + 항목별 전환
- 비교함: 같은 단지 59·84 동시 담기 허용(각 1칸), 같은 평형 중복 불가
- 금액 표기: 반올림, 소수 자리 고정(15.00억), 등락은 부호 표기(+2,000만, -1.20억)
- 취득세 6\~9억 누진 세율: 지방세법 조문대로 비율을 소수 넷째 자리까지 반올림(7억 → 0.0167). 검증 케이스 5 = 71,242
- 생애최초 감면 40세 미만 300만원 상향안: 법이 확정되면 정책 행을 추가하고 나이 입력을 넣는다. 그 전까지는 200만원

## 참고 자료

- [시골쥐의 도시생활 — 같은 돈으로 "제일 좋은 집" 사는법](https://www.youtube.com/watch?v=9xuHU52O7uw)
- [2026년 8월 주택담보대출 금리·한도 총정리 (주택가격별 한도, 스트레스 금리 하한)](https://mhb-blog.com/?p=4694)
- [10·15 대책: 15억 집 대출 최대 4억 (머니투데이)](https://www.mt.co.kr/finance/2025/10/15/2025101507090160110)
- [2026년 지방세제 개편안 (생애최초 감면)](https://www.taxnet.co.kr/contents/taxnetpost/post-detail?id=5002)
- [지방세법 제11조 제1항 제8호 (국가법령정보센터, 2026-07-01 시행)](https://law.go.kr/lsLinkCommonInfo.do?lsJoLnkSeq=1021846727)
- [국토교통부 아파트 매매 실거래가 상세 자료 (공공데이터포털)](https://www.data.go.kr/data/15126468/openapi.do)
- `docs/field_research_map/02-legal-and-terms.md` — 지도·주소 API 저장 약관 검토
