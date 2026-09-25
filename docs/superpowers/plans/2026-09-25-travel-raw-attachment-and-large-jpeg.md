# 여행 앨범 RAW 첨부 + 대용량 JPG 처리 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 여행 앨범에 카메라 JPG(20~40 MB)를 서버 메모리 걱정 없이 올리고, 같은 사진의 RAW(후지필름 `.RAF`)를 **다운로드 전용 첨부**로 붙일 수 있게 한다. 화면에는 항상 필름 시뮬레이션이 입혀진 JPG(또는 아이폰 JPEG)만 쓴다.

**Architecture:** 사진 하나는 "보기용 이미지 1장 + 선택적 RAW 첨부 1개"다. 보기용 이미지는 지금처럼 multipart로 서버를 거쳐 올라가며 파생본을 만든다. 다만 디코딩은 헤더 검증 + 축소 디코딩(subsampling)으로 바꿔 메모리를 파일 크기와 무관하게 묶는다. RAW는 서버를 거치지 않고 R2 presigned multipart로 브라우저가 직접 올린다. 서버는 업로드 세션 발급과 완료 검증(HeadObject + 앞 16바이트 매직)만 한다. RAW는 디코딩하지 않는다. 다운로드는 서명 URL을 JSON으로 돌려준다.

**Tech Stack:** Java 17, Spring Boot 3.5.x, Gradle 멀티모듈, AWS SDK v2(S3 호환 + presigner, 이미 사용 중), `com.drewnoakes:metadata-extractor`(신규, EXIF 방향), MongoDB, JUnit 5, Mockito, AssertJ

**Spec:** `docs/superpowers/specs/2026-09-03-object-storage-migration-design.md` (이하 "스토리지 설계"). 이 계획은 그 Phase 3·4를 **실제 사용 조건에 맞게 줄인 판**이다. 달라진 점은 §1에 적는다.

**작성 배경:** 2026-09-25 프론트엔드 세션에서 사용자와 확인한 조건이다.

| 항목 | 확인된 조건 |
| --- | --- |
| 카메라 | Fujifilm X100VI 한 대. 4,020만 화소(7728×5152). JPG에 필름 시뮬레이션 적용, RAW는 `.RAF` |
| JPG 크기 | 20~40 MB |
| RAW 단독 업로드 | **없다.** RAW는 항상 같은 이름의 JPG와 함께 온다 |
| RAW 사용처 | **여행 앨범만.** 임장·나들이·프로필에는 올리지 않는다 |
| RAW 용도 | **다운로드 전용.** 웹에서 RAW를 보여 주지 않는다 |
| 두 번째 사용자 | 카메라 없음. 아이폰 JPG 또는 HEIF. HEIF는 **FE가 JPEG로 변환해서** 올린다(BE는 HEIC를 받지 않는다) |

---

## 1. 스토리지 설계 대비 변경 사항

AGENTS.md "Service Scale"(커플 2인)에 따라 스토리지 설계의 무거운 부분을 걷어낸다.

| 스토리지 설계 | 이 계획 | 이유 |
| --- | --- | --- |
| D5 Photo/Rendition 상태머신 | `FileAsset.status`를 `PENDING`/`READY` 두 상태만 둔다. RAW에만 쓴다 | 보기용 이미지는 동기 업로드라 요청이 끝나면 곧 `READY`다. 비동기 상태는 RAW 직접 업로드에만 필요하다 |
| D6 FE 파생본 생성 + 서버 워커 | **하지 않는다.** 파생본은 지금처럼 업로드 요청 안에서 서버가 만든다 | 워커와 FE 인코딩 없이도 축소 디코딩으로 메모리 문제가 풀린다 |
| D8 libvips/ImageMagick CLI | **`ImageIO` + `ImageReadParam.setSourceSubsampling`** 으로 대체 | 컨테이너에 네이티브 도구를 추가하지 않는다. 40 MP JPG를 1/4로 읽으면 약 1932×1288, 10 MB 수준이다 |
| D9 크기 기준으로 PUT/multipart 분기 | RAW는 **항상 multipart**, 보기용 이미지는 **항상 서버 경유 multipart/form-data** | 분기 하나를 없앤다. RAW는 사실상 항상 32 MB 이상이다 |
| D9 IndexedDB 재개 | 범위 밖. 실패한 파트만 재시도하고, 세션이 깨지면 처음부터 다시 올린다 | 사용자 2명, 데스크톱 업로드가 주 경로다 |
| D10 스케줄러 reconciliation | `PENDING` 24시간 경과 RAW 정리 **스케줄러 1개**만 둔다 | 고아 multipart 파트 요금 방지가 목적이다 |
| D11 매직바이트 목록 | RAF 매직 `FUJIFILMCCD-RAW ` 추가 | 스토리지 설계는 CR2/ARW/NEF만 가정했다 |
| D4 페어링 | FE가 `rawFileAssetId`로 명시적으로 선언한다. 서버 휴리스틱 없음 | RAW 단독이 없으므로 보조 수단이 필요 없다 |
| D7 파생본 CDN·서명 쿠키 | 범위 밖 | 무관 |

## Global Constraints

