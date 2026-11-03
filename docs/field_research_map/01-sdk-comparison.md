# 01. 공급자별 비교 — 기능 · 비용 · 제약

> 조사일: 2026-10-08. 요금과 무료 한도는 자주 바뀐다. **수치마다 출처와 신뢰도를 함께 적었다.**
> 신뢰도 표기: 🟢 공식 문서/공지 확인 · 🟡 공식 데브톡·커뮤니티 답변 또는 2차 자료 · 🔴 미확인 또는 자료끼리 충돌

## 0. 이 기능에 필요한 역할

| # | 역할 | 설명 |
|---|---|---|
| R1 | 지도 표시 | 배경 지도, 번호 마커, 경로 폴리라인 |
| R2 | 장소 검색 · 지오코딩 | 단지명이나 주소를 입력하면 좌표를 얻는다 |
| R3 | 방문 순서 최적화 | N개 매물을 도는 최단 순서(TSP) |
| R4 | 도보 경로 | 구간별 실제 길 폴리라인, 거리, 소요 시간 |
| R5 | 대중교통 경로 (선택) | 지역 사이 이동 |

---

## 1. 카카오맵 API (developers.kakao.com)

### 1.1 제공 기능

| 역할 | API | 비고 |
|---|---|---|
| R1 | 지도 Web SDK (JavaScript 키) | 도메인을 등록해 쓰므로 FE에 키를 노출해도 된다 🟢 |
| R2 | 로컬 API: 키워드 검색 `/v2/local/search/keyword`, 주소 검색 `/v2/local/search/address` | 키워드 검색 결과에는 장소 `id`가 있다. **주소 검색 결과에는 장소 ID가 없다** 🟢 |
| R4 | **도보 경로 조회** `GET dapi.kakao.com/v2/routing/walk` | 2026-07-21 신규 출시. 경유지 `via_x`/`via_y` **최대 5개**. `route_mode`: `BROAD_FIRST`(기본) / `SHORTEST` / `ACCESSIBLE`. 응답은 `totalDistance`(m), `totalTime`(초), `legs[].steps[].path.points` 🟢 |
| R5 | 대중교통 경로 조회 `GET dapi.kakao.com/v2/routing/publictraffic` | 경유지 없음. 요금(`fare`)과 환승 수를 준다 🟢 |
| — | 자전거 경로 조회, 정적 지도 | 2026-07-21 함께 출시 🟢 |
| R3 | **없음** | 자동차는 카카오모빌리티 "다중 경유지 길찾기"(경유지 최대 30개)가 있지만 순서를 최적화해 주지 않고, 차량 전용이다 🟢 |

> 카카오모빌리티(developers.kakaomobility.com)의 **도보 길찾기**는 별개 상품이며 **제휴사 전용(사전 계약 필요)** 이다. 개인 프로젝트에서는 쓸 수 없다. 🟢

### 1.2 비용

| 항목 | 무료 한도 | 초과 단가 | 신뢰도 |
|---|---|---|---|
| 지도 SDK 로드 | 일 300,000건 | 건당 0.1원 | 🟡 (데브톡에서 이용안내를 인용) |
| 로컬 API 전체 | 월 3,000,000건 (전체 API 합산) | — | 🟡 |
| 좌표 → 주소 변환 | 일 100,000건 | 건당 0.5원 | 🟡 |
| 도보 경로 조회 | 일 1,000건 | 건당 10원 | 🟢 (2026-07 공지) |
| 대중교통 경로 조회 | 일 1,000건 | 건당 10원 | 🟢 |
| 자전거 경로 조회 | 일 1,000건 | 건당 10원 | 🟢 |
| 정적 지도 | 일 1,000건 | 건당 2원 | 🟢 |
| 카카오모빌리티 자동차 길찾기 | 일 10,000건 | 건당 약 8원 | 🟡 (데브톡 담당자 답변) |

