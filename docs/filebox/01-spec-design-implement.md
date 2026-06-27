# FileBox Spec 구현 결과

## 작업 범위

`docs/filebox/01-spec-design.md` 기준으로 `slcn-spec` 모듈의 계약을 FileBox 중심으로 전환했다.

이번 작업은 Spec 모듈 순차 개발 단계이므로 `slcn-aggregate`, `slcn-rest`, `slcn-boot`의 구현 전환은 수행하지 않았다. 다만 하위 모듈 영향도는 컴파일로 확인해 후속 작업
항목으로 기록했다.

## 서브에이전트 수행 결과

### 테스트 코드 작성

테스트 담당 서브에이전트가 `slcn-spec` 테스트를 설계 기준으로 보강했다.

- `FileBoxMapperTest`
    - `FileBoxItemCdo -> FileBoxItem`
    - `FileBoxItemUdo -> FileBoxItem`
    - `FileBoxItem + FileAssetRdo -> FileBoxItemRdo`
- `TravelMapperTest`
    - `Travel + FileBoxItemRdo` 기반 상세 응답 조립
    - 원본 `files` 유지
    - nested `cover/photos` projection 조립
    - `review.oneLineSummary -> oneLineReview` 파생
- `TripMapperTest`
    - `TripCdo`가 직접 파일 ID 대신 `files`를 노출하는지 검증
    - Trip 상세 응답이 FileBox item 메타데이터 없이 role별 `FileAssetRdo`만 노출하는지 검증
    - 기존 quiz 매핑 계약 유지 검증

### 코드 수정내역 분석

분석 담당 서브에이전트가 Spec 전환 후 하위 모듈 영향도를 검토했다.

핵심 결론:

- Spec 전환만으로는 전체 프로젝트 컴파일이 깨진다.
- `slcn-aggregate`는 아직 `TravelDay`, `TravelPlace`, `TravelReview`, `TravelTag`, `TravelPhoto`를 독립 엔티티로 전제한다.
- `TripLogic`, `TripJpo`, `TripJpoMapper`는 아직 `logoFileId`, `firstMapFileId`, `secondMapFileId` 직접 필드에 의존한다.
- 후속 Aggregate 단계에서 FileBox 저장소, FileBox item 검증, Travel/Trip 응답 조립, JPA 모델 정리가 함께 필요하다.

## 구현 내용

### FileBox Spec 추가

추가 패키지:

```text
com.seoulchonnom.spec.filebox
├── entity
│   └── FileBox.java
├── entity/vo
│   ├── FileBoxItem.java
│   ├── FileBoxOwnerType.java
│   ├── FileBoxTargetType.java
│   └── FileBoxItemRole.java
├── facade/sdo
│   ├── FileBoxItemCdo.java
│   ├── FileBoxItemRdo.java
│   └── FileBoxItemUdo.java
└── mapper
    └── FileBoxMapper.java
```

구현 기준:

- `FileBox`는 `DomainEntity` aggregate root로 추가했다.
- `FileBoxItem`은 `JsonSerializable` VO로 추가했다.
- `FileBoxItemRdo`는 `FileAssetRdo file`을 포함한다.
- `FileBoxMapper`는 필드 변환만 담당한다.
- 신규 item id 생성, owner 소유 검증, target 존재 검증은 aggregate 단계 책임으로 남겼다.

### Travel Spec 전환

도메인 변경:

- `Travel` root에 `days`, `tags`, `review`를 포함했다.
- `Travel.coverPhotoId`, `Travel.oneLineReview`를 제거했다.
- `TravelDay`, `TravelPlace`, `TravelReview`를 `entity.vo` 패키지의 VO로 이동했다.
- `TravelPhoto`, `TravelTag` 도메인 엔티티를 제거했다.

DTO 변경:

- `TravelCdo`, `TravelUdo`에 `files`를 추가했다.
- `TravelCdo`, `TravelUdo`, `TravelDayUdo`, `TravelPlaceUdo`에서 `coverPhotoId`, `photos`를 제거했다.
- `TravelDayUdo`는 `date`를 day 식별자로 사용한다.
- `TravelPlaceUdo`는 `placeKey`를 place 식별자로 사용한다.
- `TravelDetailRdo`는 `files`, root `cover`, day/place nested `cover/photos`를 표현한다.
- 기존 최상위 `places`, `photos` 중복 응답은 제거했다.
- `TravelRdo`, `TravelDetailRdo`의 `tags`는 `List<String>`으로 변경했다.
- `TravelReviewRdo`, `TravelReviewUdo`에서 legacy `content` 저장 계약을 제거했다.

Mapper 변경:

- `TravelMapper`는 `Travel + List<FileBoxItemRdo>`를 받아 상세 응답을 조립한다.
- root/day/place `cover`는 `role=COVER` 기준으로 조립한다.
- day/place `photos`는 `role=GALLERY` 기준으로 조립한다.
- `oneLineReview`는 `Travel.review.oneLineSummary`에서 파생한다.

### Trip Spec 전환

도메인 변경:

- `Trip.logoFileId`, `Trip.firstMapFileId`, `Trip.secondMapFileId`를 제거했다.

DTO 변경:

- `TripCdo.files: List<FileBoxItemCdo>`를 추가했다.
- `TripCdo`에서 직접 파일 ID 필드를 제거했다.
- 향후 Trip 수정 API를 고려해 `TripUdo`를 추가했다.
- `TripDetailRdo`, `TripListRdo`는 기존처럼 role별 `FileAssetRdo` 응답을 유지한다.

Mapper 변경:

- `TripMapper`의 응답 매핑은 `Trip + FileAssetRdo` 조합을 유지한다.
- Trip은 FileBox item 메타데이터를 FE에 노출하지 않는 설계를 유지했다.

## 검증 결과

성공:

```text
./gradlew :slcn-spec:clean :slcn-spec:compileJava
BUILD SUCCESSFUL
```

```text
./gradlew :slcn-spec:test
BUILD SUCCESSFUL
```

추가 점검:

- Spec production 코드에서 `TravelPhoto`, `TravelTag`, `TravelPlaceCdo`, `coverPhotoId`, `logoFileId`, `firstMapFileId`,
  `secondMapFileId` 잔존 여부를 검색했다.
- production 코드에는 삭제 대상 계약이 남지 않았고, 테스트의 부정 검증 문자열만 남아 있다.
- 도메인 entity/VO에서 facade DTO 또는 RDO를 역참조하는 문제는 검색되지 않았다.

## 하위 모듈 영향도

아래 명령은 실패한다.

```text
./gradlew :slcn-aggregate:compileJava
```

주요 실패 원인:

- `TravelLogic`, `TravelStore`, `TravelJpoMapper`가 삭제된 `com.seoulchonnom.spec.travel.entity.TravelDay`, `TravelPlace`,
  `TravelReview`, `TravelTag`, `TravelPhoto`를 import한다.
- `TravelLogic`이 삭제된 `TravelPhotoCdo`를 사용한다.
- `TravelJpoMapper`가 `DomainEntity` 기반 하위 Travel 엔티티 매핑을 전제한다.
- `TripJpoMapper`에서 `Trip` 제거 필드인 `logoFileId`, `firstMapFileId`, `secondMapFileId` 미매핑 경고가 발생한다.

후속 Aggregate 작업에서 필요한 정리:

- FileBox JPO, repository, store, mapper 추가
- Travel 저장/조회 시 `Travel` root 내부 VO 조립 방식 결정
- `TravelPhoto` 저장/조회 제거 후 FileBox item으로 대체
- `TravelTag` 저장/조회 제거 후 `List<String>` 저장 방식 정리
- Travel 기간 축소 시 `TRAVEL_DAY` FileBox item 삭제 정책 구현
- Travel place `placeKey` UUID 형식, 중복, 변경 불가 검증 구현
- Trip 등록/수정에서 FileBox role 기반 파일 검증 구현
- Trip 조회에서 FileBox role item을 `FileAssetRdo`로 조립
- 기존 JPA 테이블/컬럼 잔존 정책 검토

## 변경 파일 요약

주요 추가:

- `slcn-spec/src/main/java/com/seoulchonnom/spec/filebox/**`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/vo/TravelDay.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/vo/TravelPlace.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/vo/TravelReview.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/trip/facade/sdo/TripUdo.java`
- `slcn-spec/src/test/java/com/seoulchonnom/spec/filebox/mapper/FileBoxMapperTest.java`

주요 제거:

- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/TravelDay.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/TravelPlace.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/TravelReview.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/TravelTag.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/entity/TravelPhoto.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/facade/sdo/TravelPhotoCdo.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/facade/sdo/TravelPhotoRdo.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/facade/sdo/TravelTagCdo.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/facade/sdo/TravelTagRdo.java`
- `slcn-spec/src/main/java/com/seoulchonnom/spec/travel/facade/sdo/TravelPlaceCdo.java`

## 결론

`slcn-spec` 기준 FileBox 설계 구현은 완료했다.

Spec 단독 컴파일과 테스트는 통과한다. 전체 프로젝트는 아직 통과하지 않으며, 이는 이번 단계에서 Spec 계약을 먼저 전환했기 때문에 발생한 예상된 하위 모듈 영향이다. 다음 단계는
`slcn-aggregate`에서 FileBox 저장/검증/조립 흐름을 구현하면서 현재 컴파일 실패 지점을 해소하는 것이다.