- **보기용 이미지 API 계약(`POST /assets/file`, `POST /assets/files`, `GET /assets/files/{id}`)의 경로·파라미터·응답을 바꾸지 않는다.** 한도와 내부 처리만 바꾼다.
- **HEIC/HEIF를 허용하지 않는다.** 확장자가 `heic|heif`면 기존 `FileExtException` 대신 "HEIC는 JPG로 변환해 올려 주세요" 문구의 400을 준다. 변환은 FE 몫이다.
- **RAW는 절대 디코딩하지 않는다.** 서버가 RAW에서 읽는 바이트는 매직 확인용 앞 16바이트뿐이다.
- **RAW 바이트는 서버를 통과하지 않는다.** 업로드는 presigned multipart, 다운로드는 presigned GET이다.
- RAW `FileAsset`은 `type=TRAVEL`, `kind=RAW`다. 여행 외 타입의 RAW 세션 요청은 400이다.
- 보기용 이미지 처리에서 **원본 전체 해상도 디코딩을 하지 않는다.** 검증은 헤더만, 파생본은 축소 디코딩으로 만든다.
- 파생본 생성은 동시 **2건**으로 제한한다(`Semaphore`). 초과 요청은 대기한다.
- 모든 새 필드는 **nullable 추가**로만 한다(Mongo 문서 하위 호환). 기존 자산의 `kind`가 없으면 `IMAGE`, `status`가 없으면 `READY`로 읽는다.
- 모듈 의존 방향 `slcn-boot -> slcn-rest -> slcn-auth -> slcn-aggregate -> slcn-spec`을 지킨다.
- 들여쓰기는 탭, 주석은 한국어로 "왜"를 쓴다. 테스트 이름은 `action_shouldExpectedResult`다.
- 시작 전에 `./gradlew test`로 베이스라인(테스트 수 / 실패 0)을 기록한다. 각 태스크가 끝날 때 테스트 수는 늘기만 하고 실패는 0이어야 한다.
- `slcn.storage.provider=local`(테스트·로컬 개발)에서도 전부 동작해야 한다. RAW 세션 API는 로컬 프로바이더에서 **501 + 명확한 메시지**를 준다. presigned가 없으므로 기능을 막는 것이 맞다.

---

## 2. API 계약 (FE와 합의 대상)

### 2.1 보기용 이미지 — 변경 없음, 한도만 상향

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 파일당 한도 (`FileConstant.MAX_FILE_SIZE`, `spring.servlet.multipart.max-file-size`) | 10 MB | **50 MB** |
| 요청 전체 한도 (`max-request-size`) | 60 MB | **110 MB** |
| 허용 확장자 | `jpg|png|jpeg|gif|svg` | 그대로 (`heic|heif`는 전용 400 문구) |
| 최대 픽셀 수 | 없음 | **1억 픽셀 초과 400** (압축 폭탄 방지, 헤더로 판단) |
| `FileAssetRdo` | 기존 필드 | `kind`(`IMAGE`/`RAW`), `status`(`READY`/`PENDING`) 추가 |

FE는 요청당 누적 **100 MB 이하, 6장 이하**로 나눠 보낸다(현재 50 MB → 100 MB로 FE 상수 조정).

### 2.2 RAW 업로드 세션

**① 세션 생성** `POST /api/assets/raw-uploads` (X-AUTH-TOKEN 필수)

```json
// 요청
{ "type": "travel", "filename": "DSCF1234.RAF", "size": 83886080 }
// 201 응답
{
  "fileId": "FILE_ASSET-0123",
  "uploadId": "r2-multipart-upload-id",
  "partSize": 16777216,
  "expiresAt": "2026-09-25T13:30:00+09:00",
  "parts": [
    { "partNumber": 1, "url": "https://<account>.r2.cloudflarestorage.com/<bucket>/originals/travel/<uuid>.raf?partNumber=1&uploadId=...&X-Amz-..." }
  ]
}
```

- 검증: `type == travel`, 확장자 `raf`(대소문자 무시), `0 < size <= 500 MB`
- R2 `CreateMultipartUpload`로 `uploadId`를 받은 뒤 `FileAsset(kind=RAW, status=PENDING, uploadId=<받은 값>, size=<선언 크기>, mimeType=image/x-fujifilm-raf, originalFilename=DSCF1234.RAF)`을 저장한다. **`uploadId`는 자산에 저장해야 한다.** 취소(`DELETE`, 본문 없음)와 정리 스케줄러가 `AbortMultipartUpload`를 부를 때 이 값 말고는 얻을 곳이 없다. 자산 저장이 실패하면 방금 만든 multipart를 `abort`한다
- 이어서 모든 파트의 presigned `UploadPart` URL을 한 번에 발급한다(TTL 30분). 파트 크기는 16 MB로 고정한다(마지막 파트 제외, R2 최소 5 MB 조건 충족)

**② 완료** `POST /api/assets/raw-uploads/{fileId}/complete`

```json
// 요청 — ETag는 브라우저가 각 PUT 응답 헤더에서 읽는다(R2 CORS ExposeHeaders: ETag 필요)
{ "uploadId": "...", "parts": [ { "partNumber": 1, "etag": "\"abc...\"" } ] }
// 200 응답: FileAssetRdo (kind=RAW, status=READY)
```

- 요청의 `uploadId`가 자산에 저장된 `uploadId`와 다르면 400이다(다른 세션의 파트로 완료시키는 것을 막는다)
- `CompleteMultipartUpload` → `HeadObject`로 크기 == 세션 생성 때 선언한 `size`인지 확인 → Range GET `bytes=0-15`가 `FUJIFILMCCD-RAW `(끝 공백 포함 16바이트)인지 확인
- 하나라도 틀리면 객체와 자산을 삭제하고 400을 준다
- 이미 `READY`면 같은 응답을 다시 준다(멱등)

**③ 취소·삭제** `DELETE /api/assets/raw-uploads/{fileId}` → 204

