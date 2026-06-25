# FileAsset 파일 업로드

## 개요

파일 업로드는 실제 이미지 파일을 디스크에 저장하고, 파일 메타데이터는 MongoDB `file_asset` 컬렉션에 `FileAsset`으로 저장한다.

나들이/여행 API는 파일 자체를 받지 않고 `FileAsset.id`를 이미지 참조값으로 사용한다.

## API

- `POST /file`
  - 기존 단건 업로드 API다.
  - 실제 파일을 저장하고 MongoDB에 `FileAsset`을 생성한 뒤 `FileAssetRdo`를 반환한다.
- `GET /file?type={type}&filename={filename}`
  - 기존 경로 기반 파일 조회 API다.
- `POST /files`
  - 다중 업로드 API다.
  - `multipart/form-data`
  - `files`: 여러 이미지 파일
  - `type`: `logo`, `map`, `travel`
  - 응답은 `FileAssetRdo` 목록이다.
- `GET /files/{fileId}`
  - `FileAsset.id`로 실제 파일 바이트를 조회한다.

## 저장 방식

실제 파일은 기존 `upload.path` 하위에 저장한다.

- `logo/{uuid}.{ext}`
- `map/{uuid}.{ext}`
- `travel/{uuid}.{ext}`

MongoDB `file_asset` 컬렉션에는 아래 정보를 저장한다.

- `id`
- `type`
- `originalFilename`
- `storedFilename`
- `path`
- `mimeType`
- `size`
- `registeredTime`
- `modifiedTime`

## 도메인 API 연동

FE는 먼저 `POST /file` 또는 `POST /files`로 사진들을 업로드하고 응답으로 받은 `fileId`를 도메인 API에 전달한다.

Trip은 `logoFileId`, `firstMapFileId`, `secondMapFileId`에 `FileAsset.id`를 전달한다.

Travel은 `coverPhotoId`, `photoFileId`에 `FileAsset.id`를 전달한다.

```json
{
  "title": "강릉 1박 2일 여행",
  "region": "강릉",
  "startDate": "2026-07-01",
  "endDate": "2026-07-02",
  "coverPhotoId": "file-1",
  "tags": ["바다", "카페"]
}
```

실제 파일 삭제는 여행 도메인에서 수행하지 않는다. 여행/날짜/장소/사진 연결 삭제는 `TravelPhoto` 연결만 제거한다.
