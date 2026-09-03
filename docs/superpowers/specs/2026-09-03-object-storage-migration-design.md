# 이미지 오브젝트 스토리지 이전 설계

**작성일:** 2026-09-03
**대상 모듈:** `slcn-spec`, `slcn-aggregate`, `slcn-rest`, `slcn-auth`, `slcn-boot`
**관련 문서:** `docs/file-asset.md`, `docs/image-asset-api.md`, `docs/architecture.md`, `docs/module.md`

---

## 1. 배경

이미지를 서버 로컬 디스크에 저장하고 있어 사진이 늘어나면 서버 용량이 고갈된다. 카메라 사진을 다루므로 파일 하나가 크고, 같은 사진이 RAW와 JPG 두 벌로 올라온다.

### 1.1 실제 워크로드

| 항목 | 값 |
|---|---|
| RAW 파일 크기 | 약 100 MB (CR2/ARW/NEF, 45~60 MP급) |
| JPG 파일 크기 | 약 30 MB (동일 화소수) |
| 한 장(쌍) 합계 | 약 130 MB |
| JPG의 성격 | 필름 필터가 적용되어 RAW와 색감이 다르다. **사용자가 기대하는 그림은 JPG 쪽이다.** |
| 원본 다운로드 | 잦다 |
| 클라이언트 | 웹 전용. 데스크톱 + 모바일 웹(네이티브 앱 없음) |

### 1.2 현재 구현의 한계 (코드 확인 결과)

1. **`spring.servlet.multipart.max-request-size` 미설정** — `application.yml`에 `max-file-size: 10MB`만 있고 `max-request-size`가 없어 Spring Boot 기본값 10MB가 적용된다. 다중 업로드는 요청 전체가 10MB에서 막힌다.
2. **`FileConstant.MAX_FILE_SIZE = 10MB`**, `EXT_REGEX_STRING = "jpg|png|jpeg|gif|svg"` — RAW/HEIF는 업로드 자체가 거부된다.
3. **같은 이미지를 두 번 전체 디코딩한다.**
   - `FileUtils.validateImageFile` — 검증 목적의 `ImageIO.read(inputStream)` (`FileUtils.java:273`)
   - `FileUtils.writeVariants` → `readImage(originalPath)` (`FileUtils.java:90`)
   - 50 MP 이미지의 `BufferedImage`는 화소수 × 3~4 바이트 = **150~200 MB**. `scaleToWidth`의 중간 단계까지 더하면 장당 약 210 MB가 톰캣 요청 스레드에 묶인다.
4. **`FileLogic.readImageFile`이 `Files.readAllBytes()`로 파일 전체를 힙에 올린다** (`FileLogic.java:105`). 100 MB RAW 다운로드가 잦아지면 서버가 먼저 죽는다.
5. 파생본까지 로컬에 쌓이므로 원본 1장당 파일이 3개까지 늘어난다.

---

## 2. 결정 사항

### D1. 스토리지는 Cloudflare R2를 쓴다

1만 쌍 = 1.3 TB, 월 20%(260 GB) 다운로드 가정:

| | 저장 | Egress | 월 합계 |
|---|---|---|---|
| S3 Seoul Standard | ~$33 | ~$33 | ~$66 |
| **Cloudflare R2** | ~$20 ($0.015/GB) | **$0** | **~$20** |

원본 다운로드가 잦다는 조건에서 egress가 비용을 지배하므로 R2의 무료 egress가 결정적이다. R2는 S3 호환 API라 AWS SDK v2를 endpoint override로 그대로 쓴다.

**Glacier / Infrequent Access 티어링은 쓰지 않는다.** Glacier Instant Retrieval은 인출료 $0.03/GB가 붙어, 월 평균 0.6회 이상 읽히면 Standard보다 비싸다. 다운로드가 잦은 워크로드에는 손해다.

**포트를 두어 S3로 되돌릴 수 있게 한다.** 두 서비스 모두 S3 API이므로 어댑터 교체 비용은 낮게 유지한다.

### D2. 원본 바이트는 서버를 통과하지 않는다

원본 업로드는 presigned PUT, 원본 조회·다운로드는 presigned URL로 302 리다이렉트한다. 서버는 인증·메타데이터·URL 서명만 담당한다.