**무료 쿼터 정책 (2026-07-21부터)** 🟢
- 무료 쿼터는 **개발자 계정당, 카카오맵 API를 처음 활성화한 앱 하나**에만 붙는다. 그 앱에는 "카카오맵 무료 쿼터" 뱃지가 표시된다.
- 두 번째 앱부터는 비즈월렛을 연결하고 유료 API 사용을 설정해야 쓸 수 있다.
- 무료 쿼터를 다 쓰면 `429` 에러가 난다. 유료 설정을 하지 않았다면 과금되지 않고 그냥 차단된다.
- 7월 21일 이전부터 쓰던 앱은 별도 안내가 있을 때까지 기존 쿼터가 유지된다.
- ⚠️ 데브톡에는 "테스트 앱에 무료 쿼터가 붙어 버려서 운영 앱으로 옮겨 달라"는 요청이 많다. **운영 앱에서 카카오맵을 먼저 켜야 한다.**
- 사업자번호가 없는 개인 개발자도 비즈앱으로 전환할 수 있다는 커뮤니티 답변이 있다 🟡.

### 1.3 제약 (상세는 02 문서)
- 로컬 API 결과 가운데 **저장할 수 있는 것은 장소 ID와 장소 URL뿐**이다. 좌표, 이름, 주소는 "라이브 콜 only"다 🟡(데브톡 운영팀 답변이 여러 건 일관됨).
- **서비스가 자체로 보유한 좌표를 카카오맵에 표시하는 것은 허용**된다 🟡.
- 카카오 API를 다른 회사 API와 함께 쓰는 것은 별도로 제한하지 않는다 🟡.

### 1.4 평가
- R1, R4, R5를 **한 공급자**로 해결할 수 있다. 배경 지도와 경로선이 같은 도로 데이터를 쓰므로 선이 어긋나지 않는다.
- 도보 경로의 경유지가 최대 5개뿐이다. 매물이 7곳 이상이면(출발 + 경유 5 + 도착 = 7점 초과) **구간을 나눠 여러 번 호출**해야 한다.
- R2에서 좌표를 저장할 수 없는 점이 핵심 제약이다. → 공공 API로 보완한다(§6).

---

## 2. 네이버 지도 (NAVER Cloud Platform Maps)

### 2.1 제공 기능
| 역할 | API | 비고 |
|---|---|---|
| R1 | Web Dynamic Map | |
| R2 | Geocoding / Reverse Geocoding | |
| R4 | **없음** | Directions 5 / Directions 15는 **자동차 전용**이다(경유지 5개 / 15개) |
| R3 | 없음 | |

### 2.2 비용
- 2025년 기존 "AI NAVER API ▶ 지도 API"는 신규 신청이 막혔고(2025-05-22경), **무료 이용량 제공이 끝났다(2025-07-01경)** 🟡(공지 제목은 공식, 날짜는 2차 자료).
- 같은 시기 "Maps 상품 신규 출시" 공지가 있었지만 새 상품의 단가와 무료 한도는 본문을 확인하지 못했다 🔴.
- 참고(2023년 요금표): Web Dynamic Map 월 1,000만 건 무료, Directions 5는 건당 5원(월 6만 건 무료), Directions 15는 건당 20원(월 3,000건 무료). **현재 기준이 아니다** 🔴.
- 무료 이용량은 "대표 계정"(Maps를 처음 쓴 계정)에만 있다 🟢.

### 2.3 제약
- 이용약관(2025-03-20 시행) 제7조 ⑨, ⑪: 결과 데이터를 별도로 저장할 수 없고, 받은 즉시 **1회만** 쓸 수 있다. 좌표를 모아 두고 API를 다시 부르지 않고 재사용하는 것은 "엄격히 금지"한다 🟡(약관 PDF 검색 요약 + 2차 정리).

### 2.4 평가
- **도보 경로가 없어서** 이 기능의 핵심(R4)을 해결하지 못한다. 요금 체계도 불확실하다. → **채택하지 않는다.**

---