- `PENDING`: 자산에 저장된 `uploadId`로 `AbortMultipartUpload` + 자산 삭제
- `READY`이고 **어떤 FileBox 항목에도 연결되지 않았으면**(`items.rawFileAssetId`로 한 번 조회): R2 객체 삭제 + 자산 삭제. 여러 RAW 중 일부만 완료된 뒤 사용자가 저장을 포기하거나 RAW를 목록에서 빼는 흐름이 흔하다. 이때 409로 막으면 장당 수십 MB 고아가 남는다
- `READY`이고 여행에 연결되어 있으면 409(`RAW_UPLOAD_IN_USE`). 연결을 끊으려면 여행 수정으로 `rawFileAssetId`를 빼고, 그 뒤에 삭제한다
- 순서는 "연결 여부 확인 → 객체 삭제 → 자산 삭제"다. 확인과 삭제 사이에 여행 저장이 끼어드는 경합은 사용자 2명 규모에서 사실상 없고, 생기더라도 여행 상세의 `rawFile`이 비는 것(다운로드 버튼만 사라짐)으로 끝나므로 락을 두지 않는다

### 2.3 다운로드 URL

`GET /api/assets/files/{fileId}/download-url` (X-AUTH-TOKEN 필수, 쿠키 인증 불가)

```json
{ "url": "https://...r2...?response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%27DSCF1234.RAF&X-Amz-...", "filename": "DSCF1234.RAF", "size": 83886080, "expiresAt": "..." }
```

- RAW와 보기용 이미지 원본 모두 쓸 수 있다. 기존 `presignedGetUrl(key, ttl, contentDisposition)`을 재사용한다
- FE는 `location.href = url`로 받는다. 토큰이 R2로 가지 않고, `/download` 302를 fetch로 따라가는 CORS 문제도 없다
- 로컬 프로바이더에서는 **501**(`PRESIGNED_URL_NOT_SUPPORTED`, RAW 세션 API와 같은 코드)을 준다. FE와 합의했다(2026-09-25). 로컬에서는 RAW 업로드 세션 자체가 501이라 RAW 자산이 생길 수 없으므로 fetch+Blob 대체 경로가 필요 없다
- 기존 `GET /download`는 그대로 둔다(하위 호환)
- `GET /assets/files/{rawFileId}`(인라인 조회)는 RAW면 **404**를 준다. `<img>`에 RAW가 물려 100 MB가 내려가는 사고를 막는다
- 경로 기반 조회 `GET /assets/file?type=travel&filename={uuid}.raf`도 **거부한다.** 이 경로는 쿠키 인증·캐시 허용 대상이고(`AssetRequestMatchers`), Task 6에서 `STORED_EXT`에 `raf`를 넣으면 `FileLogic.getImageFile`이 RAW를 presigned 리다이렉트로 내보내 위 404 규칙을 우회한다. 확장자가 `raf`면 `FilePathInvalidException`(400)으로 막는다
- 404는 새 `ErrorCode`가 필요하다. 기존 `FILE_ASSET_NOT_FOUND`는 **400**이다(`ErrorCode.java`)

### 2.4 여행 사진과 RAW 연결

`FileBoxItem`, `FileBoxItemCdo`, `FileBoxItemUdo`, `FileBoxItemRdo`에 `rawFileAssetId`(nullable)를 추가한다. `FileBoxItemRdo`에는 다운로드 버튼용 `rawFile: FileAssetRdo`(`originalFilename`, `size`)도 채운다.

```json
// 여행 등록·수정 files 항목
{ "fileAssetId": "FILE_ASSET-0100", "rawFileAssetId": "FILE_ASSET-0123", "targetType": "TRAVEL", "targetId": null, "role": "GALLERY", "caption": null }
```

`TravelLogic.validateTravelFiles`에 추가할 검증:
- `fileAssetId`의 자산은 `kind == IMAGE`여야 한다(RAW를 보기용으로 연결하는 것을 막는다)
- `rawFileAssetId`가 있으면: 자산이 존재하고, `type == TRAVEL`, `kind == RAW`, `status == READY`여야 한다
- 한 여행 안에서 `rawFileAssetId`와 `fileAssetId`는 **1:1로 대응**해야 한다. 같은 RAW가 서로 다른 보기용 사진에 붙거나, 같은 보기용 사진이 항목마다 다른 RAW를 가지면 거부한다. 같은 RAW가 여러 항목에 나오는 것 자체는 허용한다. 현재 중복 키(`targetType|targetId|role|fileAssetId`)는 같은 사진을 COVER와 GALLERY에 동시에 두는 것을 허용하므로, "한 번만" 규칙으로 하면 이 정상 요청이 400이 된다
- 위반 시 400이다(문구는 태스크 7 참고)

임장(`InspectionPhotoSupport`)은 `rawFileAssetId`가 들어오면 400을 준다.

---

## File Structure

### Create
- `slcn-spec/.../file/entity/vo/FileKind.java`: `IMAGE`, `RAW`
- `slcn-spec/.../file/entity/vo/FileStatus.java`: `PENDING`, `READY`
- `slcn-spec/.../file/facade/sdo/RawUploadSessionCdo.java`, `RawUploadSessionRdo.java`, `RawUploadCompleteCdo.java`, `DownloadUrlRdo.java`
- `slcn-aggregate/.../file/util/ImageInspector.java`: 헤더 검증, 축소 디코딩, EXIF 방향, ICC→sRGB. `FileUtils`에서 분리해 800줄 규칙과 테스트 용이성을 지킨다
- `slcn-aggregate/.../file/logic/RawUploadLogic.java`: 세션 생성·완료·취소
- `slcn-aggregate/.../file/logic/RawUploadCleanupScheduler.java`: `PENDING` 24시간 경과분 정리
- `slcn-aggregate/.../file/storage/MultipartUploadStorage.java`: create/presignPart/complete/abort/headSize/readRange 포트. `ObjectStorage`와 분리해 로컬 어댑터가 구현하지 않아도 되게 한다
- `slcn-aggregate/.../file/storage/R2MultipartUploadStorage.java`
- `slcn-rest/.../file/RawUploadResource.java`
- `slcn-aggregate/.../file/exception/`: RAW 인라인 조회 404, 연결된 RAW 삭제 409, 로컬 프로바이더 501(RAW 세션·`download-url` 공용)용 예외 3개(기존 `FileAssetNotFoundException` 관례를 따른다)
- 스케줄링 활성화 설정(`@EnableScheduling`). **현재 프로젝트에는 스케줄링이 켜져 있지 않다.** `slcn-boot` 설정 클래스에 두거나 `slcn-aggregate/.../config/`에 전용 설정을 둔다
- 각 클래스의 `*Test`