`response-content-disposition` 쿼리 파라미터로 파일명과 attachment를 넘길 수 있으므로, 현재 `FileResource.attachmentDisposition()`의 RFC 5987 한글 처리를 그대로 옮긴다.

presigned URL의 만료는 **요청 시작 시점에만** 검사되므로, 5분 만료 URL로 100 MB 다운로드를 시작하면 전송은 끝까지 진행된다. 만료를 짧게(5분) 잡아도 안전하다.

### D3. 파생본은 JPG(PRIMARY)에서만 만든다

JPG에 필름 필터가 적용되어 있으므로 RAW의 임베디드 프리뷰로 썸네일을 만들면 **목록의 색감과 다운로드한 JPG의 색감이 달라진다.** 파생본의 소스는 항상 JPG다.

RAW 임베디드 프리뷰 추출은 **JPG 없이 RAW만 올라온 경우의 폴백**으로만 남긴다.

이 결정의 부수 효과가 크다. **워커가 RAW를 디코딩할 일이 없다.** RAW에서 필요한 것은 EXIF뿐이고 헤더만 읽으면 된다.

| 의존성 | 필요도 | 용도 |
|---|---|---|
| libvips (또는 ImageMagick) | 필수 | JPG → WebP 파생본, shrink-on-load |
| exiftool / metadata-extractor | 필수 | RAW·JPG EXIF (헤더만) |
| libraw / RAW delegate | 선택 | RAW 단독 업로드 시 폴백 프리뷰 |
| libheif | 선택 | HEIC 유입 시 |

### D4. RAW/JPG 페어링은 FE가 선언한다

필터로 픽셀이 달라져 내용 기반 매칭은 신뢰도가 낮다. 파일명도 위험하다 — Lightroom 내보내기는 `IMG_1234.CR2` 옆에 `IMG_1234-Edit.jpg`를 만든다.

사용자는 파일 선택창에서 둘을 함께 고르므로 **그 정보를 그대로 받는다.** FE가 확장자와 stem으로 묶어 제안하고 사용자가 화면에서 확인·수정한다. 서버의 stem + EXIF 휴리스틱은 FE가 못 묶은 경우의 보조 수단이다.

`role`은 `PRIMARY`(파생본 생성 대상) / `RAW`(보관·다운로드 전용)로 명시적으로 받는다. 워커가 무엇을 디코딩할지 추측하지 않게 한다.

### D5. Rendition별 상태와 Photo 상태를 분리한다

JPG 30 MB가 RAW 100 MB보다 3배 빨리 끝난다. Photo 전체 완료를 기다리면 사용자는 100 MB가 다 올라갈 때까지 빈 칸을 본다.

```
Rendition:  PENDING → UPLOADED → PROCESSED / FAILED
Photo:      PENDING → PREVIEW_READY → COMPLETE
                          ↑                ↑
                   PRIMARY 처리 완료   선언된 모든 rendition 도착
                   (갤러리 노출 시작)
```

- RAW 업로드가 실패해도 **Photo를 잃지 않는다.** RAW rendition만 `FAILED`로 두고 재시도 가능하게 한다.
- `PREVIEW_READY` 미만인 자산은 도메인(Travel/Trip)에서 참조할 수 없다.

### D6. 파생본 생성은 FE 우선, 서버 폴백

브라우저에서 `OffscreenCanvas.convertToBlob({type:'image/webp'})` 또는 `@jsquash/webp`(libwebp WASM)로 WebP를 만들 수 있다. 서버 워커가 전체 업로드의 5~10%에서만 돌게 되고, 업로드 직후 즉시 미리보기가 가능해진다.

**단, 서버 워커는 반드시 만든다.** 아래가 전부 서버 경로로 간다.

- RAW 단독 업로드 (브라우저가 RAW를 디코딩할 수 없다)
- WebP 인코딩 미지원 브라우저 (Safari는 버전 편차가 있고, 미지원 시 **에러 없이 PNG로 폴백**하므로 feature-detect가 필수)
- iOS Safari 캔버스 면적 상한 초과 (대략 16.7 M px부터. 넘으면 예외 없이 빈 캔버스를 반환한다)
- **비-sRGB 이미지** — 카메라 JPEG는 Adobe RGB나 Display P3인 경우가 많고 `canvas.drawImage`의 ICC 처리는 브라우저마다 다르다. **필름 필터의 색이 결과물이므로** sRGB가 아니면 클라이언트 변환을 포기하고 서버로 넘긴다.
- 미래의 재처리 (새 변형 크기 추가, 품질 조정, 버그 수정). 원본 JPG를 R2에 보관하므로 서버 백필이 가능하다.