## 3. TMAP API (SK open API / TMAP 모빌리티)

### 3.1 제공 기능
| 역할 | API | 비고 |
|---|---|---|
| R1 | 지도 SDK | |
| R2 | POI 검색, 지오코딩 | |
| R4 | **보행자 경로안내** | 경유지 `passList` 지원. 최대 개수는 공식 문서로 재확인 필요 🔴 |
| R3 | **경유지 순서 최적화 10/20/30/100** | 자동차 기준으로 설명돼 있다. **보행자 모드를 지원하는지 확인하지 못했다** 🔴 |
| R5 | TMAP 대중교통 API (별도 상품) | |

### 3.2 비용 🟡 (SK open API 요금 페이지 검색 결과)
| 상품 | Free | Lite | Premium(종량) |
|---|---|---|---|
| 보행자 경로안내 | 일 1,000건 | 일 10,000건 | 건당 11원 |
| 경유지 최적화 10 | 일 50건 | 일 100건 | 건당 44원 |
| 경유지 최적화 20 | 일 50건 | 일 100건 | 건당 55원 |
| 경유지 최적화 30 | 일 1건 | 일 50건 | 건당 66원 |
| 경유지 최적화 100 | 일 1건 | 일 50건 | 건당 77원 |
| 지도 표시 / POI 검색 / 지오코딩 | 일 100,000 / 20,000 / 20,000 | | |

- Lite 정액제는 월 2,200,000원(VAT 포함)이다. 개인 프로젝트에는 비현실적이다.

### 3.3 제약
- 약관: **"TMAP Open API를 이용하여 얻어진 데이터는 저장 후 24시간 이상 사용할 수 없습니다."** 🟢. 24시간 이내의 단기 캐시는 허용된다는 뜻으로 읽힌다.
- 같은 서비스를 위해 여러 프로젝트를 만들어 무료 한도를 늘리면 불법 사용으로 간주된다 🟢.
- 무료 요금제를 상업적으로 써도 되는지, TMAP 경로를 타사 지도 위에 그려도 되는지는 **약관에서 확인하지 못했다** 🔴.

### 3.4 평가
- 도보 경로의 대안으로 쓸 만하다(무료 한도가 카카오와 같은 일 1,000건).
- 다만 경로선을 카카오 지도 위에 그리면 **도로 데이터가 달라서 선이 미세하게 어긋날 수 있다.** 그렇다고 TMAP 지도 SDK로 통일하면 국내 POI와 디자인 면에서 카카오보다 불리하다.
- 경유지 최적화는 무료 한도가 사실상 없다(일 1~50건). 그리고 이 기능의 N(10곳 이하)은 자체 계산으로 충분하다.
- → **예비 공급자.** 카카오 도보 API에 장애가 나거나 정책이 바뀔 때 대체한다.

---

## 4. ODsay (대중교통 전문)

- 무료 Basic: 개인, 학생, 5인 이하 스타트업이 대상이다. **한도 표기가 서로 다르다.** 운영정책 페이지는 일 1,000건(앱 등록 후 6개월 무료), 문의 페이지는 일 30건(기간 제한 없음)이다. 실제로 30건이 적용된 사례가 있다 🔴.
- Flex 후불은 건당 25원(VAT 별도), Standard는 일 100,000건(가격 문의) 🟡.
- 평가: 카카오 대중교통 경로 조회(일 1,000건 무료, 건당 10원)와 기능이 겹치고 더 비싸다. → **채택하지 않는다.**

---

## 5. Google Maps Platform

- 2026-02-27 국내 1:5,000 지도 데이터 반출이 **조건부로 승인**됐다(보안시설 가림, 국내 서버 선처리, 정부 승인 후 반출) 🟢(언론 보도).
- 국내에서 도보·자동차 길안내가 실제로 제공되는지는 자료마다 다르고 공식 발표를 찾지 못했다 🔴.
- 국내 POI 밀도와 아파트 단지명 검색 품질이 국내 공급자보다 떨어진다는 평가가 일관된다 🟡.
- 평가: 국내 임장 서비스에는 **채택하지 않는다.**