### Modify
- `slcn-spec/.../file/constant/FileConstant.java`: `MAX_FILE_SIZE` 50 MB, `MAX_RAW_FILE_SIZE` 500 MB, `MAX_IMAGE_PIXELS` 100_000_000, `RAF_MAGIC`, HEIC 안내 문구
- `slcn-spec/.../file/entity/FileAsset.java`: `kind`, `status`(getter에서 null → `IMAGE`/`READY`), `uploadId`(RAW `PENDING` 동안만 쓰는 R2 multipart id, nullable)
- `slcn-spec/.../common/exception/ErrorCode.java`: `FILE_ASSET_RAW_NOT_VIEWABLE(404)`, `RAW_UPLOAD_IN_USE(409)`, `PRESIGNED_URL_NOT_SUPPORTED(501)`. 501은 RAW 세션 API와 `download-url`이 함께 쓴다. 둘 다 "로컬 프로바이더라 서명 URL을 만들 수 없다"는 같은 원인이다. 기존 `FILE_ASSET_NOT_FOUND`는 400이라 재사용할 수 없다
- `slcn-spec/.../file/facade/FileFacade.java`: `download-url` 메서드 추가(`FileResource`가 구현한다)
- `slcn-aggregate/.../file/store/FileAssetStore.java`·`FileAssetRepository.java`: `deleteById`, `findByKindAndStatusAndRegisteredTimeLessThan`(정리 스케줄러용)
- `slcn-aggregate/.../filebox/store/FileBoxStore.java`·`FileBoxRepository.java`: `existsByItemsRawFileAssetId(rawFileAssetId)`(연결되지 않은 `READY` RAW 삭제 판단용)
- `slcn-spec/.../file/facade/sdo/FileAssetRdo.java`: `kind`, `status`
- `slcn-spec/.../filebox/entity/vo/FileBoxItem.java` 외 Cdo/Udo/Rdo와 `FileBoxMapper`: `rawFileAssetId`, `rawFile`
- `slcn-aggregate/.../file/store/doc/FileAssetDoc.java`, `filebox/store/doc/FileBoxDoc.java`(해당 시) 및 매퍼
- `slcn-aggregate/.../file/util/FileUtils.java`: `validateImageFile`·`writeVariants`의 전체 디코딩을 `ImageInspector`로 교체
- `slcn-aggregate/.../file/logic/FileLogic.java`: 파생본 생성 `Semaphore(2)`, RAW 인라인 조회 404, `getDownloadUrl`
- `slcn-aggregate/.../travel/logic/TravelLogic.java`: §2.4 검증
- `slcn-aggregate/.../flow/inspection/InspectionPhotoSupport.java`: `rawFileAssetId` 거부
- `slcn-aggregate/.../config/ObjectStorageConfiguration.java`: **먼저 리팩터링한다.** 지금은 `S3Client`/`S3Presigner`를 `objectStorage()` 메서드 안에서 만들어 바로 버린다. 둘을 `slcn.storage.provider=r2`일 때만 만들어지는 별도 빈(`@ConditionalOnProperty`)으로 꺼내고, `R2ObjectStorage`와 `R2MultipartUploadStorage`가 같은 클라이언트를 주입받게 한다. 로컬이면 `MultipartUploadStorage` 빈이 없고, `RawUploadLogic`은 `Optional`로 받는다
- `slcn-rest/.../file/FileResource.java`: `GET /files/{id}/download-url`
- `slcn-auth/.../matcher/AssetRequestMatchers.java`: `/download-url`을 쿠키 인증 대상에서 제외, 캐시 허용 대상에서도 제외
- `slcn-aggregate/build.gradle`: `metadata-extractor`
- `slcn-boot/src/main/resources/application.yml`: multipart 한도, `slcn.storage.raw.*`(part-size, session-ttl, cleanup-cron)

---

## Tasks

### Task 1. `FileKind`·`FileStatus`와 자산 필드 추가
- [x] `FileKind`, `FileStatus` enum 추가. JSON 값은 `FileBoxItemRole`처럼 상수 이름 그대로(대문자) 직렬화한다
- [x] `FileAsset`에 `kind`, `status`, `uploadId` 추가. getter는 null이면 `IMAGE`/`READY`
- [x] `FileAssetDoc`·매퍼에 세 필드 모두, `FileAssetRdo`에는 `kind`·`status`만 반영(`uploadId`는 응답에 싣지 않는다)
- [x] 테스트: `getKind_shouldDefaultToImageWhenMissing`, `getStatus_shouldDefaultToReadyWhenMissing`, `uploadId` 포함 Doc 왕복 매핑

