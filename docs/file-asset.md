# FileAsset 파일 업로드

## 개요

파일 업로드는 실제 이미지 파일을 오브젝트 스토리지에 저장하고, 파일 메타데이터는 MongoDB `file_asset` 컬렉션에 `FileAsset`으로 저장한다.

나들이/여행 API는 파일 자체를 받지 않고 `FileAsset.id`를 이미지 참조값으로 사용한다. 도메인과 파일의 연결은 MongoDB `file_box` 컬렉션이 담당한다.

## API

모든 경로는 context path `/api` 하위이며 컨트롤러 prefix는 `assets`다. 인증은 `X-AUTH-TOKEN` 헤더를 요구한다.
예외로 이미지 조회 두 개(`GET /assets/files/{fileId}`, `GET /assets/file`)는 `sessionId` 쿠키로도 인증한다. `img` 태그가 요청 헤더를 붙일 수 없기 때문이며, 자세한 내용은 `docs/image-asset-api.md`를 본다.

- `POST /api/assets/file`
  - 단건 업로드 API다.
  - `multipart/form-data`
  - `file`: 이미지 파일
  - `type`: `logo`, `map`, `travel`, `profile`, `inspection`
  - 실제 파일을 저장하고 MongoDB에 `FileAsset`을 생성한 뒤 `FileAssetRdo`를 반환한다.
- `POST /api/assets/files`
  - 다중 업로드 API다.
  - `files`: 여러 이미지 파일
  - `type`: 단건과 동일
  - 응답은 `FileAssetRdo` 목록이다.
- `GET /api/assets/files/{fileId}`
  - `FileAsset.id`로 실제 파일 바이트를 조회한다.
  - `variant`, `width`로 축소본을 요청할 수 있고, 파라미터가 없으면 원본을 반환한다.
- `GET /api/assets/files/{fileId}/download`
  - 조회와 같은 바이트를 `Content-Disposition: attachment`로 반환한다.
- `GET /api/assets/files/{fileId}/download-url`
  - 원본(RAW 첨부 포함)을 첨부 파일로 받을 서명 URL을 JSON(`url`, `filename`, `size`, `expiresAt`)으로 반환한다.
  - 쿠키 인증과 브라우저 캐시 대상이 아니다. 로컬 프로바이더에서는 `501`이다.
- `POST /api/assets/raw-uploads`, `POST /api/assets/raw-uploads/{fileId}/complete`, `DELETE /api/assets/raw-uploads/{fileId}`
  - 여행 사진의 RAW 첨부를 브라우저가 저장소에 직접 올리는 세션 API다. 아래 "RAW 첨부"를 본다.
- `GET /api/assets/file?type={type}&filename={filename}`
  - 경로 기반 파일 조회 API다. 축소본을 타지 않고 항상 해당 파일을 그대로 반환한다.
  - `.raf`(RAW 첨부)는 `400`으로 거부한다.

FE 연동 규칙(축소본 선택, 캐시, 구 자산 처리)은 `docs/image-asset-api.md`를 기준으로 한다.

### 업로드 제약

- 파일당 최대 50 MB (`FileConstant.MAX_FILE_SIZE`, `spring.servlet.multipart.max-file-size`). 카메라 JPG(20~40 MB)를 받기 위한 값이다
- 다중 업로드는 요청 전체 110 MB까지다 (`spring.servlet.multipart.max-request-size`). FE는 요청당 100 MB·6장 이하로 나눠 보낸다
- 확장자는 `jpg`, `jpeg`, `png`, `gif`, `svg`
- `heic`, `heif`는 "HEIC 사진은 JPG로 변환해 올려 주세요." 문구의 `400`이다. 변환은 FE가 한다
- `Content-Type`이 `image/`로 시작해야 한다
- SVG를 제외하면 **헤더를 읽을 수 있는** 이미지여야 한다. 검증은 헤더만 읽고 픽셀은 디코딩하지 않는다
- 헤더 기준 1억 픽셀을 넘으면 `400`이다(압축 폭탄 방지)

## 저장 방식