---

## 6. 공공 API — 좌표를 "저장할 수 있는" 출처

| API | 제공 | 용도 | 저장 | 한도·조건 | 신뢰도 |
|---|---|---|---|---|---|
| 도로명주소 검색 API (`business.juso.go.kr/addrlink/addrLinkApi.do`) | 행정안전부 | 주소 후보 검색 | ✅ "이용허락범위 제한 없음" | 운영키 필요. 트래픽 한도 미확인 | 🟡 |
| 도로명주소 좌표제공 API | 행정안전부 | 주소 → 좌표 | ✅ | **승인 필요**. 좌표계가 EPSG:5179여서 WGS84로 변환해야 한다 | 🟡 |
| SGIS 지오코딩 WGS84 (`sgisapi.mods.go.kr/OpenAPI3/addr/geocodewgs84.json`) | 국가데이터처 | 주소 → 좌표(WGS84) | ✅ 금지 조항 없음 (공공누리 1유형, 출처 표시) | 일 50,000회. **영업활동은 사전 승낙(약관 제7조 ④)** | 🟡 |
| SGIS 리버스 지오코딩 WGS84 | 국가데이터처 | 좌표 → 주소 | ✅ (위와 같음) | 위와 같음 | 🟡 |
| 도로명주소 위치정보요약DB (파일) | 행정안전부 | 전국 건물 좌표 일괄 다운로드 | ✅ 공공누리 1유형 | 이용목적 심사를 거쳐 신청 | 🟡 |
| 브이월드 Geocoder 2.0 | 국토교통부 | 주소 → 좌표 | ❌ **실시간 사용만 허용** | 일 40,000건 | 🟡 |