### Task 2. `ImageInspector` — 헤더 검증과 축소 디코딩
- [x] `inspect(Path)`: `ImageIO.getImageReaders(ImageInputStream)`로 reader를 얻고 `getWidth(0)`/`getHeight(0)`만 읽는다(픽셀 디코딩 없음). reader가 없으면 `FileExtException`, 픽셀 수가 `MAX_IMAGE_PIXELS`를 넘으면 400
- [x] `readScaled(Path, int minWidth)`: 서브샘플 계수를 **너비와 픽셀 예산 중 큰 쪽**으로 정해 `ImageReadParam.setSourceSubsampling(n, n, 0, 0)` 후 디코딩한다.
  - `nByWidth = max(1, floor(width / (minWidth * 2)))`: `minWidth`는 가장 큰 파생본 너비(960)다. 일반 사진은 결과 너비가 1920 이상이 되어 기존 절반씩 축소 품질을 유지한다
  - `nByPixels = max(1, ceil(sqrt(width * height / DECODE_PIXEL_BUDGET)))`: `DECODE_PIXEL_BUDGET = 4_000_000`(약 16 MB `INT_RGB`)
  - `n = max(nByWidth, nByPixels)`
  - 너비만 보면 세로로 긴 이미지(예: 2000×50000 PNG, 1억 픽셀 한도 이하)가 n=1로 전체 디코딩되어 약 400 MB를 쓴다. 픽셀 예산 조건이 이를 막아 메모리를 파일 크기와 무관하게 묶는다. 7728×5152 JPG는 두 조건 모두 n=4다
- [x] 구현 메모: 업로드 검증은 임시 파일로 옮기기 전 `MultipartFile` 스트림에서 하므로 `inspect(InputStream)` 오버로드를 함께 두었다. 픽셀 한도 초과는 `BadRequestException("이미지 해상도가 너무 큽니다.")`다. 파생본 생성 여부(`width <= variant.width`면 건너뜀)는 축소본이 아니라 원본 헤더 너비로 판단한다
- [x] SVG는 지금처럼 `validateSvg` 경로를 유지한다(이 계획은 SVG 정책을 바꾸지 않는다)
- [x] `FileUtils.validateImageFile`에서 `ImageIO.read(inputStream)`을 `inspect`로 교체
- [x] `FileUtils.writeVariants`에서 `ImageIO.read(path)`를 `readScaled`로 교체. `ImageProfile`의 width/height는 **원본 헤더 값**을 쓴다(축소본 크기가 아님). EXIF 방향 보정은 Task 3에서 더한다
- [x] 테스트: 7728×5152 합성 JPEG(테스트 리소스로 생성, 단색이면 수백 KB)로 `readScaled` 결과 너비가 1932인지 확인. 2000×50000 합성 PNG(단색)가 픽셀 예산 조건으로 n≥5로 읽히는지 확인. 1억 픽셀 초과 헤더를 가진 PNG를 거부하는지 확인(헤더만 조작한 파일로, 실제로 크게 만들지 않는다)

### Task 3. EXIF 방향과 색 프로필
- [x] `metadata-extractor`로 `ExifIFD0Directory.TAG_ORIENTATION`을 읽는다. 값 2~8이면 축소 디코딩 결과에 `AffineTransform`을 적용한다. 파생본은 항상 **똑바로 선 상태**로 저장한다(파생본에는 EXIF를 쓰지 않으므로 브라우저가 다시 돌리지 않는다)
- [x] orientation이 5~8(90°/270° 회전 계열)이면 `ImageProfile`의 width/height를 **바꿔서** 저장한다. 헤더는 가로(7728×5152)인데 파생본과 브라우저가 보여 주는 원본은 세로다. FE는 `FileAssetRdo.width/height`로 레이아웃을 미리 잡으므로(`FileAssetRdo` 주석) 값을 바꾸지 않으면 비율이 뒤집힌다. 즉 `width/height`의 의미는 "헤더 값"이 아니라 "**화면에 보이는 방향의 원본 크기**"다
- [x] ~~JPEG APP2 `ICC_PROFILE` 세그먼트를 직접 파싱해 `ColorConvertOp`로 sRGB 변환~~ → **하지 않는다.** 구현 전에 확인해 보니 JDK `JPEGImageReader`가 내장 ICC 프로필을 디코딩 단계에서 sRGB로 변환하고, 축소 디코딩(`setSourceSubsampling`) 경로도 같다(선형 RGB 프로필 JPEG의 50이 약 122로 읽힘). 대신 이 전제를 지키는 회귀 테스트를 둔다(`readScaled_shouldConvertEmbeddedNonSrgbProfileToSrgb`)
- [x] 적용 순서: 축소 디코딩(색 변환은 JDK가 이 단계에서 함) → 회전 → 기존 `scaleToWidth`. 회전은 `ImageOrientation`으로 분리했다. `ScaledImage`의 크기 필드는 `displayWidth`/`displayHeight`(화면 방향 기준 원본 크기)이고, 파생본 생성 여부도 이 값으로 판단한다
- [x] 테스트: orientation 6(시계 90°) 태그를 붙인 가로 JPEG의 파생본이 세로가 되고, 저장된 width/height도 세로(width < height)인지. 비 sRGB 프로필 JPEG가 sRGB로 변환되는지(JDK에 Display P3 프로필이 없어 선형 RGB 프로필로 대신한다). 방향 1~8 전부의 픽셀 위치 검증
- [x] **FE 참고:** 원본 `<img>`는 브라우저가 EXIF로 돌리므로 영향이 없다. 이 태스크 전에 만들어진 기존 파생본은 여전히 누워 있을 수 있다. 재생성 여부는 §5 미결정 항목이다