실제 파일은 오브젝트 스토리지(Cloudflare R2)에 저장한다. `slcn.storage.provider`가 `r2`가 아니면 `slcn.upload.path` 하위의 로컬 디스크를 같은 키 구조로 쓴다.

원본과 파생본은 prefix를 나눈다. 파생본에만 CDN과 캐시 정책을 걸 수 있어야 하기 때문이다.

- 원본: `originals/{type}/{uuid}.{ext}`
- 파생본: `derived/{type}/{uuid}_{variant}.{ext}`

`{type}`은 `logo`, `map`, `travel`, `profile`이다.

- `derived/travel/{uuid}_home-feature.{ext}` (가로 960px)
- `derived/travel/{uuid}_home-thumb.{ext}` (가로 320px)

축소본은 업로드 시점에 서버가 임시 디렉터리에서 생성한 뒤 업로드하며, 원본이 목표 너비보다 작으면 만들지 않는다.

- 원본을 전체 해상도로 디코딩하지 않는다. 결과 너비가 1920px 이상 남고 픽셀 수가 400만 이하가 되도록 줄여서 디코딩한다(`ImageInspector`). 40 MP JPG(7728×5152)는 1/4인 1932×1288로 읽힌다
- EXIF 방향 태그대로 사진을 세운 뒤 축소본을 만든다. 축소본에는 EXIF를 쓰지 않으므로 브라우저가 다시 돌리지 않는다
- `width`/`height`는 **화면에 보이는 방향 기준** 원본 크기다. 방향값 5~8(90°/270° 회전) 사진은 헤더 값과 가로·세로가 바뀐다
- 내장 ICC 프로필은 JDK JPEG 리더가 디코딩하면서 sRGB로 변환한다
- 축소본 생성은 서버 전체에서 동시 2건으로 제한한다. 차례를 60초(`slcn.upload.variant-wait-seconds`) 넘게 기다리면 축소본 없이 업로드를 끝내고, `width`/`height`만 헤더와 EXIF로 기록한다. 조회는 원본으로 폴백한다 인코딩은 WebP를 우선하고 사용할 수 없는 환경에서는 JPEG로 폴백한다. 확장자가 환경에 따라 달라질 수 있으므로 실제 파일명과 MIME 타입을 `variants`에 기록한다. 축소본 생성 실패는 업로드를 실패시키지 않는다.

업로드는 오브젝트 업로드가 모두 끝난 뒤에 메타데이터를 저장한다. 순서를 뒤집으면 객체가 없는 메타데이터가 생겨 조회가 깨진다.

### 조회 경로

- **원본**은 서버를 통과하지 않는다. 서명된 조회 URL(기본 만료 300초)로 `302 Found` 리다이렉트한다. 응답에 ETag를 붙이지 않고 `Cache-Control: no-store`를 건다.
- **축소본**은 서버가 오브젝트 스토리지에서 읽어 그대로 응답한다. ETag와 `Cache-Control: private, max-age=86400`은 이전과 같다.
- 로컬 프로바이더는 서명 URL을 만들 수 없으므로 원본도 바이트로 응답한다.
- 로컬 프로바이더도 새 키 구조(`originals/`, `derived/`)로만 읽는다. 기존 레이아웃(`{type}/{filename}`)은 읽지 않으므로, 이 버전을 처음 띄우는 환경은 아래 "기존 파일 이관"을 반드시 함께 실행해야 기존 이미지가 조회된다.

