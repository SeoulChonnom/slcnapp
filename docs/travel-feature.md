# 여행 기록 기능

## 개요

`travel` 도메인은 1박 이상 여행 기록을 관리한다. 여행 기본 정보, 일자, 장소, 사진 연결, 태그, 후기를 분리해 저장하고 상세 조회에서 한 번에 조립해 내려준다.

## 주요 모델

- `Travel`: `title`, `region`, `startDate`, `endDate`, `coverPhotoId`, `oneLineReview`, `hidden`, `registeredTime`, `modifiedTime`
- `TravelDay`: `travelId`, `date`, `title`, `memo`, `coverPhotoId`, `sortOrder`, `dayNumber`
- `TravelPlace`: `travelId`, `travelDayId`, `name`, `category`, `description`, `coverPhotoId`, `sortOrder`
- `TravelPhoto`: `travelId`, nullable `travelDayId`, nullable `travelPlaceId`, `photoFileId`, `caption`, `sortOrder`
- `TravelTag`: `travelId`, `name`, `sortOrder`
- `TravelReview`: `oneLineSummary`, `goodPoint`, `badPoint`, `revisitPlace`, `finalReview`

`TravelPlaceCategory` 값은 `TOURIST_SPOT`, `RESTAURANT`, `CAFE`, `ACCOMMODATION`, `SHOPPING`, `TRANSPORT`, `ACTIVITY`, `ETC`이다.

## API

- `GET /travels`: 여행 목록 조회. 기본 정보, `coverPhotoId`, `oneLineReview`, `tags`를 포함한다.
- `GET /travels/{travelId}`: 여행 상세 조회. 기본 정보, `tags`, `days`, `places`, `photos`, `review`를 포함한다. `days` 안에도 해당 day의 places/photos를 조립한다.
- `POST /travels`: 여행 생성. `coverPhotoId`는 필수이며 `tags`, `travelDays`, 여행 전체 `photos`, `review`를 함께 저장할 수 있다.
- `PUT /travels/{travelId}` 또는 `PATCH /travels/{travelId}`: 여행 전체 저장. 기본 정보/기간/태그를 수정하고, 요청에 `travelDays`가 있으면 날짜별 기록, 장소, 사진 연결을 요청 내용 기준으로 동기화한다. `coverPhotoId`는 필수이고 기간 축소 시 `confirmDeleteDays` 정책을 따른다.
- `DELETE /travels/{travelId}`: 여행과 연결 데이터 삭제. 실제 파일은 삭제하지 않는다.
- `PATCH /travels/{travelId}/days/{travelDayId}`: day의 `title`, `memo`, `coverPhotoId`, `sortOrder` 수정. `coverPhotoId`는 필수다.
- `POST /travels/{travelId}/days/{travelDayId}/places`: 장소 추가. `photoFileIds`가 있으면 장소 사진 연결도 생성한다.
- `PATCH /travels/{travelId}/days/{travelDayId}/places/{placeId}`: 장소 수정. place가 travel/day에 속하는지 검증한다.
- `DELETE /travels/{travelId}/days/{travelDayId}/places/{placeId}`: 장소와 사진 연결만 삭제한다.
- `POST /travels/{travelId}/photos`: 사진 연결 추가. day/place가 모두 없으면 여행 전체 사진, place만 있으면 day를 유추한다.
- `GET /travels/{travelId}/photos`: 여행 전체, day, place 사진을 모두 조회한다.
- `GET /travels/{travelId}/days/{travelDayId}/photos`: day 직접 사진과 해당 day의 place 사진을 조회한다.
- `GET /travels/{travelId}/places/{placeId}/photos`: 해당 place 사진만 조회한다.
- `DELETE /travels/{travelId}/photos/{travelPhotoId}`: 사진 연결만 삭제한다.
- `PUT /travels/{travelId}/review`: 후기 5개 필드를 upsert한다.

기존 태그 단건 추가/삭제 API도 유지한다.