- 실제 사례: 다른 팀 프로젝트(woowacourse 2026-jachwi-sunbae #250, #251)도 같은 문제로 "**행안부 검색 + SGIS 지오코딩으로 좌표 저장, 카카오/네이버는 렌더링과 실시간 표시용**" 구조로 바꿨다.
- 알려진 함정(위 사례에서 보고됨)
  - SGIS 응답의 빈 값이 JSON `null`이 아니라 **문자열 `"null"`** 이다.
  - `matching` 필드는 정확한 결과에도 `"0"`이 나와서 믿을 수 없다.
  - 리버스 지오코딩 결과에 행정동과 법정동이 섞인다.
  - SGIS 인증은 `consumer_key`/`secret`으로 `accessToken`을 받는 방식이고, 토큰 유효시간이 짧다.
- 한계: **아파트 "단지명"으로는 검색할 수 없고 도로명·지번 주소만 받는다.** 그래서 사용자가 단지명을 치면 카카오 키워드 검색(실시간)으로 주소를 찾아 보여주고, 사용자가 확인한 **주소 문자열**을 행안부·SGIS로 다시 지오코딩해 저장하는 2단계 흐름이 필요하다(03 문서 §3.2). 여기서 카카오 결과의 주소를 그대로 저장해도 되는지는 **회색지대**다(02 문서 §1.1).

---

## 7. 종합 점수

| 공급자 | R1 지도 | R2 저장 가능 좌표 | R4 도보 | R5 대중교통 | 개인 규모 비용 | 약관 리스크 | 종합 |
|---|---|---|---|---|---|---|---|
| **카카오** | ◎ | ✕ (ID만) | ◎ (경유 5) | ○ | ◎ 무료 | 중 (저장 금지) | **주 공급자** |
| 공공(행안부+SGIS) | — | ◎ | — | — | ◎ 무료 | 낮음 (출처 표시) | **좌표 출처** |
| TMAP | ○ | ✕ (24h) | ◎ | ○ | ○ 무료 | 중 (혼용 불명확) | 예비 |
| 네이버 | ◎ | ✕ | ✕ | ✕ | △ 유료 전환 | 중 | 제외 |
| ODsay | — | — | — | ◎ | △ | 낮음 | 제외 |
| Google | △ | △ | ? | ? | △ | 낮음 | 제외 |

## 출처

- [카카오맵 API 신규 기능 및 무료 쿼터 운영 방식 변경 안내 (데브톡 공지)](https://devtalk.kakao.com/t/api-notice-on-new-kakao-map-api-features-and-free-quota-policy/150222)
- [Kakao Developers — 카카오맵 시작하기](https://developers.kakao.com/docs/ko/kakaomap/common)
- [Kakao Developers — 카카오맵 REST API](https://developers.kakao.com/docs/ko/kakaomap/rest-api)
- [카카오맵 유료 쿼터 사용 관련 문의 (데브톡)](https://devtalk.kakao.com/t/topic/149017)
- [카카오맵 사업자가 없는 경우 사용 불가능 여부 (데브톡)](https://devtalk.kakao.com/t/topic/147610)
- [카카오 길찾기 API 요금 (데브톡)](https://devtalk.kakao.com/t/api/142931)
- [카카오모빌리티 다중 경유지 길찾기](https://developers.kakaomobility.com/guide/navi-api/waypoints)
- [카카오모빌리티 도보 길찾기 (제휴 전용)](https://developers.kakaomobility.com/affiliate/walking/directions)
- [NCP 공지 — 지도 API 신규 이용 신청 차단 및 무료 이용량 제공 중단](https://www.ncloud.com/support/notice/all/1930)
- [NCP 공지 — Maps 상품 신규 출시 및 무료 이용량 제공 종료](https://www.ncloud.com/support/notice/all/1965?page=2)
- [NCP Maps 사용 가이드](https://guide.ncloud-docs.com/docs/maps-app)
- [Maps API 요금 변경 사전 안내('23.1~)](https://www.ncloud-forums.com/topic/99/)
- [NCP Maps 서비스 이용약관 PDF](https://xv-ncloud.pstatic.net/images/provision/%5B%EB%AF%BC%EA%B0%84%5DMaps%EC%84%9C%EB%B9%84%EC%8A%A4%EC%9D%B4%EC%9A%A9%EC%95%BD%EA%B4%80_v0.4_(CLEAN)_1742433558704.pdf)
- [SK open API — 요금](https://openapi.sk.com/products/calc?svcSeq=4&menuSeq=5)
- [SK open API — 경유지 순서 최적화 100](https://openapi.sk.com/products/detail?linkMenuSeq=50)
- [TMAP API 약관](https://tmapapi.tmapmobility.com/terms.html)
- [ODsay LAB 운영정책](https://lab.odsay.com/doc/totalPolicy)
- [ODsay 대중교통 API 문의](https://lab.odsay.com/contact/contact)
- [Korea clears exporting map data for Google (Korea Herald)](https://www.koreaherald.com/article/10684189)
- [South Korea opens the door to let Google Maps operate fully (TechCrunch)](https://techcrunch.com/2026/02/27/south-korea-opens-the-door-to-let-google-maps-operate-fully/embed/)
- [Why Google Maps Can't Guide You Through Seoul (The Diplomat)](https://thediplomat.com/2025/07/why-google-maps-cant-guide-you-through-seoul/)
- [국토교통부 지오코더 API (공공데이터포털)](https://www.data.go.kr/data/15101106/openapi.do?recommendDataYn=Y)
- [행정안전부 도로명주소 위치정보 요약DB](https://www.data.go.kr/data/15050410/fileData.do)
- [주소기반산업지원서비스](https://business.juso.go.kr/)
- [woowacourse 2026-jachwi-sunbae #250 — 지도 API 약관 검토](https://github.com/woowacourse-teams/2026-jachwi-sunbae/issues/250)
- [woowacourse 2026-jachwi-sunbae #251 — 공공데이터 API로 전환](https://github.com/woowacourse-teams/2026-jachwi-sunbae/issues/251)