### 설정

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `slcn.storage.provider` | `local` | `local` 또는 `r2` |
| `slcn.storage.presigned-ttl-seconds` | `300` | 서명 URL 만료. 만료는 요청 시작 시점에만 검사된다 |
| `slcn.storage.r2.endpoint` | (빈 값) | R2 S3 호환 endpoint |
| `slcn.storage.r2.region` | `auto` | R2는 `auto`를 쓴다 |
| `slcn.storage.r2.bucket` | (빈 값) | 버킷 이름 |
| `slcn.storage.r2.access-key` | (빈 값) | `SLCN_R2_ACCESS_KEY` |
| `slcn.storage.r2.secret-key` | (빈 값) | `SLCN_R2_SECRET_KEY` |
| `slcn.storage.raw.part-size-bytes` | `16777216` | RAW 직접 업로드 파트 크기(16 MB). R2는 마지막 파트를 뺀 모든 파트가 같은 크기이고 5 MB 이상이어야 한다 |
| `slcn.storage.raw.session-ttl-seconds` | `1800` | RAW 파트 서명 URL 만료 |
| `slcn.storage.raw.cleanup-cron` | `0 30 4 * * *` | 끝나지 않은 RAW 업로드 정리(Asia/Seoul). 속성이 없거나 `-`면 꺼진다 |
| `slcn.upload.variant-wait-seconds` | `60` | 축소본 생성(동시 2건) 차례를 기다리는 최대 초 |
| `slcn.storage.migration.enabled` | `false` | 기동 시 기존 로컬 파일 1회 백필 |
| `slcn.storage.migration.batch-size` | `100` | 백필 페이지 크기 |

`slcn.upload.path`는 로컬 프로바이더의 기준 디렉터리이자 백필의 원본 위치로 계속 쓰인다.

### 기존 파일 이관

`slcn.storage.migration.enabled=true`로 기동하면 `ApplicationRunner`가 `file_asset`을 페이지 단위로 읽어 기존 파일을 새 키로 **복사**한다. 기존 파일은 지우지 않는다.

- 원본: `{slcn.upload.path}/{type}/{storedFilename}` → `originals/{type}/{storedFilename}`
- 축소본: `{slcn.upload.path}/{type}/{variantFilename}` → `derived/{type}/{variantFilename}`

대상 키가 이미 있으면 건너뛰므로 여러 번 돌려도 안전하다. 프로바이더가 `local`이면 같은 디스크의 새 경로로, `r2`면 버킷으로 복사한다. 끝나면 아래 로그가 한 줄 남는다.

```
Object storage migration finished. MigrationReport[uploaded=.., skipped=.., missing=.., failed=..]
```

- `missing`: 메타데이터는 있으나 디스크에 파일이 없는 건. `Legacy file is missing` 경고 로그에 key와 경로가 남는다.
- `failed`: 업로드 실패 건. 같은 설정으로 다시 기동하면 실패분만 이어서 처리한다.

코드 배포와 이관은 **같은 기동에서** 한다. 이 버전은 기존 레이아웃을 읽지 않으므로, 코드만 먼저 배포하면 이관 전까지 기존 이미지가 조회되지 않는다. 또한 러너는 웹 서버가 요청을 받기 시작한 뒤에 돌기 때문에, 이관이 끝나기 전의 짧은 구간에는 아직 옮기지 않은 이미지 조회가 실패할 수 있다. 사용량이 적은 시간대에 배포한다.

#### 운영 배포 순서 (R2)

1. R2 버킷을 만들고 자격 증명을 발급한다. 비교 기준으로 기존 파일 수를 기록한다: `find "$SLCN_UPLOAD_PATH" -mindepth 2 -maxdepth 2 -type f | wc -l`
2. 새 코드를 아래 환경변수로 기동한다. 기존 파일을 읽어야 하므로 `SLCN_UPLOAD_PATH` 볼륨은 그대로 마운트한다.
   ```
   SLCN_STORAGE_PROVIDER=r2
   SLCN_R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
   SLCN_R2_BUCKET=<bucket>
   SLCN_R2_ACCESS_KEY=...
   SLCN_R2_SECRET_KEY=...
   SLCN_STORAGE_MIGRATION_ENABLED=true
   ```
3. 이관 로그에서 `failed=0`을 확인한다. `missing`이 있으면 경고 로그로 한 건씩 확인한다. 실패가 있으면 다시 기동한다.
4. 한 번 더 기동해 `uploaded=0, skipped=N`인지, 버킷 객체 수가 1단계 파일 수(`missing` 제외)와 맞는지 확인한다. 원본 조회 302, 축소본 조회 200/ETag도 확인한다.
5. `SLCN_STORAGE_MIGRATION_ENABLED=false`로 되돌리고 재기동한다. 켜 둔 채로 두면 기동마다 파일 수만큼 HeadObject가 나간다.
6. 기존 `{SLCN_UPLOAD_PATH}/{type}/` 디렉터리는 한동안 운영해 문제가 없음을 확인한 뒤 지운다.

