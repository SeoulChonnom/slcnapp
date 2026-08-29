# 이미지 에셋 API (FE 연동)

## 개요

표지 이미지에 축소본(variant)과 브라우저 캐시가 추가되었고, 파일 저장용 endpoint가 생겼다.

모든 변경은 additive다. **기존 프론트 코드를 바꾸지 않아도 지금까지와 동일하게 동작한다.** 아래는 적용하면 전송량이 줄어드는 선택지다.

모든 경로는 context path `/api` 하위다. 인증은 기존과 같이 `X-AUTH-TOKEN` 헤더를 요구하고, 없으면 401이다.
이미지 조회 두 개만 예외로 쿠키 인증도 허용한다. 아래 [인증](#인증)을 본다.

**이번 변경으로 FE가 알아야 할 내용은 이 문서 하나에 모두 담겨 있다.** 업로드 절차와 도메인(여행/나들이)에 파일을 연결하는 규칙은 이번에 바뀌지 않았으며, 그쪽이 필요하면 `docs/file-asset.md`를 본다.

### {fileId}는 어디서 오는가

이미지 URL에 넣는 `{fileId}`는 `FileAsset.id`다. 도메인 응답에서 아래 두 값은 항상 같으므로 어느 쪽을 써도 된다.

- `TravelRdo.cover.fileAssetId`
- `TravelRdo.cover.file.fileId`

나들이는 `TripListRdo.logo.fileId`, 업로드 응답은 `FileAssetRdo.fileId`다.

측정값(실제 사진 5,072,268 B 기준)은 다음과 같다.

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 홈 1회 진입 (표지 13건) | 52.50 MiB | 0.63 MiB |
| 재방문 | 52.50 MiB | 0 B (전부 304) |

## 하위 호환

아래는 변경되지 않았다.

- `GET /travels` — `TravelRdo[]` 배열 그대로다. envelope로 감싸지 않았다.
- `GET /travels/{travelId}`
- `GET /trips` — `TripListRdo[]`
- `GET /schedule/now` — 현재 달력월만 반환하는 동작도 그대로다.
- `GET /assets/files/{fileId}` — **쿼리 파라미터를 붙이지 않으면 원본 바이트가 그대로 나온다.** 바이트 단위로 동일함을 확인했다.
- `POST /assets/file`, `POST /assets/files` — 업로드 계약은 그대로이고 응답에 필드만 추가되었다.

## 인증

`GET /assets/files/{fileId}`와 `GET /assets/file` 두 개는 `X-AUTH-TOKEN` 헤더 **또는** 로그인 시 발급되는 `sessionId` 쿠키로 인증한다.
`img` 태그는 요청 헤더를 붙일 수 없어 액세스 토큰을 전달할 방법이 없기 때문이다.

따라서 이 두 경로는 `fetch`로 받아 blob URL을 만들 필요 없이 `img` 태그로 바로 부를 수 있다.

```html
<img src="/api/assets/files/{fileId}?variant=home-thumb" loading="lazy" width="320" height="240" alt="">
```

`sessionId`는 HttpOnly라 JS로 읽을 수 없고, 브라우저가 알아서 실어 보낸다. **프론트가 따로 할 일은 없다.**
`fetch`로 부를 때만 `credentials: 'include'`가 필요하다.

이 방식이 blob보다 나은 이유는 브라우저 HTTP 캐시를 그대로 쓸 수 있어서다.
아래 [캐시](#캐시)의 ETag/304와 `max-age`가 실제로 동작하려면 `img` 태그로 불러야 한다.

### 쿠키 인증이 적용되지 않는 경로

- `GET /assets/files/{fileId}/download` — 사용자의 클릭으로 발생하므로 `img` 태그 제약이 없다. `X-AUTH-TOKEN`이 필요하다.
- `POST /assets/file`, `POST /assets/files` — 업로드는 항상 `X-AUTH-TOKEN`이 필요하다.

### 제약

- 액세스 토큰도 `sessionId` 쿠키도 없으면 401이다. 로그아웃하면 서버 세션이 즉시 지워져 이미지도 바로 401이 된다.
- 크로스 사이트에서 이 URL을 `img`로 임베드하면 인증되지 않는다. 서비스 도메인 안에서만 동작한다.

## 이미지 조회

`GET /assets/files/{fileId}`

### 쿼리 파라미터

모두 선택이다.

- `variant`: `home-feature`(가로 960px), `home-thumb`(가로 320px), `original`(원본). 가장 우선한다.
- `width`: 정수. `variant`가 없을 때만 본다. 요청 너비를 덮을 수 있는 가장 작은 축소본을 고른다.
- `format`: 호환을 위해 받기만 하고 사용하지 않는다. 실제 포맷은 `Content-Type`으로 판단한다.

### variant 결정 규칙

| 요청 | 응답 |
| --- | --- |
| (파라미터 없음) | 원본 |
| `?variant=original` | 원본 |
| `?variant=home-thumb` | 320px 축소본 |
| `?variant=home-feature` | 960px 축소본 |
| `?variant=` 오타·미지원 값 | **기본 축소본(`home-feature`)** |
| `?width=320` (320 이하) | `home-thumb` |
| `?width=640` (321~960) | `home-feature` |
| `?width=1400` (960 초과) | 원본 |
| 축소본이 없는 자산 | 원본 |

알 수 없는 `variant`에 4xx를 반환하지 않는다. 실패 후 원본으로 재시도하는 왕복을 없애기 위한 선택이다. 따라서 **프론트는 variant 오타를 감지할 수 없으므로** 값은 정확히 `home-feature`, `home-thumb`, `original` 중 하나를 써야 한다.

### 응답 헤더

```
HTTP/1.1 200
Content-Type: image/webp
Content-Length: 18386
ETag: "52cce541-8752-4c00-9b91-452272a5d98f-home-thumb"
Cache-Control: max-age=86400, private
```

축소본은 보통 WebP지만 서버 환경에 따라 JPEG로 생성될 수 있다. 두 경우 모두 `Content-Type`이 실제 포맷을 정확히 알려주므로, 확장자나 요청한 `format`이 아니라 이 헤더를 기준으로 판단한다.

`If-None-Match`가 일치하면 304와 빈 본문을 반환한다.

## 이미지 다운로드

`GET /assets/files/{fileId}/download`

조회와 같은 바이트를 반환하되 `Content-Disposition: attachment`와 업로드 당시 파일명이 붙는다. 한글 파일명은 RFC 5987로 인코딩한다.

```
Content-Type: image/png
Content-Disposition: attachment;
  filename="=?UTF-8?Q?=EC=A0=9C=EC=A3=BC_=EB=B0=94=EB=8B=A4.png?=";
  filename*=UTF-8''%EC%A0%9C%EC%A3%BC%20%EB%B0%94%EB%8B%A4.png
```

`variant` 파라미터를 지원하며, 기본값이 조회와 다르다.

| 요청 | 조회 | 다운로드 |
| --- | --- | --- |
| (`variant` 없음) | 원본 | 원본 |
| `?variant=home-thumb` | 320px 축소본 | 320px 축소본 (`제주 바다_home-thumb.webp`) |
| `?variant=` 오타 | 축소본(`home-feature`) | **원본** |

저장은 사용자가 파일로 남기는 행위이므로, 알 수 없는 값이 왔을 때 조용히 축소된 파일을 저장시키지 않는다. 규칙은 **조회는 알 수 없으면 축소본, 저장은 원본**이다.

다운로드 응답의 ETag는 `"{fileId}-{variant}-download"`로 조회 응답과 분리되어 있다. 같은 바이트라도 헤더가 달라 캐시 항목이 섞이면 `Content-Disposition` 없는 응답이 재사용될 수 있기 때문이다. 프론트가 신경 쓸 것은 없다.

## FileAssetRdo 추가 필드

`TravelRdo.cover.file`, `TripListRdo.logo`, 업로드 응답에 모두 포함된다. 기존 필드는 그대로다.

- `width` (int): **원본** 가로 픽셀. 축소본도 종횡비가 같으므로 레이아웃 예약에 쓸 수 있다.
- `height` (int): 원본 세로 픽셀.
- `variants` (string[]): 이 자산에 실제로 존재하는 축소본 이름. 예: `["home-feature", "home-thumb"]`

### 구 자산은 0으로 나온다

축소본 기능 이전에 업로드된 자산은 `width`/`height`가 `0`, `variants`가 `[]`다.

```
신자산여행  1238x2460  variants=["home-feature","home-thumb"]
구자산여행     0x0     variants=[]
```

`width`로 `aspect-ratio`나 `<img width>`를 계산하면 0으로 나누어 레이아웃이 깨진다. **`width > 0`일 때만 크기 힌트로 사용하고, 아니면 기존 방식으로 폴백해야 한다.**

## 축소본이 없는 자산

축소본은 업로드 시점에 생성한다. 그 이전에 올라간 자산에는 축소본이 없고, 아직 일괄 생성 작업을 돌리지 않았다.

축소본이 없는 자산에 `?variant=home-thumb`를 요청하면 오류 없이 원본이 나온다. 프론트는 자산의 나이를 구분하지 않고 언제나 원하는 variant를 요청하면 되며, 표지가 사라지는 일은 없다. 다만 그 자산에서는 용량 이득이 없다.

미리 알고 싶으면 `variants` 배열을 확인한다. 비어 있으면 축소본이 없는 자산이다. 일괄 생성 작업이 실행되면 이 배열이 채워지고, 이후 같은 요청이 **프론트 코드 변경 없이** 축소본을 받게 된다.

## 홈 화면 권장 사용

| 위치 | 요청 | 이유 |
| --- | --- | --- |
| 최신 여행 대표 표지 1건 | `?variant=home-feature` | 크게 보이는 자리다. 960px면 2배 DPR에서도 충분하다. `fetchPriority="high"` 유지. |
| 목록 행 썸네일 12건 | `?variant=home-thumb` | 1건당 약 18~28 KB. `loading="lazy"` 유지. |
| 상세 화면 큰 이미지 | (파라미터 없음) | 원본 화질이 필요한 자리다. |
| 사진 저장 버튼 | `/download` | 파일명이 붙는다. |

## 캐시

권장한다.

- 같은 이미지에는 같은 URL을 쓴다. 쿼리 순서가 달라지면 캐시 항목이 갈린다.
- `img` 태그로 부른다. 브라우저 HTTP 캐시가 `ETag`와 `max-age`를 그대로 처리한다.
- fetch를 쓴다면 기본 캐시 동작을 그대로 둔다.
- Blob URL을 만들었다면 `URL.revokeObjectURL`로 정리한다.

피해야 한다.

- `cache: "no-store"` 또는 `"reload"`. 이번 작업의 이득이 전부 사라진다.
- URL에 `?t=Date.now()` 같은 캐시 무효화 파라미터를 붙이는 것.
- `format=webp`를 근거로 응답 포맷을 단정하는 것. 이 값은 무시된다.

## 확인 방법

```bash
# 원본 (지금까지와 동일)
curl -H "X-AUTH-TOKEN: $T" \
  http://localhost:8080/api/assets/files/$FID -o cover.png

# 썸네일 — 약 18 KB
curl -H "X-AUTH-TOKEN: $T" \
  "http://localhost:8080/api/assets/files/$FID?variant=home-thumb" -o thumb.webp

# 재검증 — 304, 본문 0바이트
curl -D - -o /dev/null -H "X-AUTH-TOKEN: $T" \
  -H 'If-None-Match: "'$FID'-home-thumb"' \
  "http://localhost:8080/api/assets/files/$FID?variant=home-thumb"

# 저장 — 파일명이 붙는다
curl -OJ -H "X-AUTH-TOKEN: $T" \
  http://localhost:8080/api/assets/files/$FID/download

# 쿠키 인증 — img 태그가 보내는 것과 같은 요청. 200이어야 한다
curl -D - -o /dev/null \
  -b "sessionId=$SID" \
  -H 'Sec-Fetch-Site: same-site' -H 'Sec-Fetch-Dest: image' \
  "http://localhost:8080/api/assets/files/$FID?variant=home-thumb"

# 쿠키 인증이 열려서는 안 되는 경로 — 둘 다 401이어야 한다
curl -o /dev/null -w '%{http_code}\n' -b "sessionId=$SID" \
  http://localhost:8080/api/assets/files/$FID/download
curl -o /dev/null -w '%{http_code}\n' -b "sessionId=$SID" \
  http://localhost:8080/api/travels
```

`$SID`는 로그인 응답의 `Set-Cookie: sessionId=...` 값이다.
`curl`은 브라우저와 달리 SameSite를 강제하지 않으므로, 실제 동작 확인은
브라우저에서 `img` 태그로 한 번 더 해야 한다.

## 백엔드에 요청이 필요한 항목

- **다른 축소본 크기가 필요한 경우** — 축소본 크기는 서버가 정한다(현재 960 / 320). 임의의 `width`로 즉석 변환하지 않으므로, 새 크기가 필요하면 백엔드에 요청한다.
- **구 자산 축소본이 필요한 경우** — 일괄 생성 작업이 필요하다. 현재 대상은 31건이다.
- **일정 영역이 월말에 비어 보이는 문제** — `GET /schedule/now`는 현재 달력월만 반환하므로 월말에 다음 달 일정만 남으면 `[]`가 된다. 제품 결정이 필요한 항목이라 변경하지 않았다. 프론트에서 우회하려면 `GET /schedule?start=&end=`를 쓰되 제약이 두 가지 있다.
  - `yyyy-MM-dd'T'HH:mm:ssXXX` 오프셋 필수 (예: `2026-09-01T00:00:00+09:00`)
  - 조회 구간이 1개월을 초과하면 400