### Task 4. 한도 상향과 동시성 제한
- [x] `FileConstant.MAX_FILE_SIZE = 50 MB`, `application.yml` `max-file-size: 50MB`, `max-request-size: 110MB`
- [x] `FileLogic`의 파생본 생성 구간을 `Semaphore(2)`로 감싼다. 대기는 무기한이 아니라 60초 `tryAcquire`로 하고, 실패하면 파생본 없이 업로드를 성공시킨다(`variants: []`, 조회 시 원본 폴백은 기존 동작)
- [x] 확장자 `heic|heif`면 전용 400 문구
- [x] 테스트: 50 MB 초과 거부, HEIC 문구, 세마포어 획득 실패 시 `variants`가 빈 채로 저장되는지
- [x] 구현 메모: 세마포어 획득에 실패하면 파생본은 건너뛰지만 가로·세로는 `FileUtils.readProfile`(헤더+EXIF만 읽음)로 기록한다. 대기 시간은 `slcn.upload.variant-wait-seconds`(기본 60초)로 뺐다. HEIC는 `FileExtException`(FILE_EXT_INVALID) 코드에 "HEIC 사진은 JPG로 변환해 올려 주세요." 문구다

### Task 5. `MultipartUploadStorage`와 R2 어댑터
- [ ] **선행 리팩터링:** `ObjectStorageConfiguration`에서 `S3Client`/`S3Presigner` 생성을 r2 전용 빈으로 분리한다(File Structure 참고). 동작 변화가 없어야 하므로 기존 `R2ObjectStorageTest`·설정 테스트가 그대로 통과하는지 먼저 확인하고 다음 단계로 간다
- [ ] 포트: `create(key, contentType) → uploadId`, `presignPart(key, uploadId, partNumber, ttl) → url`, `complete(key, uploadId, parts)`, `abort(key, uploadId)`, `headSize(key) → long`, `readRange(key, 0, 15) → byte[]`
- [ ] R2 구현: `CreateMultipartUpload`, `S3Presigner.presignUploadPart`, `CompleteMultipartUpload`, `AbortMultipartUpload`, `HeadObject`, `GetObject(range="bytes=0-15")`. SDK 예외는 `IOException`으로 감싼다(`R2ObjectStorage` 관례)
- [ ] 로컬 프로바이더면 빈을 만들지 않는다. `RawUploadLogic`은 `Optional`로 받고, 비어 있으면 501을 던진다(`PRESIGNED_URL_NOT_SUPPORTED`)
- [ ] 테스트: provider=local이면 `MultipartUploadStorage` 빈이 없고 provider=r2면 `ObjectStorage`와 같은 `S3Client`를 쓰는지. `S3Client`/`S3Presigner` 목으로 요청 파라미터(bucket, key, uploadId, partNumber, range) 검증

### Task 6. RAW 업로드 세션 API
- [ ] `RawUploadLogic.createSession(cdo)`: §2.2 ① 검증 → multipart 생성(key는 `ObjectKeys.original("travel", "{uuid}.raf")`) → 자산 저장(`PENDING`, `storedFilename = {uuid}.raf`, `uploadId` 포함. 저장 실패 시 `abort`) → 파트 수 `ceil(size / partSize)`만큼 presign
- [ ] `complete(fileId, cdo)`: 요청 `uploadId`와 저장된 `uploadId`가 같은지 먼저 확인(다르면 400). 이어서 §2.2 ② 순서대로. 매직 불일치·크기 불일치면 `abort`/객체 삭제 후 자산 삭제, 400
- [ ] `delete(fileId)`: §2.2 ③. `PENDING`이면 저장된 `uploadId`로 `abort` 후 자산 삭제. `READY`이면 `FileBoxStore.existsByItemsRawFileAssetId`로 연결 여부를 보고, 연결되지 않았으면 R2 객체 삭제 후 자산 삭제, 연결되어 있으면 409(`RAW_UPLOAD_IN_USE`). `kind != RAW`인 자산은 400이다(이 API로 보기용 이미지를 지우지 못하게 한다)
- [ ] `MultipartUploadStorage`에 `delete(key)`를 추가한다(`READY` RAW 객체 삭제용)
- [ ] `RawUploadResource`: `POST /assets/raw-uploads`(201), `POST /assets/raw-uploads/{fileId}/complete`(200), `DELETE /assets/raw-uploads/{fileId}`(204)
- [ ] `FileConstant.EXT_REGEX_STRING`은 **바꾸지 않는다.** RAF는 이 세션 API로만 들어온다(기존 multipart 업로드로 RAF가 오면 지금처럼 거절)
- [ ] `STORED_EXT_REGEX_STRING`에 `raf`를 추가해 경로 검증이 저장 파일명을 거부하지 않게 한다(`readOriginal`의 `isValidFileRef` 통과용). **이 변경은 Task 8의 경로 기반 조회 차단과 같은 커밋에 넣는다.** 따로 들어가면 그 사이에 `GET /assets/file`로 RAW가 열린다
- [ ] 테스트: 정상 흐름, `uploadId` 불일치 400, 취소 시 저장된 `uploadId`로 `abort` 호출, `type=inspection` 거부, 확장자 거부, 크기 초과, 매직 불일치 시 정리, 크기 불일치 시 정리, 완료 멱등, 연결되지 않은 `READY` 삭제 204(객체 삭제 호출), 연결된 `READY` 삭제 409, `IMAGE` 자산 삭제 요청 400, 로컬 프로바이더 501

### Task 7. 여행 사진과 RAW 연결
- [ ] `FileBoxItem`·Cdo·Udo·Rdo·`FileBoxMapper`·`FileBoxDoc`(해당 시)에 `rawFileAssetId` 추가. Rdo에 `rawFile` 채우기(여행 상세 조회 흐름에서 자산 일괄 조회에 포함)
- [ ] `TravelLogic.validateTravelFiles`: §2.4 규칙. 문구는 "RAW 파일은 보기용 사진으로 연결할 수 없습니다.", "연결할 RAW 파일이 올바르지 않습니다.", "RAW 업로드가 아직 끝나지 않았습니다.", "하나의 사진에는 하나의 RAW 파일만 연결할 수 있습니다."(1:1 대응 위반)
- [ ] 수정 시 "`id`를 보낸 기존 항목 유지" 규칙에 `rawFileAssetId`도 포함한다. 보내지 않으면 null로 바뀐다(전체 치환 규칙과 같은 의미)
- [ ] `InspectionPhotoSupport`: `rawFileAssetId`가 있으면 400
- [ ] 테스트: 각 규칙 1개씩. 같은 사진+같은 RAW를 COVER와 GALLERY에 함께 두면 **통과**, 같은 RAW를 다른 사진에 붙이면 거부, 같은 사진에 다른 RAW를 붙이면 거부. 여행 상세 응답에 `rawFile`이 채워지는지, 임장 거부