## 전체 저장 요청 구조

FE의 주요 화면은 목록, 상세, 전체 수정 화면이므로 상세 조회 응답과 유사한 구조를 `POST /travels`, `PUT /travels/{travelId}`에 전달할 수 있다.

- 최상위 `photos`: 여행 전체 앨범에 직접 연결되는 사진이다.
- `travelDays[].photos`: 해당 날짜에 직접 연결되는 사진이다.
- `travelDays[].places[].photos`: 해당 장소에 직접 연결되는 사진이다.
- `travelDays` 필드가 요청에 포함되면 기존 날짜별 장소/사진 연결은 요청 내용 기준으로 재구성된다.
- `review`가 포함되면 기존 후기를 upsert한다.

## 정책

- 여행은 `startDate < endDate`인 1박 이상 기간만 허용한다.
- 여행 생성/수정 시 `region`은 필수이며 blank/null 값은 `400 Bad Request`로 거부한다.
- 생성/기간 증가는 날짜 범위에 맞춰 `TravelDay`를 자동 생성하고 `dayNumber`, `sortOrder`를 순서대로 부여한다.
- 기간 감소로 삭제될 day에 `title`, `memo`, `coverPhotoId`, place, photo 연결이 있으면 `confirmDeleteDays=true`가 없을 때 `409 Conflict`를 반환한다.
- 기간 충돌 응답은 `ErrorResponse.message`에 삭제 대상 날짜 목록을 포함한다.
- `confirmDeleteDays=true`면 삭제 대상 day/place/photo 연결을 삭제한다. 실제 파일 API는 호출하지 않는다.
- 같은 대상과 `photoFileId` 조합의 중복 사진 연결은 로직에서 거부한다. nullable target 때문에 DB unique 대신 분기 검증한다.
- 태그는 trim 후 저장하며 빈 값, 동일 여행 내 중복, 10개 초과를 거부한다.

## 저장소

JPA 테이블명은 요구 후보 복수형을 사용한다.

- `travels`
- `travel_days`
- `travel_places`
- `travel_photos`
- `travel_tags`
- `travel_reviews`

가능한 범위에서 `travel_tags(travel_id,name)`, `travel_reviews(travel_id)`, `travel_days(travel_id,date)` 유니크 제약과 조회 인덱스를 JPA `@Table`에 선언했다.

## 구현 위치

- 계약/도메인: `slcn-spec/src/main/java/com/seoulchonnom/spec/travel`
- 비즈니스/저장소: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/travel`
- REST 어댑터: `slcn-rest/src/main/java/com/seoulchonnom/rest/travel/TravelResource.java`
- 에러 코드: `ErrorCode.TRAVEL_PERIOD_CONFLICT`는 `409 Conflict`로 매핑된다.

## 변경 파일 요약

- `slcn-spec`: travel 도메인, DTO, mapper, facade 계약과 `FileAssetRdo`를 추가했다.
- `slcn-aggregate`: travel JPA store/logic, FileAsset Mongo store/logic, 파일 저장 유틸 확장을 추가했다.
- `slcn-rest`: travel REST API와 FileAsset 업로드/조회 API를 추가했다.
- `docs`: 여행 기록 정책과 FileAsset 업로드 정책을 문서화했다.

## 검증

실행 명령:

```bash
./gradlew :slcn-spec:test :slcn-aggregate:test :slcn-rest:test
./gradlew test
```

결과: 모두 `BUILD SUCCESSFUL`.

## 남은 연동 주의사항

- 전체 저장 API에서 `travelDays`를 전달하면 날짜별 장소와 사진 연결은 요청 내용 기준으로 재구성된다.
- 여행 도메인은 `FileAsset.id`만 참조하며 실제 파일 삭제는 호출하지 않는다.
- 당일 나들이 `Trip`도 `FileAsset.id` 기반으로 통일되어, FE는 Trip/Travel 이미지 모두 동일한 파일 업로드/조회 흐름을 사용한다.
