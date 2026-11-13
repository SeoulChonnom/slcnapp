# 정비사업 CSV 등록 방법

서울시 도시정비사업 통계(분기 갱신)를 내집찾기에 반영한다. 관리자 계정으로 한다.

## 1. 원본 받기
- 서울 열린데이터광장 OA-22856 "서울특별시 도시정비사업 통계": https://data.seoul.go.kr/dataList/OA-22856/F/1/datasetView.do
- 엑셀을 내려받는다. 시트 이름·띄어쓰기·"-" 표기·중간 머리글 행은 분기마다 달라도 괜찮다(서버가 정리한다).

## 2. 양식에 붙여넣기
1. `docs/myseoulhouse/redevelopment-template.csv`를 엑셀로 열면 머리글 한 줄만 있다.
2. 원본 엑셀의 **A5:J끝**(머리글 아래 데이터 전부, 열 A~J)을 복사해 양식의 **A2**에 붙여넣는다. 11번째 열(K, 좌표검색주소)은 비워 둔다.
3. **CSV UTF-8(쉼표로 분리)** 로 저장한다. 그냥 "CSV"는 한글이 깨지므로 서버가 거절한다(`ENCODING`).

## 3. 올리기
```bash
curl -X POST "https://{호스트}/house/admin/redevelopments/import" \
  -H "Authorization: Bearer {ADMIN 토큰}" \
  -F "file=@redevelopment.csv"
```
성공(200):
```json
{ "total": 496, "inserted": 496, "updated": 0, "unchanged": 0, "deleted": 0, "skippedRows": 0, "geocodePending": 496 }
```
- 매번 **통째 교체**다. 파일에 없는 구역은 지워진다. 바뀐 게 없는 구역은 건드리지 않는다.
- 주소(자치구·지번·도로명·좌표검색주소)가 바뀐 구역과 새 구역은 다음 야간 배치(5단계)에서 좌표를 다시 찾는다(`geocodePending`).
- `skippedRows`: 빈 행, 데이터 사이에 섞인 머리글 행(CODE가 숫자가 아닌 행).

## 4. 오류 응답 해석
오류가 하나라도 있으면 **아무것도 저장하지 않고** 400이다. `errors[]`는 최대 50개이고 `field`는 CSV 줄 번호다(`line 12`).
```json
{ "code": "VALIDATION_FAILED", "errors": [ { "field": "line 12", "code": "DISTRICT_INVALID", "message": "자치구가 서울 25개 구 이름이 아닙니다. 자치구=중" } ] }
```
| code | 뜻 | 고치는 법 |
| --- | --- | --- |
| `HEADER_MISMATCH` | 머리글이 양식과 다름 | 양식 파일의 머리글 그대로 쓴다 |
| `ENCODING` | UTF-8이 아님 | "CSV UTF-8"로 다시 저장 |
| `DISTRICT_INVALID` | 자치구가 25개 구 이름이 아님 | "종로구"처럼 정확히 |
| `NAME_REQUIRED` / `JIBUN_REQUIRED` | 구역명·지번주소가 비었거나 `-` | 원본 값 확인 |
| `PROJECT_TYPE_INVALID` | 사업유형 모름 | 주택정비형재개발, 도시정비형재개발, 공동주택재건축, 아파트지구재건축, 단독주택재건축 (띄어쓰기는 무관) |
| `STAGE_INVALID` | 추진단계 모름 | 구역지정, 추진위, 조합설립, 건축심의, 사업시행, 관리처분, 착공 |
| `CODE_DUPLICATED` | 파일 안에서 CODE 중복 | 중복 행 삭제 |
| `COLUMN_COUNT` | 열이 11개보다 많음 | 쉼표가 든 값은 따옴표로 감싸야 한다(엑셀이 저장 시 자동으로 한다) |
| `CSV_FORMAT` | 따옴표가 닫히지 않음 | 해당 줄 확인 |
| `TOO_LONG` / `EMPTY` | 값이 너무 김 / 등록할 행이 없음 | |

## 5. 좌표를 못 찾은 구역 고치기
야간 배치가 지났으면 못 찾은 구역을 조회한다.
```bash
curl "https://{호스트}/house/admin/redevelopments/incomplete?page=1&size=100" -H "Authorization: Bearer {ADMIN 토큰}"
```
`geocodeStatus`가 `FAILED`(주소로 못 찾음)인 구역은 철거됐거나 주소가 옛 번지인 경우가 많다. CSV의 **11번째 열(좌표검색주소)** 에 근처 건물의 도로명주소(예 `서울특별시 구로구 오리로 1`)를 적어 다시 올리면, 그 구역만 좌표를 다시 찾는다(검색 순서: 좌표검색주소 → 도로명 → 지번).
`PENDING`은 아직 배치가 돌지 않았거나 호출 예산이 모자라 다음 날로 넘어간 구역이다.
