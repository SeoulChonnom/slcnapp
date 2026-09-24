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
- `GET /api/assets/file?type={type}&filename={filename}`
  - 경로 기반 파일 조회 API다. 축소본을 타지 않고 항상 해당 파일을 그대로 반환한다.

FE 연동 규칙(축소본 선택, 캐시, 구 자산 처리)은 `docs/image-asset-api.md`를 기준으로 한다.

### 업로드 제약

- 최대 10 MB
- 확장자는 `jpg`, `jpeg`, `png`, `gif`, `svg`
- `Content-Type`이 `image/`로 시작해야 한다
- SVG를 제외하면 실제로 디코딩 가능한 이미지여야 한다
- 다중 업로드는 요청 전체 60 MB까지다 (`spring.servlet.multipart.max-request-size`)

## 저장 방식

실제 파일은 오브젝트 스토리지(Cloudflare R2)에 저장한다. `slcn.storage.provider`가 `r2`가 아니면 `slcn.upload.path` 하위의 로컬 디스크를 같은 키 구조로 쓴다.

원본과 파생본은 prefix를 나눈다. 파생본에만 CDN과 캐시 정책을 걸 수 있어야 하기 때문이다.

- 원본: `originals/{type}/{uuid}.{ext}`
- 파생본: `derived/{type}/{uuid}_{variant}.{ext}`

`{type}`은 `logo`, `map`, `travel`, `profile`이다.

- `derived/travel/{uuid}_home-feature.{ext}` (가로 960px)
- `derived/travel/{uuid}_home-thumb.{ext}` (가로 320px)

축소본은 업로드 시점에 서버가 임시 디렉터리에서 생성한 뒤 업로드하며, 원본이 목표 너비보다 작으면 만들지 않는다. 인코딩은 WebP를 우선하고 사용할 수 없는 환경에서는 JPEG로 폴백한다. 확장자가 환경에 따라 달라질 수 있으므로 실제 파일명과 MIME 타입을 `variants`에 기록한다. 축소본 생성 실패는 업로드를 실패시키지 않는다.

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

수정 시 `files`를 보내면 그 목록이 최종 상태가 된다. 생략하면 기존 연결을 유지한다. 기존 항목의 `id`를 함께 보낼 때 그 `id`는 실제로 존재하는 항목이어야 한다.

### 나들이

`POST /api/trips`의 `files`에 전달한다. `targetType`은 모두 `TRIP`이고 `targetId`는 비워둔다.

- `LOGO`: 필수, 파일 타입 `logo`
- `FIRST_MAP`: 필수, 파일 타입 `map`
- `SECOND_MAP`: 선택, 파일 타입 `map`
- 같은 `role`을 두 번 보낼 수 없다

`SECOND_MAP`, `nextButtonText`, `previousButtonText`는 **셋 다 있거나 셋 다 없어야** 한다.

### 임장

`POST /api/inspection-visits`, `PUT /api/inspection-visits/{visitId}`, 매물 등록·수정의 `files`에 전달한다. 파일 타입은 `inspection`이어야 한다.

한 임장 기록의 `FileBox` 하나가 임장 사진과 그 임장에 속한 매물 사진을 **함께** 담는다. 매물마다 `FileBox`를 만들지 않는다 — 상세 화면이 사진을 한 번에 읽고, 매물을 지울 때 해당 `items`만 걷어내면 된다.

- `role`은 `COVER` 또는 `GALLERY`만 허용한다
- `ownerType`은 `INSPECTION_VISIT`, `ownerId`는 임장 기록 ID다
- `targetType`이 `INSPECTION_VISIT`이면 `targetId`는 비워야 한다 (임장 자체의 사진)
- `targetType`이 `VIEWED_PROPERTY`면 `targetId`는 그 임장에 존재하는 매물 ID(UUID)다
- 위 두 값은 요청에 담지 않아도 서버가 경로에서 확정한다
- 정렬만 바꿀 때는 `files` 전체를 되돌려보내지 말고 `PUT /api/inspection-visits/{visitId}/images/order`를 쓴다. 임장 사진과 매물 사진을 한 엔드포인트가 함께 처리한다

### 프로필

사용자 프로필 이미지는 `FileBox`를 쓰지 않고 `user.profileImageFileId`에 `FileAsset.id`를 직접 보관한다. 파일 타입은 `profile`이어야 하며, 빈 문자열을 보내면 연결을 해제한다.

## 삭제

여행을 삭제하면 해당 `FileBox` 문서와 여행 레코드를 지운다.

임장 기록을 삭제할 때는 **RDB를 먼저 커밋하고 그 다음에 `FileBox`를 지운다.** 순서를 뒤집으면 RDB 삭제가 실패했을 때 `fileAssetId` 목록을 잃어 사진을 복구할 수 없다. 반대 순서에서 남는 것은 아무도 참조하지 않는 `FileBox` 문서 하나뿐이다.

**디스크의 실제 파일과 `file_asset` 문서는 남는다.** 도메인 삭제는 연결만 끊으며, 파일 자체를 정리하는 로직은 아직 없다.