롤백 시 기존 파일은 남아 있으므로 이전 버전에서도 조회된다. 단, 새 버전으로 업로드된 파일은 새 키에만 있어 이전 버전에서 보이지 않는다.

#### 로컬 프로바이더를 유지하는 환경 (개발 등)

`SLCN_STORAGE_PROVIDER`는 설정하지 않고 `SLCN_STORAGE_MIGRATION_ENABLED=true`로 한 번 기동한 뒤 `false`로 되돌린다. 같은 디렉터리 안에 `originals/`, `derived/`가 생긴다. 정리할 때는 기존 `{type}/` 디렉터리만 지우고 `originals/`, `derived/`는 남긴다.

MongoDB `file_asset` 컬렉션에는 아래 정보를 저장한다.

- `id`
- `type`
- `originalFilename`
- `storedFilename`
- `path`
- `mimeType`
- `size`
- `width` (원본 가로 픽셀, 축소본 도입 이전 자산은 `0`)
- `height` (원본 세로 픽셀, 축소본 도입 이전 자산은 `0`)
- `variants` (생성된 축소본 목록. 각 항목은 `variant`, `filename`, `mimeType`)
- `registeredTime`
- `modifiedTime`

## RAW 첨부

여행 앨범 사진에는 같은 사진의 RAW(후지필름 `.RAF`)를 **다운로드 전용 첨부**로 붙일 수 있다. 화면에는 항상 보기용 JPG만 쓰고, 서버는 RAW를 디코딩하지 않는다. RAW 바이트는 서버를 통과하지 않는다. 업로드는 서명된 multipart URL, 다운로드는 서명된 GET URL이다.

`FileAsset`에는 아래 필드가 있다. 이 필드가 없는 과거 문서는 `IMAGE`/`READY`로 읽는다.

- `kind`: `IMAGE`(보기용) 또는 `RAW`(다운로드 전용)
- `status`: `READY` 또는 `PENDING`(RAW 직접 업로드가 완료 검증을 기다리는 중)
- `uploadId`: `PENDING` RAW의 저장소 multipart id. 취소·정리 때 쓰며 응답에는 싣지 않는다

흐름은 아래와 같다. 로컬 프로바이더에서는 세 API 모두 `501`이다.

1. `POST /api/assets/raw-uploads` `{ "type": "travel", "filename": "DSCF1234.RAF", "size": 83886080 }` → `201`
   - `type`은 `travel`만, 확장자는 `raf`만, 크기는 0 초과 500 MB 이하다
   - `PENDING` RAW 자산(`originals/travel/{uuid}.raf`)을 만들고 모든 파트의 서명 URL(16 MB 단위, 30분 만료)을 한 번에 준다
2. 브라우저가 파트마다 PUT하고 응답의 `ETag`를 모은다
3. `POST /api/assets/raw-uploads/{fileId}/complete` `{ "uploadId": "...", "parts": [{ "partNumber": 1, "etag": "\"...\"" }] }` → `200 FileAssetRdo`
   - `uploadId`가 세션 값과 다르면 `400`이다
   - 저장소의 실제 크기가 선언한 `size`와 같고, 앞 16바이트가 `FUJIFILMCCD-RAW `여야 한다. 틀리면 객체와 자산을 지우고 `400`이다
   - 저장소 일시 오류는 자산을 지우지 않고 `400`(업로드 실패)으로 돌려 재시도하게 한다
   - 이미 `READY`면 같은 응답을 다시 준다
4. 여행 등록·수정의 `files` 항목에 `rawFileAssetId`로 연결한다(아래 "여행")
5. 여행 상세의 파일 항목은 `rawFileAssetId`와 `rawFile`(다운로드 버튼용 `FileAssetRdo`)을 싣는다. RAW는 `GET /api/assets/files/{fileId}/download-url`로 받는다

