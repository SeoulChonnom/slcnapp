너는 SLCN(Seoul Chonnom) 프론트엔드(`slcnfront`)를 수정하는 시니어 프론트엔드 엔지니어다. 백엔드는 Spring Boot이고 base path는 `/api`다.

백엔드가 여행 앨범에 **카메라 JPG(20~40 MB) 업로드**와 **RAW(후지필름 `.RAF`) 다운로드 전용 첨부**를 지원하도록 바뀌었다. 이 요청의 목적은 프론트엔드를 바뀐 API 계약에 맞추는 것이다. 근거 문서는 백엔드 저장소의 `docs/superpowers/plans/2026-09-25-travel-raw-attachment-and-large-jpeg.md`(§2 계약, §4 FE 변경)와 `docs/file-asset.md`, `docs/image-asset-api.md`다.

작업 원칙:

- 판단마다 근거를 붙여라. 코드 근거는 `파일경로:줄번호`, 런타임 근거는 요청 URL·상태 코드·응답 본문이다.
- RAW 기능은 로컬 프로바이더에서 `501`이다. 실제 업로드 검증은 MSW 모의 테스트와 R2 스테이징에서 한다.

## 1. 먼저 할 일 — 여행 수정 시 사진 목록 전체 전송 (기존 버그)

백엔드는 여행 수정(`PUT /api/travels/{travelId}`)에 `files`가 있으면 그 목록으로 **통째로 치환**한다. 지금 FE는 새 사진이 있을 때 새로 올린 사진만 보낸다(`src/domains/travel/components/TravelRegisterSection.tsx`의 제출부, `src/domains/travel/mappers/travel-payload.ts`).

- 수정 중 앨범 사진을 추가하면 기존 사진(날짜·장소 사진 포함)이 모두 사라진다.
- 표지 없이 앨범만 추가하면 COVER가 없어 `400`("여행 대표 이미지는 1개여야 합니다.")이다.

고칠 내용:

- 수정 시 **기존 항목**(`id`, `fileAssetId`, `rawFileAssetId`, `role`, `targetType`, `targetId`, `caption`, `sortOrder`)에 **새 항목**을 더한 전체 목록을 보낸다. 새 사진이 없으면 지금처럼 `files`를 생략한다.
- 기존 항목의 `rawFileAssetId`를 빠뜨리면 백엔드는 RAW 연결을 해제한다.
- 새 항목의 `sortOrder`는 기존 그룹의 최대값 다음부터 매긴다. 백엔드는 같은 `sortOrder`를 거부하지 않으므로, 0부터 다시 매기면 기존 사진과 순서가 섞인다.

## 2. 보기용 이미지 업로드 — 경로·응답은 그대로, 한도만 바뀜

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 파일당 한도 | 10 MB | **50 MB** |
| 요청 전체 한도 | 60 MB | **110 MB**. FE는 요청당 **100 MB·6장 이하**로 나눠 보낸다 |
| HEIC/HEIF | 확장자 오류 | `400` "HEIC 사진은 JPG로 변환해 올려 주세요." → **FE가 브라우저에서 JPEG로 변환**(WASM 변환기 지연 로딩)해서 올린다 |
| 해상도 | 제한 없음 | 헤더 기준 1억 픽셀 초과 `400` "이미지 해상도가 너무 큽니다." |

`FileAssetRdo`에 필드가 추가됐다.

- `kind`: `IMAGE` | `RAW`
- `status`: `READY` | `PENDING`
- `width`/`height`: 이제 **화면에 보이는 방향 기준**이다(EXIF로 돌린 세로 사진은 세로 값). 지금 FE는 이 값을 쓰지 않으므로 변경은 없다.

여행 상세 표지가 원본을 쓰고 있으면 `home-feature` 축소본으로 바꾼다(40 MB 원본을 표지로 내려받지 않게).

## 3. RAW 짝짓기와 1:1 규칙

- 여행 **앨범 칸**의 파일 선택에서 `.RAF`를 허용하고, 파일명 앞부분(stem)으로 JPG와 RAF를 짝짓는다. 짝 없는 RAF는 경고를 보여 주고 제외한다.
- 표지 칸은 JPG 한 장만 받는다.
- **같은 `File`은 한 번만 업로드한다.** 표지와 앨범에 같은 파일(이름·크기·`lastModified` 일치)이 있으면 JPG 자산 하나를 COVER와 GALLERY 항목에 같이 쓰고, 표지 항목도 앨범 쪽 `rawFileAssetId`를 이어받는다.
- 백엔드 규칙: 한 여행 안에서 `fileAssetId`와 `rawFileAssetId`는 1:1이다. 같은 RAW를 다른 사진에 붙이거나 같은 사진에 다른 RAW를 붙이면 `400` "하나의 사진에는 하나의 RAW 파일만 연결할 수 있습니다."다. 같은 사진+같은 RAW가 표지와 앨범에 함께 나오는 것은 허용된다.

## 4. RAW 업로드 (브라우저 → R2 직접)

RAW 바이트는 백엔드를 거치지 않는다. 모든 요청에 `X-AUTH-TOKEN`이 필요하다.