FE는 `derivedBy: "CLIENT" | "NONE"`을 complete 통보에 담는다.

### D7. 인증은 자산 종류별로 다르다

presigned URL은 서명이 쿼리스트링에 들어가 **서명할 때마다 값이 바뀐다.** CDN은 URL 전체를 캐시 키로 쓰므로 매번 캐시 미스가 된다. 재사용률이 낮은 원본은 상관없지만 썸네일은 정반대다.

| | URL | 인증 | CDN |
|---|---|---|---|
| 원본 RAW·JPG | 요청마다 presigned, 5분 만료 | 서명 쿼리 | 캐시 안 함 |
| 파생본 WebP | **고정 URL** | **서명 쿠키** | **캐시 적중** |

파생본은 CloudFront Signed Cookies / Cloudflare 서명 쿠키를 쓴다. 현재 `ImageSessionAuthenticationFilter`(sessionId 쿠키)가 이미 같은 발상이므로, 그 필터를 걷어내는 대신 **로그인 시 CDN 도메인 스코프의 서명 쿠키를 함께 발급**하는 쪽으로 옮긴다.

FE가 만든 썸네일은 다른 사용자에게 서빙되는 사용자 제공 콘텐츠다. presigned 조건으로 `Content-Type: image/webp`를 강제하고 CDN에 `X-Content-Type-Options: nosniff`를 건다.

### D8. `ImageIO`를 버리고 shrink-on-load를 쓴다

960 px 썸네일을 만드는 데 전체 해상도 디코딩은 필요 없다. libvips `thumbnail`과 ImageMagick `-define jpeg:size=`는 JPEG DCT 단계에서 1/2·1/4·1/8로 축소해 읽어, 같은 50 MP 이미지를 **약 50 MB**로 처리한다.

**CLI로 호출하면 JVM 힙 밖에서 끝나 OOM 위험 자체가 사라진다.** 워커는 별도 프로세스 또는 전용 스레드풀(동시 2~4)에서 돌린다.

### D9. 업로드는 크기로 방식을 나눈다

```
파일 크기 <  32 MB → presigned 단일 PUT      (폰 사진, HEIC/JPEG)
파일 크기 >= 32 MB → presigned multipart      (RAW, 대용량 JPG)
```

폰 사진은 2~5 MB라 단일 PUT으로 충분하고, multipart의 N+2 왕복과 CORS 복잡도를 대부분의 업로드에서 피한다. 파트 크기는 8~16 MB. `uploadId`와 완료 파트 목록을 IndexedDB에 저장해 재개를 지원한다 — 모바일 웹에서 130 MB는 탭 백그라운딩 한 번에 날아간다.

**버킷에 `AbortIncompleteMultipartUpload` 라이프사이클 규칙(7일)을 반드시 건다.** 중단된 multipart의 업로드된 파트는 abort 전까지 스토리지 요금이 계속 붙는다.

### D10. 완료 통보는 이중화한다

R2에는 S3 Event Notification → SQS 조합이 없다(Cloudflare Queues + Workers가 필요). 그리고 클라이언트의 완료 통보만 믿으면 (a) 통보 누락 시 고아 객체, (b) 거짓 통보가 발생한다.

- **1차:** FE의 complete 통보
- **2차:** 스케줄러 reconciliation — `PENDING` 상태로 N시간 지난 rendition에 대해 `HeadObject`로 실제 존재·크기를 확인하고 상태를 정정한다. 일정 시간이 지나도 없으면 정리한다.

### D11. 서버 검증은 "바이트 처리"가 아니라 "주장 검증"이다

presigned로 가면 서버가 업로드 바이트를 보지 못해 `FileUtils.validateImageFile`이 무력화된다. 대체 수단:

- presigned 발급 시 조건 강제 — `Content-Type`, `content-length-range`, key prefix 고정, 만료 5~15분
- **`HeadObject`로 존재·크기 대조** (바이트를 받지 않는다)
- **Range GET으로 앞 4 KB만 읽어 매직바이트 확인** — WebP `RIFF....WEBP`, JPEG `FFD8FF`, PNG `89504E47`, CR2/TIFF계 `II*\0` 또는 `MM\0*`
- 불일치 시 객체 삭제 + rendition `FAILED`