`DELETE /api/assets/raw-uploads/{fileId}` → `204`

- `PENDING`이면 multipart 세션을 중단하고 자산을 지운다
- `READY`이고 어떤 `FileBox` 항목에도 연결되지 않았으면 객체와 자산을 지운다
- 여행에 연결된 RAW는 `409`다. 여행 수정으로 연결을 먼저 해제한다
- 보기용 이미지 자산은 `400`이다

RAW 자산은 인라인 조회(`GET /api/assets/files/{fileId}`)에서 `404`, 경로 기반 조회에서 `400`이다. `img` 태그에 RAW가 물려 수십 MB가 내려가는 것을 막는다.

## FileBox 연동

도메인과 파일의 연결은 MongoDB `file_box` 컬렉션에 `FileBox`로 저장한다. 소유자 1건당 문서 1건이며 `(ownerType, ownerId)`에 유니크 인덱스가 있다.

- `id`
- `ownerType`: `TRAVEL`, `TRIP`, `INSPECTION_VISIT`
- `ownerId`: 여행 ID, 나들이 ID 또는 임장 기록 ID
- `items`: 연결된 파일 목록
- `registeredTime`, `modifiedTime`

`items`의 각 항목은 아래 형태다.

- `id`
- `fileAssetId`: `FileAsset.id`
- `rawFileAssetId`: 같은 사진의 RAW 첨부 `FileAsset.id`. 여행에서만 쓰고, 없으면 `null`이다
- `targetType`: `TRAVEL`, `TRAVEL_DAY`, `TRAVEL_PLACE`, `TRIP`, `INSPECTION_VISIT`, `VIEWED_PROPERTY`
- `targetId`: 대상 식별자. `TRAVEL`/`TRIP`/`INSPECTION_VISIT`은 비워둔다
- `role`: `COVER`, `GALLERY`, `LOGO`, `FIRST_MAP`, `SECOND_MAP`
- `caption`
- `sortOrder`: 0 이하로 보내면 그룹별로 자동 채번한다

## 도메인 API 연동

FE는 먼저 `POST /api/assets/file` 또는 `POST /api/assets/files`로 사진을 업로드하고, 응답의 `fileId`를 도메인 API의 `files` 배열에 담아 전달한다.

### 여행

`POST /api/travels`, `PUT /api/travels/{travelId}`의 `files`에 전달한다. 파일 타입은 `travel`이어야 한다.

- `role`은 `COVER` 또는 `GALLERY`만 허용한다
- `targetType`이 `TRAVEL`이면 `targetId`는 비워야 한다
- `targetType`이 `TRAVEL_DAY`면 `targetId`는 여행 기간에 포함된 `yyyy-MM-dd`다
- `targetType`이 `TRAVEL_PLACE`면 `targetId`는 해당 여행에 존재하는 `placeKey`(UUID)다
- 여행 대표 이미지(`targetType=TRAVEL`, `targetId` 없음, `role=COVER`)는 **정확히 1건**이어야 한다
- 같은 `targetType`/`targetId`/`role`/`fileAssetId` 조합은 중복될 수 없다
- `fileAssetId`는 보기용 이미지(`kind=IMAGE`)여야 한다. RAW 자산을 넣으면 `400`이다
- `rawFileAssetId`가 있으면 `type=travel`, `kind=RAW`, `status=READY`인 자산이어야 한다. 업로드가 끝나지 않은 RAW는 `400`이다
- 한 여행 안에서 `fileAssetId`와 `rawFileAssetId`는 1:1로 대응해야 한다. 같은 사진을 표지와 앨범에 함께 두면 같은 RAW가 두 항목에 나와도 된다. 같은 RAW를 다른 사진에 붙이거나 같은 사진에 다른 RAW를 붙이면 `400`이다

```json
{
  "title": "강릉 1박 2일 여행",
  "region": "강릉",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "tags": ["바다", "카페"],
  "files": [
    {
      "fileAssetId": "file-1",
      "targetType": "TRAVEL",
      "role": "COVER"
    },
    {
      "fileAssetId": "file-2",
      "targetType": "TRAVEL_DAY",
      "targetId": "2026-07-01",
      "role": "GALLERY",
      "caption": "첫날 바다"
    }
  ]
}
```