**① 세션 생성** `POST /api/assets/raw-uploads`

```json
// 요청
{ "type": "travel", "filename": "DSCF1234.RAF", "size": 83886080 }
// 201 응답
{
  "fileId": "0b3c...uuid",
  "uploadId": "r2-multipart-upload-id",
  "partSize": 16777216,
  "expiresAt": "2026-09-25T13:30:00+09:00",
  "parts": [ { "partNumber": 1, "url": "https://<account>.r2.cloudflarestorage.com/<bucket>/originals/travel/<uuid>.raf?partNumber=1&uploadId=...&X-Amz-..." } ]
}
```

- `type`이 `travel`이 아니면 `400`, 확장자가 `raf`가 아니면 `400`, 크기가 0 이하이거나 500 MB 초과면 `400`이다.
- 로컬 프로바이더면 `501`이다. "로컬 환경에서는 RAW를 올릴 수 없어요" 안내를 보여 준다.

**② 파트 업로드**

- `file.slice((n-1) * partSize, n * partSize)`를 `parts[n-1].url`로 **PUT**한다. **Content-Type 없이** 보낸다(R2 CORS `AllowedHeaders`는 `content-type`뿐이고, 인증 헤더도 붙이지 않는다).
- 각 PUT 응답의 `ETag` 헤더를 그대로(따옴표 포함) 모은다.
- 실패한 파트는 3회까지 재시도하고 진행률을 보여 준다.
- URL은 30분 뒤 만료된다. 만료(R2 `403`)면 ③으로 세션을 지우고 ①부터 다시 한다.

**③ 완료** `POST /api/assets/raw-uploads/{fileId}/complete`

```json
{ "uploadId": "r2-multipart-upload-id", "parts": [ { "partNumber": 1, "etag": "\"abc...\"" } ] }
```

- `200` + `FileAssetRdo`(`kind: "RAW"`, `status: "READY"`).
- `uploadId`가 다르면 `400` "업로드 세션이 일치하지 않습니다."
- 크기나 RAF 매직이 틀리면 백엔드가 파일을 지우고 `400` "RAW 파일 내용이 올바르지 않습니다. 다시 올려 주세요."
- 저장소 일시 오류는 `400` "파일 업로드가 실패하였습니다."이고 자산은 남아 있다. 완료 요청을 다시 보내면 된다.
- 이미 완료된 세션에 다시 보내면 같은 응답이 온다(멱등).

**④ 취소·삭제** `DELETE /api/assets/raw-uploads/{fileId}` → `204`

- 업로드 실패, 사용자가 RAW를 목록에서 뺀 경우, 저장을 포기한 경우에 호출한다.
- 업로드 중(`PENDING`)이든, 완료됐지만 아직 여행에 저장되지 않았든 `204`다.
- 이미 여행에 저장된 RAW는 `409`다. 저장된 여행에서 RAW를 빼는 것은 여행 수정에서 `rawFileAssetId`를 지우는 것으로 한다.

**⑤ 여행 저장**

- 모든 RAW가 `READY`가 된 뒤에 저장한다. `PENDING` RAW를 연결하면 `400` "RAW 업로드가 아직 끝나지 않았습니다."다.
- 사진 항목에 `rawFileAssetId`를 넣는다.

```json
{ "fileAssetId": "<JPG fileId>", "rawFileAssetId": "<RAF fileId>", "targetType": "TRAVEL", "targetId": null, "role": "GALLERY", "caption": null }
```

## 5. 스키마와 다운로드

- 요청 타입 `TravelFileBoxItemCdo`에 `id`(기존 항목 유지용)와 `rawFileAssetId`를 추가한다.
- 응답 스키마 `fileBoxItemRdoSchema`에 `rawFileAssetId`(nullable)와 `rawFile`(nullable `FileAssetRdo`, `originalFilename`·`size` 사용)을 추가한다. `rawFileAssetId`가 있는데 `rawFile`이 `null`이면 RAW가 사라진 것이므로 버튼을 숨긴다.
- 여행 상세에서 `rawFile`이 있는 사진에 "RAW 다운로드" 버튼을 둔다.
  - `GET /api/assets/files/{rawFileId}/download-url` (`X-AUTH-TOKEN` 필수, 쿠키 인증 불가) → `{ "url", "filename", "size", "expiresAt" }`
  - `location.href = url`로 이동한다. URL은 300초 뒤 만료되므로 클릭할 때마다 새로 받는다. 로컬 프로바이더에서는 `501`이다.
- RAW 파일 id를 `<img>`에 넣지 않는다. `GET /api/assets/files/{rawFileId}`는 `404`다.

## 6. 산출물

- 변경한 파일 목록과 각 변경의 이유
- 1번 버그 수정의 회귀 테스트(기존 사진이 유지되는지, 표지 없이 앨범만 추가해도 저장되는지)
- RAW 업로드 흐름의 MSW 모의 테스트(정상, 파트 재시도, URL 만료 후 재시작, 완료 실패, 취소)
- 실행하지 못한 검증과 그 이유