### Task 8. 다운로드 URL과 RAW 인라인 조회 차단
- [ ] `FileLogic.getDownloadUrl(fileId)`: 원본 키 presign(TTL 300초, `ContentDisposition.attachment().filename(originalFilename, UTF_8)`). 로컬 프로바이더면 501(§2.3)
- [ ] `FileResource`: `GET /files/{fileId}/download-url`
- [ ] `AssetRequestMatchers.IMAGE_READ_MATCHER`·`CACHEABLE_IMAGE_MATCHER`에서 `/download-url` 제외. 기존 `/download` 제외 로직과 같은 방식
- [ ] `FileFacade`에 `download-url` 메서드를 추가하고 `FileResource`가 구현한다
- [ ] `getImageFileById`: `kind == RAW`면 404(`FILE_ASSET_RAW_NOT_VIEWABLE`. 기존 `FILE_ASSET_NOT_FOUND`는 400이므로 새 코드를 쓴다). 현재 `FileResource`는 ETag를 먼저 비교하므로 `If-None-Match`가 맞으면 304가 나갈 수 있다. RAW의 ETag를 가진 클라이언트는 없으므로 무방하다
- [ ] `getImageFile`(경로 기반 조회): 파일명 확장자가 `raf`면 `FilePathInvalidException`(400). Task 6의 `STORED_EXT` 변경과 같은 커밋이다
- [ ] 테스트: 한글 파일명 `filename*=UTF-8''` 인코딩, 로컬 프로바이더 `/download-url` 501, 쿠키만으로 `/download-url` 401, RAW 인라인 404, 경로 기반 `{uuid}.raf` 조회 400(스토리지 호출 없음)

### Task 9. 고아 RAW 정리
- [ ] `@EnableScheduling`을 추가한다(현재 프로젝트 어디에도 없다. 없으면 `@Scheduled`가 조용히 돌지 않는다). 테스트 컨텍스트에서 스케줄러가 실제로 돌지 않게 cron을 설정값(`slcn.storage.raw.cleanup-cron`)으로 빼고 테스트에서는 `-`(비활성)로 둔다
- [ ] `FileAssetStore`에 `deleteById`와 `findByKindAndStatusAndRegisteredTimeLessThan`을 추가한다. 시간 필드는 `DomainEntity.registeredTime`(epoch millis)이다. `createdTime` 필드는 없다
- [ ] `RawUploadCleanupScheduler`: 매일 1회. `kind=RAW, status=PENDING, registeredTime < now-24h`를 조회해 저장된 `uploadId`로 `abort` + 자산 삭제. R2 실패는 로그만 남기고 다음 실행 때 다시 시도한다
- [ ] 어떤 여행에도 연결되지 않은 `READY` RAW는 **정리하지 않는다**(기존 이미지 고아 정책과 같다. 규모상 수동 확인이 낫다)
- [ ] 테스트: 경과분만 정리, 실패해도 다음 항목 계속

### Task 10. 문서
- [ ] `docs/file-asset.md`, `docs/image-asset-api.md`에 §2 계약 반영
- [ ] 스토리지 설계 문서 §4에 "Phase 3·4는 2026-09-25 계획으로 축소 구현"을 한 줄 링크로 남긴다
- [ ] `docs/prompts/`에 FE용 변경 요약(§2 + §4)을 남긴다

---

## 3. 인프라 변경 (`slcn_deploy`, 배포 담당)

FE 세션에서 `slcn_deploy`의 `fix/upload-limit-and-r2-env` 브랜치에 1차 변경이 있다(`/api` 60M, compose에 R2 환경 변수 전달). 이 계획 기준으로 아래와 같이 갱신한다.

- [ ] nginx `location /api/`: `client_max_body_size 110M;`, `proxy_read_timeout 180s;`, `proxy_send_timeout 180s;`
- [ ] Jenkins: R2 자격 증명 등록 후 `SLCN_STORAGE_PROVIDER=r2`, `SLCN_R2_*` 주입. **이것 없이는 운영이 local 프로바이더로 떠서 RAW 기능이 501이다**
- [ ] R2 버킷 CORS. 앞선 검증 보고서의 "GET만" 권장을 **아래로 대체한다**

```json
[
  {
    "AllowedOrigins": ["https://slcn.duckdns.org"],
    "AllowedMethods": ["GET", "HEAD", "PUT"],
    "AllowedHeaders": ["content-type"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

  `X-AUTH-TOKEN`은 허용하지 않는다(토큰이 R2로 가지 않게 한다). 개발용 origin(`http://localhost:5173`)은 R2 스테이징 버킷에만 둔다.
- [ ] R2 라이프사이클: `AbortIncompleteMultipartUpload` 7일(스토리지 설계 D9)
- [ ] JVM: 컨테이너 메모리 한도와 `-Xmx`를 명시한다(예: 한도 1.5 GB, `-Xmx1g`). 호스트 RAM을 확인한 뒤 정한다

## 4. FE 변경 요약 (FE 저장소에서 별도 진행)

2026-09-25 FE 세션에서 이 계획을 FE 코드와 대조해 보완했다. 근거는 FE 코드 기준이다.