수정 시 `files`를 보내면 그 목록이 최종 상태가 된다. 생략하면 기존 연결을 유지한다. 기존 항목의 `id`를 함께 보낼 때 그 `id`는 실제로 존재하는 항목이어야 한다. 기존 항목을 다시 보낼 때 `rawFileAssetId`를 빠뜨리면 RAW 연결이 해제된다.

### 나들이

`POST /api/trips`의 `files`에 전달한다. `targetType`은 모두 `TRIP`이고 `targetId`는 비워둔다.

- `LOGO`: 필수, 파일 타입 `logo`
- `FIRST_MAP`: 필수, 파일 타입 `map`
- `SECOND_MAP`: 선택, 파일 타입 `map`
- 같은 `role`을 두 번 보낼 수 없다

`SECOND_MAP`, `nextButtonText`, `previousButtonText`는 **셋 다 있거나 셋 다 없어야** 한다.

RAW 첨부는 여행 전용이다. `rawFileAssetId`를 보내면 등록이 거부된다.

### 임장

`POST /api/inspection-visits`, `PUT /api/inspection-visits/{visitId}`, 매물 등록·수정의 `files`에 전달한다. 파일 타입은 `inspection`이어야 한다.

한 임장 기록의 `FileBox` 하나가 임장 사진과 그 임장에 속한 매물 사진을 **함께** 담는다. 매물마다 `FileBox`를 만들지 않는다 — 상세 화면이 사진을 한 번에 읽고, 매물을 지울 때 해당 `items`만 걷어내면 된다.

- `role`은 `COVER` 또는 `GALLERY`만 허용한다
- `ownerType`은 `INSPECTION_VISIT`, `ownerId`는 임장 기록 ID다
- `targetType`이 `INSPECTION_VISIT`이면 `targetId`는 비워야 한다 (임장 자체의 사진)
- `targetType`이 `VIEWED_PROPERTY`면 `targetId`는 그 임장에 존재하는 매물 ID(UUID)다
- 위 두 값은 요청에 담지 않아도 서버가 경로에서 확정한다
- `rawFileAssetId`를 보내면 `400`이다. RAW 첨부는 여행 전용이다
- 정렬만 바꿀 때는 `files` 전체를 되돌려보내지 말고 `PUT /api/inspection-visits/{visitId}/images/order`를 쓴다. 임장 사진과 매물 사진을 한 엔드포인트가 함께 처리한다

### 프로필

사용자 프로필 이미지는 `FileBox`를 쓰지 않고 `user.profileImageFileId`에 `FileAsset.id`를 직접 보관한다. 파일 타입은 `profile`이어야 하며, 빈 문자열을 보내면 연결을 해제한다.

## 삭제

여행을 삭제하면 해당 `FileBox` 문서와 여행 레코드를 지운다.

임장 기록을 삭제할 때는 **RDB를 먼저 커밋하고 그 다음에 `FileBox`를 지운다.** 순서를 뒤집으면 RDB 삭제가 실패했을 때 `fileAssetId` 목록을 잃어 사진을 복구할 수 없다. 반대 순서에서 남는 것은 아무도 참조하지 않는 `FileBox` 문서 하나뿐이다.

**디스크의 실제 파일과 `file_asset` 문서는 남는다.** 도메인 삭제는 연결만 끊으며, 파일 자체를 정리하는 로직은 아직 없다.

예외는 RAW 직접 업로드다. 24시간 넘게 `PENDING`인 RAW는 매일 새벽 정리 작업(`RawUploadCleanupScheduler`)이 multipart 세션을 중단하고 자산을 지운다. 완료됐지만 어떤 여행에도 연결되지 않은 RAW는 자동으로 지우지 않는다. FE가 `DELETE /api/assets/raw-uploads/{fileId}`로 지우거나 수동으로 확인한다. R2 버킷 라이프사이클(미완료 multipart 7일 중단)이 마지막 안전망이다.