**SVG는 업로드에서 제거한다.** 현재 `validateSvg`는 `<svg` 문자열만 확인하고 스크립트를 검사하지 않는다. presigned로 오면 XSS 벡터가 되고, 사진 서비스에 SVG 업로드가 필요하지 않다.

---

## 3. 목표 아키텍처

```
React FE
  │ 1. POST /assets/upload-sessions   (photo 단위로 rendition 선언)
  ▼
Spring Boot ── 인증, FileAsset(PENDING) 생성, presigned PUT 발급
  │
  │ 2. PUT presigned URL (원본 RAW·JPG, 가능하면 FE 생성 WebP까지)
  ▼
Cloudflare R2
  │
  │ 3. POST /assets/upload-sessions/{id}/complete   (+ 스케줄러 reconcile)
  ▼
Spring Boot ── HeadObject + 앞 4KB 매직바이트 검증
  │
  ├─ derivedBy=CLIENT → 메타 기록, PREVIEW_READY
  └─ derivedBy=NONE   → NEEDS_PROCESSING → 워커(libvips/exiftool, 힙 밖)
  │
  ▼
R2 (derived/) → CDN(서명 쿠키, 고정 URL) → FE 갤러리
R2 (originals/) → presigned 302 → FE 다운로드
```

---

## 4. 단계 구분

각 단계는 그 자체로 동작하고 배포 가능해야 한다.

### Phase 1 — 스토리지 포트 + R2 이전 + 원본 302 (본 계획의 범위)

**목표: 서버 디스크 용량 문제와 원본 다운로드의 메모리 문제를 없앤다.**

- `ObjectStorage` 포트 + `LocalFileObjectStorage` + `R2ObjectStorage`
- 업로드 저장 위치를 로컬 → R2로 전환 (파생본 생성은 임시 디렉터리를 거친다)
- **원본** 조회·다운로드를 presigned 302로 전환
- **파생본** 조회는 Spring이 R2에서 읽어 서빙 (작고, 기존 ETag/304 로직을 그대로 산다)
- 기존 로컬 파일 R2 백필
- `max-request-size` 등 설정 정리

**Phase 1은 업로드 크기 제한을 올리지 않는다.** 10 MB 제한이 유지되므로 `ImageIO` 경로의 메모리 문제도 경계 안에 머문다. 제한 상향은 presigned 업로드가 들어오는 Phase 3의 몫이다.

### Phase 2 — 파생본 CDN + 서명 쿠키

- CDN 배치, 파생본 고정 URL + 서명 쿠키 인증 (D7)
- `ImageSessionAuthenticationFilter`를 서명 쿠키 발급으로 이관
- 파생본 조회가 Spring을 완전히 벗어난다

### Phase 3 — presigned 업로드 + 상태머신 + 워커

- 업로드 세션 API, presigned PUT/multipart (D9)
- `FileAsset.status` 상태머신 (D5), complete 통보 + reconciliation (D10)
- 서버 검증 재설계 (D11), SVG 제거
- 워커: libvips + exiftool, 힙 밖 CLI (D8)
- 크기 제한 상향, RAW 확장자 허용

### Phase 4 — Photo-Rendition 모델 + FE 파생본 생성

- Photo 1:N Rendition 도메인 (D3, D4)
- FE WebP 변환 + feature detection + 서버 폴백 (D6)
- RAW 단독 업로드 시 임베디드 프리뷰 폴백
- HEIC 대응 (유입량 측정 후)

---

## 5. 미결정 / 후속 확인 항목

- **여러 장 일괄 다운로드(ZIP)가 필요한가.** 서버에서 묶으면 전 파일이 다시 서버를 통과해 D2의 이득이 사라진다. 필요하다면 FE에서 presigned URL을 순차 호출하는 방식으로 설계한다.
- **HEIC 실제 유입량.** iOS Safari는 사진 라이브러리에서 고를 때 HEIC를 JPEG로 자동 변환한다. 원본 HEIC는 "파일" 앱 경로로만 올라온다. Phase 3 배포 후 실측하고 Phase 4 범위를 정한다.
- **R2 버킷 리전과 CDN 구성.** 국내 사용자 기준 지연을 측정해 정한다.