### 4.1 선행 수정 — 여행 수정 시 사진 목록 전체 전송 (기존 버그, RAW와 무관하게 먼저 한다)

- 백엔드는 여행 수정 요청에 `files`가 있으면 그 목록으로 **통째로 치환**한다(`TravelLogic.resolveModifyItems`). 그런데 FE는 새 사진이 있을 때 **새로 올린 사진만** 보낸다(`slcnfront` `TravelRegisterSection.tsx` 제출부, `mappers/travel-payload.ts`).
  - 수정 중 앨범 사진을 추가하면 기존 사진(날짜·장소 사진 포함)이 모두 사라진다
  - 표지 없이 앨범만 추가하면 COVER가 없어 400("여행 대표 이미지는 1개여야 합니다.")이다
- FE는 수정 시 **기존 항목**(`id`, `fileAssetId`, `rawFileAssetId`, `role`, `targetType`, `targetId`, `caption`, `sortOrder`)에 **새 항목**을 더한 전체 목록을 보낸다. 새 사진이 없으면 지금처럼 `files`를 생략한다
- `rawFileAssetId`를 빠뜨리면 백엔드는 null로 바꾼다(Task 7). 기존 항목의 RAW 연결을 유지하려면 이 수정이 전제다

### 4.2 업로드 한도·HEIC·표지

- 업로드 묶음 기준을 요청당 100 MB / 6장으로, 파일당 검증을 50 MB로 바꾼다
- HEIC가 들어오면 브라우저에서 JPEG로 변환(WASM 변환기 지연 로딩) 후 기존 경로로 올린다
- 원본을 쓰는 여행 상세 표지를 `home-feature` 축소본으로 바꾼다

### 4.3 RAW 짝짓기와 1:1 규칙

- 여행 앨범 파일 선택에서 `.RAF`를 허용하고, 파일명 앞부분(stem)으로 JPG와 RAF를 짝짓는다. 짝 없는 RAF는 경고 표시 후 제외한다
- RAF 짝짓기는 **앨범 칸에서만** 한다. 표지 칸은 JPG 한 장만 받는다
- **같은 `File`은 한 번만 업로드한다.** 표지와 앨범에 같은 파일(이름·크기·`lastModified` 일치)이 있으면 JPG 자산 하나를 COVER와 GALLERY 항목에 같이 쓰고, 표지는 앨범 쪽 RAW 연결을 이어받는다. 따로 올리면 JPG 자산이 둘이 되어 같은 RAW가 두 보기용 사진에 붙으므로 §2.4 1:1 규칙에 걸려 400이다

### 4.4 RAW 업로드 (§2.2)

- RAF는 세션으로 R2에 직접 올린다. 파트는 `file.slice()`를 **타입 없이** PUT한다(R2 CORS `AllowedHeaders`가 `content-type`뿐이다). 실패 파트는 3회까지 재시도하고 진행률을 표시한다
- 파트 URL은 세션 생성 때 한꺼번에 받고 **30분 뒤 만료**된다. 만료(R2 403)면 세션을 `DELETE`로 취소하고 새 세션으로 처음부터 다시 올린다
- 완료 요청에는 세션에서 받은 `uploadId`와 각 PUT 응답의 `ETag`를 보낸다
- 업로드 실패, 사용자가 RAW를 목록에서 뺀 경우, 저장을 포기한 경우 `DELETE /assets/raw-uploads/{fileId}`로 지운다. `PENDING`과 아직 여행에 연결되지 않은 `READY` 모두 204다. 이미 여행에 저장된 RAW는 409이므로, 저장된 여행에서 RAW를 빼는 것은 여행 수정(`rawFileAssetId` 제거)으로 한다
- 여행 저장은 모든 RAW가 `READY`가 된 뒤에 한다(`PENDING` 연결은 400이다)
- 로컬 개발(local 프로바이더)에서는 RAW API가 501이다. FE는 "로컬 환경에서는 RAW를 올릴 수 없어요" 안내를 보여 주고, 기능 검증은 MSW 모의 테스트와 R2 스테이징에서 한다

### 4.5 스키마·다운로드

- `TravelFileBoxItemCdo`에 `id`(기존 항목 유지용), `rawFileAssetId`를 추가한다
- 응답 스키마 `fileBoxItemRdoSchema`에 `rawFileAssetId`, `rawFile`(`originalFilename`, `size`)을 추가한다
- 여행 상세에서 `rawFile`이 있는 사진에 "RAW 다운로드" 버튼을 두고 `download-url`로 받는다(`location.href = url`)
- `FileAssetRdo.width/height`는 이제 화면에 보이는 방향 기준이다(Task 3). FE는 현재 이 값을 쓰지 않으므로 변경은 없고, 이후 비율 선점 레이아웃에 쓸 수 있다

## 5. 미결정 / 확인 필요

- ~~로컬 프로바이더의 `download-url` 응답 형태~~ → **501로 결정**(2026-09-25 FE 합의, §2.3)
- **기존 파생본 재생성.** Task 3 이전에 만들어진 세로 사진의 파생본은 누워 있을 수 있다. 규모가 작으므로 영향받는 자산을 직접 조회해 일회성으로 재생성할지 결정한다(AGENTS.md 원칙: 대량 백필보다 직접 조회·처리)
- **X100VI RAF 실제 크기.** 무손실 압축과 비압축에 따라 다르다. 500 MB 상한과 16 MB 파트 크기가 충분한지 실제 파일로 확인한다
- **호스트 RAM.** JVM 설정 값을 정하려면 필요하다
- **여행 삭제·사진 제거 시 R2 객체 삭제.** 현재 이미지도 지우지 않는다(고아 허용). RAW는 장당 수십 MB라 저장 비용이 누적된다. 별도 과제로 다룰지 정한다
