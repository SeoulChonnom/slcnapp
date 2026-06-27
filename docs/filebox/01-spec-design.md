# FileBox Spec 설계

## 목적

이 문서는 `slcn-spec` 모듈 기준으로 Travel/Trip의 파일 참조 모델을 재정리하기 위한 설계서다.

현재 공개 API 기준에서 Travel은 전체 생성, 전체 수정, 전체 삭제, 상세 조회 단위로만 동작한다. 따라서 `TravelDay`, `TravelPlace`, `TravelTag`, `TravelReview`
는 독립 생명주기를 가진 도메인 엔티티보다 `Travel` aggregate 내부 값 객체에 가깝다.

반면 사진/파일 연결은 Travel 내부 값 목록으로만 들고 가기에는 수량 증가, 위치별 조회, 파일 역할 관리가 섞인다.
파일 연결은 공통 `FileBox` 도메인으로 분리하고, Travel과 Trip은 FileBox를 통해 파일 묶음을 관리한다.

## 설계 원칙

- `Travel`과 `Trip`은 각각 도메인 aggregate root로 유지한다.
- `TravelPhoto`는 Travel 전용 도메인에서 제거하고 FileBox의 파일 연결 항목으로 흡수한다.
- `TravelDay`, `TravelPlace`, `TravelTag`, `TravelReview`는 Travel 내부 VO로 변경한다.
- `FileAsset`은 실제 업로드 파일 메타데이터의 source of truth로 유지한다.
- `FileBox`는 특정 도메인 객체가 어떤 파일들을 어떤 역할과 위치로 참조하는지 표현한다.
- `FileBox` 자체는 외부 API로 생성, 수정, 삭제하지 않는다.
- `FileBox`는 Travel/Trip 생성 요청 처리 과정에서 aggregate가 자동 생성한다.
- Travel/Trip 수정 요청에서는 FileBox 자체가 아니라 FileBox 내부 item 목록만 동기화한다.
- Spec 도메인은 persistence 테이블 구조를 직접 반영하지 않는다.
- DTO는 API 계약이고, VO는 도메인 값이다. 도메인 VO가 facade DTO를 import하지 않도록 유지한다.

## 대상 패키지

Spec 모듈에 아래 패키지를 추가한다.

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

`FileBoxFacade`는 만들지 않는다. FileBox는 Travel/Trip 요청 처리 과정에서 내부적으로 생성/수정되는 파일 연결 도메인이고, 외부 API 계약은 Travel/Trip DTO의 `files`
필드로만 노출한다.

`FileBoxRdo`도 기본 설계에는 포함하지 않는다. FE가 필요한 것은 FileBox 자체 정보가 아니라 Travel/Trip에 연결된 파일 item 목록과 조립된 이미지 정보다.

## FileBox 도메인 모델

### FileBox

`FileBox`는 파일 연결 aggregate root다.

```java
public class FileBox extends DomainEntity {
	private FileBoxOwnerType ownerType;
	private String ownerId;
	private List<FileBoxItem> items;
}
```

필드 의미:

- `ownerType`: 파일 묶음의 소유 도메인. 예: `TRAVEL`, `TRIP`
- `ownerId`: 소유 도메인 ID. 예: `TRAVEL-0001`, `TRIP-0001`
- `items`: 파일 연결 항목 목록

규칙:

- 한 owner는 기본적으로 하나의 FileBox를 가진다.
- FileBox ID를 Travel/Trip에 직접 저장하지 않고 `ownerType + ownerId`로 조회한다.
- Travel/Trip 생성 시 aggregate가 `ownerType + ownerId` 기준 FileBox를 자동 생성한다.
- Travel/Trip 수정 시 aggregate가 기존 FileBox를 조회하고 item 목록을 요청 기준으로 동기화한다.
- Travel/Trip 삭제 시 aggregate 단계에서 해당 owner의 FileBox와 items를 함께 삭제한다.
- 같은 owner 안에서 역할상 단건이어야 하는 항목은 role 정책으로 제한한다. 예: Trip logo, first map
- FileBox 자체의 생성/수정/삭제를 위한 Facade API는 제공하지 않는다.

### FileBoxItem

`FileBoxItem`은 FileBox 내부 VO다.

```java
public class FileBoxItem implements JsonSerializable {
	private String id;
	private String fileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private int sortOrder;
}
```

필드 의미:

- `id`: FileBox 내부 항목 식별자. 전체 수정 시 FE가 기존 항목을 식별할 수 있게 둔다.
- `fileAssetId`: `FileAsset.id`
- `targetType`: 파일이 붙는 도메인 내부 위치
- `targetId`: target의 안정 식별자. Travel day는 `yyyy-MM-dd` 날짜 문자열, Travel place는 `placeKey` UUID를 사용한다.
- `role`: 파일의 역할
- `caption`: 사진 설명
- `sortOrder`: 같은 target/role 내 정렬 순서

`FileBoxItem`을 `DomainEntity`로 만들지 않는 이유:

- 독립 API 생명주기를 갖지 않는다.
- owner FileBox 안에서만 의미가 있다.
- Travel 전체 저장 또는 Trip 전체 저장 시 함께 동기화된다.

`FileBoxItem.id`는 서버가 생성한 값만 허용한다. 생성 요청의 `FileBoxItemCdo`에는 id를 받지 않고, 수정 요청의 `FileBoxItemUdo.id`는 기존 item 식별 용도로만
사용한다. FE가 임의 생성한 id를 신규 item id로 채택하지 않는다.

`FileBoxItem.id`는 DB 자동 생성 값이 아니라 애플리케이션에서 생성한 UUID 문자열로 관리한다. Spec에서는 문자열 ID로만 표현하고, 실제 생성 시점은 aggregate 구현 단계에서
FileBox item 신규 저장 직전으로 둔다.

## Enum 설계

### FileBoxOwnerType

```java
public enum FileBoxOwnerType {
	TRAVEL,
	TRIP
}
```

### FileBoxTargetType

```java
public enum FileBoxTargetType {
	TRAVEL,
	TRAVEL_DAY,
	TRAVEL_PLACE,
	TRIP
}
```

설명:

- `TRAVEL`: 여행 전체 앨범 또는 여행 대표 이미지
- `TRAVEL_DAY`: 특정 여행 일자에 연결된 사진
- `TRAVEL_PLACE`: 특정 장소에 연결된 사진
- `TRIP`: Trip 자체에 연결된 파일

### FileBoxItemRole

```java
public enum FileBoxItemRole {
	COVER,
	GALLERY,
	LOGO,
	FIRST_MAP,
	SECOND_MAP
}
```

역할 정책:

- Travel
    - `COVER`: 여행/일자/장소 대표 사진
    - `GALLERY`: 일반 여행 사진
- Trip
    - `LOGO`: 나들이 로고
    - `FIRST_MAP`: 첫 번째 지도
    - `SECOND_MAP`: 두 번째 지도

Trip 파일 역할은 고정 필드 성격이 강하므로 `targetType=TRIP`, `role=LOGO/FIRST_MAP/SECOND_MAP` 조합으로 표현한다.

## Travel Spec 변경안

### 현재 모델 문제

현재 `slcn-spec`의 Travel 하위 모델은 모두 `DomainEntity`다.

- `TravelDay`
- `TravelPlace`
- `TravelPhoto`
- `TravelTag`
- `TravelReview`

하지만 공개 `TravelFacade`는 Travel 단위 전체 생성/수정/삭제/조회만 제공한다. 하위 객체를 별도 생명주기로 다루지 않으므로 `TravelPhoto`를 제외한 하위 모델은 Travel 내부 VO가
더 적합하다.

### 변경 후 Travel 도메인 구조

```java
public class Travel extends DomainEntity {
	private String title;
	private String region;
	private LocalDate startDate;
	private LocalDate endDate;
	private boolean hidden;
	private List<TravelDay> days;
	private List<String> tags;
	private TravelReview review;
}
```

`coverPhotoId`, `oneLineReview`는 제거한다.

- `coverPhotoId`: FileBox의 `role=COVER`로 이동한다.
- `oneLineReview`: `TravelReview.oneLineSummary`와 중복되므로 Travel root에서 제거한다.

### TravelDay VO

패키지:

```text
com.seoulchonnom.spec.travel.entity.vo.TravelDay
```

```java
public class TravelDay implements JsonSerializable {
	private LocalDate date;
	private String title;
	private String memo;
	private int dayNumber;
	private int sortOrder;
	private List<TravelPlace> places;
}
```

변경 포인트:

- `DomainEntity` 상속 제거
- `travelId` 제거
- 별도 `id` 제거. 여행 내부에서 날짜가 day의 안정 식별자 역할을 한다.
- `coverPhotoId` 제거. FileBox `targetType=TRAVEL_DAY`, `role=COVER`로 이동
- day 직접 사진은 FileBox `targetType=TRAVEL_DAY`, `role=GALLERY`로 이동

TravelDay 관련 FileBox item은 `targetType=TRAVEL_DAY`, `targetId={date}`를 사용한다. 날짜 포맷은 API의 기존 날짜 문자열과 동일하게 `yyyy-MM-dd`로
통일한다.

TravelDay의 `date`는 Travel 내부에서 unique 해야 하며, Travel 기간 안에 포함되어야 한다. `date`는 day의 식별자이므로 기존 day의 날짜 변경은 지원하지 않는다. 기간 수정으로
날짜 구성이 바뀌면 기존 날짜 삭제와 신규 날짜 생성으로 처리한다. 삭제되는 날짜에 연결된 FileBox item은 기존 기간 축소 정책과 함께 삭제 대상이 된다.

### TravelPlace VO

패키지:

```text
com.seoulchonnom.spec.travel.entity.vo.TravelPlace
```

```java
public class TravelPlace implements JsonSerializable {
	private String placeKey;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private int sortOrder;
}
```

변경 포인트:

- `DomainEntity` 상속 제거
- `travelId`, `travelDayId` 제거
- `id` 대신 `placeKey`를 사용한다.
- `coverPhotoId` 제거. FileBox `targetType=TRAVEL_PLACE`, `role=COVER`로 이동
- place 사진은 FileBox `targetType=TRAVEL_PLACE`, `role=GALLERY`로 이동

`placeKey`는 Travel 내부 place 안정 식별자다. 정렬 순서와 무관해야 하므로 `sortOrder`를 key로 사용하지 않는다. 신규 place 생성 요청에서 FE가 UUID를 생성해 전달하고,
서버는 해당 UUID를 그대로 저장한다.

TravelPlace 관련 FileBox item은 `targetType=TRAVEL_PLACE`, `targetId={placeKey}`를 사용한다.

`placeKey`는 UUID 문자열 형식이어야 하며, 같은 Travel 안에서 unique 해야 한다. `placeKey`는 생성 후 변경할 수 없다. 기존 place의 `placeKey`가 바뀌면 기존 place
삭제와 신규 place 생성으로 간주한다. 서버는 placeKey를 DB에서 생성하지 않는다. 신규 place와 그 place에 붙는 FileBox item을 같은 요청에서 함께 표현해야 하므로 FE가 UUID를
생성하고, 백엔드 애플리케이션은 형식과 중복만 검증한다.

### TravelReview VO

패키지:

```text
com.seoulchonnom.spec.travel.entity.vo.TravelReview
```

```java
public class TravelReview implements JsonSerializable {
	private String oneLineSummary;
	private String goodPoint;
	private String badPoint;
	private String revisitPlace;
	private String finalReview;
}
```

변경 포인트:

- `DomainEntity` 상속 제거
- `travelId`, `content` 제거
- Travel root의 `oneLineReview`와 중복 제거

### TravelTag

태그는 별도 클래스를 만들지 않고 `List<String> tags`로 표현한다. 태그 정렬은 리스트 순서로만 표현한다.

### TravelPhoto 제거

`com.seoulchonnom.spec.travel.entity.TravelPhoto`는 제거 대상이다.

대체:

- 여행 전체 사진: `FileBoxItem(targetType=TRAVEL, role=GALLERY)`
- 여행 대표 사진: `FileBoxItem(targetType=TRAVEL, role=COVER)`
- 일자 직접 사진: `FileBoxItem(targetType=TRAVEL_DAY, targetId=yyyy-MM-dd, role=GALLERY)`
- 일자 대표 사진: `FileBoxItem(targetType=TRAVEL_DAY, targetId=yyyy-MM-dd, role=COVER)`
- 장소 사진: `FileBoxItem(targetType=TRAVEL_PLACE, targetId=placeKey, role=GALLERY)`
- 장소 대표 사진: `FileBoxItem(targetType=TRAVEL_PLACE, targetId=placeKey, role=COVER)`

## Travel DTO 변경안

### 요청 DTO

`TravelCdo`, `TravelUdo`는 FileBox 항목을 포함한다.

```java
public class TravelCdo {
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private List<String> tags;
	private List<TravelDayUdo> travelDays;
	private TravelReviewUdo review;
	private List<FileBoxItemCdo> files;
}
```

```java
public class TravelUdo {
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private List<String> tags;
	private Boolean confirmDeleteDays;
	private List<TravelDayUdo> travelDays;
	private TravelReviewUdo review;
	private List<FileBoxItemUdo> files;
}
```

`coverPhotoId`, `photos`는 제거한다.

하위 DTO 변경:

- `TravelDayUdo.coverPhotoId` 제거
- `TravelDayUdo.photos` 제거
- `TravelPlaceUdo.coverPhotoId` 제거
- `TravelPlaceUdo.photos` 제거
- `TravelDayUdo`는 `date`를 day 식별자로 사용한다.
- `TravelPlaceUdo`는 `placeKey`를 place 식별자로 사용한다.
- 사진 관련 입력은 모두 `files`에서 표현한다.

`TravelDayUdo`:

```java
public class TravelDayUdo {
	private String date;
	private String title;
	private String memo;
	private Integer sortOrder;
	private List<TravelPlaceUdo> places;
}
```

`TravelPlaceUdo`:

```java
public class TravelPlaceUdo {
	private String placeKey;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private Integer sortOrder;
}
```

신규 place를 포함해 파일을 함께 연결해야 하므로 FE는 신규 place에도 UUID `placeKey`를 생성해 전달한다. 서버는 해당 `placeKey`를 Travel 내부 VO의 안정 식별자로 저장한다.

### 응답 DTO

상세 응답은 화면 편의를 위해 FileBoxItem을 조립해 내려준다.

응답 형태:

```java
public class TravelDetailRdo {
	private String id;
	private String travelId;
	private String title;
	private String region;
	private String startDate;
	private String endDate;
	private FileBoxItemRdo cover;
	private String oneLineReview;
	private int nights;
	private int days;
	private List<TravelDayRdo> travelDays;
	private List<String> tags;
	private TravelReviewRdo review;
	private List<FileBoxItemRdo> files;
}
```

`oneLineReview` 응답 필드는 하위 호환을 위해 유지한다. 값은 `review.oneLineSummary`에서 파생한다. Travel root 도메인에는 `oneLineReview`를 별도 필드로 저장하지
않는다.

원본 `files` 목록은 유지하고, nested `cover/photos`도 함께 내려준다. 페이징은 적용하지 않는다.

기존 최상위 `places`, `photos` 중복 목록은 제거한다. 장소는 `travelDays[].places`에 포함하고, 사진은 `files` 원본 목록과 nested `cover/photos`로 표현한다.

`TravelDayRdo`:

```java
public class TravelDayRdo {
	private String date;
	private String title;
	private String memo;
	private int dayNumber;
	private int sortOrder;
	private FileBoxItemRdo cover;
	private List<FileBoxItemRdo> photos;
	private List<TravelPlaceRdo> places;
}
```

`TravelPlaceRdo`:

```java
public class TravelPlaceRdo {
	private String placeKey;
	private String name;
	private TravelPlaceCategory category;
	private String address;
	private String memo;
	private String description;
	private int sortOrder;
	private FileBoxItemRdo cover;
	private List<FileBoxItemRdo> photos;
}
```

상세 응답은 `files` 원본 목록과 조립된 `cover/photos`를 둘 다 내려준다.

- `files`: owner에 연결된 모든 FileBox item 원본 목록
- nested `cover/photos`: FE 화면 렌더링 편의를 위해 target별로 조립한 이미지 목록
- 요청/수정의 source of truth는 `files`다.
- nested `cover/photos`는 서버가 `files`에서 조립한 read-only projection이다.
- 같은 `FileBoxItem.id`가 `files`와 nested `cover/photos`에 반복될 수 있다.
- 응답 불일치가 발생하면 `files`를 기준으로 판단한다.
- 초기 설계에서는 파일 item 페이징을 적용하지 않는다.

## Trip Spec 변경안

### 현재 모델 문제

`Trip`은 `logoFileId`, `firstMapFileId`, `secondMapFileId`를 직접 가진다. 파일 수가 고정이고 의미가 명확하다는 장점은 있지만, 파일 연결 정책이 Travel과 분리되어
중복된다.

Trip도 FileBox를 통해 파일 역할을 관리한다.

### 변경 후 Trip 도메인 구조

```java
public class Trip extends DomainEntity {
	private String date;
	private String type;
	private String name;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private Quiz quiz;
}
```

제거:

- `logoFileId`
- `firstMapFileId`
- `secondMapFileId`

대체:

- `FileBoxItem(targetType=TRIP, role=LOGO)`
- `FileBoxItem(targetType=TRIP, role=FIRST_MAP)`
- `FileBoxItem(targetType=TRIP, role=SECOND_MAP)`

### Trip DTO 변경안

`TripCdo`:

```java
public class TripCdo {
	private String date;
	private String type;
	private String name;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private QuizCdo quiz;
	private List<FileBoxItemCdo> files;
}
```

`TripUdo`:

```java
public class TripUdo {
	private String date;
	private String type;
	private String name;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
	private QuizCdo quiz;
	private List<FileBoxItemUdo> files;
}
```

현재 코드에는 Trip 수정 API가 없지만, Trip 수정 API를 추가할 계획이 있으므로 Spec 설계에는 `TripUdo`를 포함한다. Trip 수정 시 FileBox 자체가 아니라 `files` item
목록을 요청 기준으로 동기화한다.

`TripDetailRdo`:

```java
public class TripDetailRdo {
	private String id;
	private String date;
	private String type;
	private String name;
	private FileAssetRdo logo;
	private FileAssetRdo firstMap;
	private FileAssetRdo secondMap;
	private String nextButtonText;
	private String previousButtonText;
	private String driveUrl;
}
```

Trip은 역할별 파일이 단건이어야 한다.

- `LOGO`: 필수 1개
- `FIRST_MAP`: 필수 1개
- `SECOND_MAP`: 선택 0 또는 1개

기존 navigation 정책은 유지한다.

- `SECOND_MAP`, `nextButtonText`, `previousButtonText`는 모두 있거나 모두 없어야 한다.

실제 Trip 파일 연결 정보는 FileBox 저장소에만 저장한다. Application 계층은 Trip 조회 시 FileBox를 함께 조회하고, role별 item의 `FileAsset`을 `logo`,
`firstMap`, `secondMap`으로 조립해 FE에는 `FileAssetRdo` 이미지 정보만 내려준다. Trip 상세 응답에는 FileBox item의 id, role, targetType 같은 연결
메타데이터를 노출하지 않는다.

## FileBox DTO

### FileBoxItemCdo

```java
public class FileBoxItemCdo {
	private String fileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private Integer sortOrder;
}
```

### FileBoxItemUdo

```java
public class FileBoxItemUdo {
	private String id;
	private String fileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private Integer sortOrder;
}
```

`FileBoxItemUdo.id`는 기존 서버 생성 item 식별자다. 신규 item은 id 없이 전달한다. 서버는 신규 item 저장 시 id를 생성한다.

### FileBoxItemRdo

```java
public class FileBoxItemRdo {
	private String id;
	private String fileAssetId;
	private FileBoxTargetType targetType;
	private String targetId;
	private FileBoxItemRole role;
	private String caption;
	private int sortOrder;
	private FileAssetRdo file;
}
```

`FileBoxItemRdo`는 Travel 상세 응답의 `files`, nested `cover/photos`에서 사용한다. `FileAssetRdo file`을 포함하므로 FE는 별도 파일 메타 조회 없이 이미지
렌더링에 필요한 정보를 받을 수 있다.

Trip 상세 응답은 `FileBoxItemRdo`를 노출하지 않고 role별 `FileAssetRdo`를 직접 내려준다.

## Mapper 책임

Spec mapper는 도메인 모델과 API DTO 변환만 담당한다.

Mapper:

- `FileBoxMapper`
    - `FileBoxItem -> FileBoxItemRdo`
    - `FileBoxItemCdo/Udo -> FileBoxItem`
- `TravelMapper`
    - `Travel + FileBox -> TravelDetailRdo`
    - `Travel + FileBox -> TravelRdo`
    - Travel 내부 VO를 nested RDO로 변환
- `TripMapper`
    - `Trip + FileBox + FileAsset -> TripDetailRdo`
    - `Trip + FileBox + FileAsset -> TripListRdo`
- mapper는 필드 변환만 수행한다.
- 신규 FileBox item의 id 금지, 기존 item id의 owner 소유 검증, target 존재 검증은 aggregate 단계에서 수행한다.

주의:

- `Travel` domain entity가 `FileBoxItemCdo`, `FileBoxItemRdo` 같은 DTO를 import하면 안 된다.
- `FileBoxItem`은 `FileAssetRdo`를 직접 들지 않는다.
- DTO에 `FileAssetRdo`를 포함하는 것은 mapper 단계에서만 처리한다.

## 검증 규칙

Spec 기준으로 표현할 규칙:

- `FileBoxItem.fileAssetId`는 필수다.
- `FileBoxItem.id`는 서버가 생성한다.
- `FileBoxItem.targetType`은 필수다.
- `FileBoxItem.role`은 필수다.
- `targetType=TRAVEL_DAY` 또는 `TRAVEL_PLACE`이면 `targetId`가 필수다.
- `targetType=TRAVEL_DAY`이면 `targetId`는 `yyyy-MM-dd` 날짜 문자열이어야 한다.
- `targetType=TRAVEL_PLACE`이면 `targetId`는 해당 TravelPlace의 `placeKey` UUID여야 한다.
- `targetType=TRAVEL` 또는 `TRIP`이면 `targetId`는 null로 둔다.
- `TravelDay.date`는 같은 Travel 안에서 unique 해야 하며 Travel 기간 안에 있어야 한다.
- 기존 TravelDay의 date 변경은 지원하지 않고, 기존 날짜 삭제와 신규 날짜 생성으로 처리한다.
- `TravelPlace.placeKey`는 UUID 문자열 형식이어야 한다.
- `TravelPlace.placeKey`는 같은 Travel 안에서 unique 해야 한다.
- 기존 TravelPlace의 placeKey 변경은 지원하지 않고, 기존 place 삭제와 신규 place 생성으로 처리한다.
- 같은 owner 안에서 `targetType + targetId + role + fileAssetId` 중복은 허용하지 않는다.
- `sortOrder`가 없으면 같은 target/role 내 순서대로 부여한다.
- Trip의 `LOGO`, `FIRST_MAP`은 필수 단건이다.
- Trip의 `SECOND_MAP`은 선택 단건이다.

실제 검증 구현 위치는 aggregate 단계에서 결정한다.

## 단계별 Spec 작업 순서

1. `filebox` spec 패키지를 추가한다.
2. `FileBox`, `FileBoxItem`, owner/target/role enum을 추가한다.
3. `FileBoxItemCdo/Udo/Rdo`를 추가한다.
4. `TravelPhoto` domain entity를 제거하고 FileBoxItem DTO로 요청/응답을 대체한다.
5. `TravelDay`, `TravelPlace`, `TravelReview`를 `entity.vo`로 이동하고 `DomainEntity` 상속을 제거한다.
6. `Travel`에 `days`, `tags`, `review`를 포함하도록 spec 모델을 정리한다.
7. `TravelCdo`, `TravelUdo`, 하위 Travel DTO에서 `coverPhotoId`, `photos` 필드를 FileBox item 목록으로 대체한다.
8. `TravelDetailRdo`에서 원본 `files`와 nested `cover/photos`를 모두 표현하고, 기존 최상위 `places`, `photos` 중복 목록은 제거한다.
9. `Trip`에서 직접 파일 ID 필드를 제거하고 FileBox item 기반 요청 DTO와 `FileAssetRdo` 기반 응답 DTO로 전환한다.
10. `TravelMapper`, `TripMapper`, `FileBoxMapper`의 계약을 새 모델에 맞춘다.

## 확정 사항

- FileBox 자체는 별도 API로 생성, 수정, 삭제하지 않는다.
- Travel/Trip 생성 요청 시 aggregate가 FileBox를 자동 생성한다.
- Travel/Trip 수정 요청 시 aggregate가 FileBox item 목록을 동기화한다.
- Travel/Trip 삭제 시 aggregate가 FileBox와 items를 함께 삭제한다.
- `FileBoxItem.id`는 서버 생성 값만 허용한다.
- `FileBoxItem.id`는 애플리케이션에서 UUID로 생성한다.
- `TravelDay`는 별도 id를 두지 않고 날짜를 FileBox target 식별자로 사용한다.
- `TravelPlace`는 `placeKey` UUID를 FileBox target 식별자로 사용한다.
- 신규 Travel 생성/수정 요청에서 신규 place를 함께 보낼 때 FE는 UUID `placeKey`를 생성해 전달한다.
- `TravelDay.date`와 `TravelPlace.placeKey`는 생성 후 변경하지 않고, 변경 시 삭제+신규 생성으로 처리한다.
- Travel 상세 응답은 원본 `files` 목록과 nested `cover/photos`를 모두 내려준다.
- Travel nested `cover/photos`는 `files`에서 파생된 read-only projection이다.
- 파일 item 페이징은 초기 설계에 포함하지 않는다.
- Trip의 고정 파일 역할은 전부 FileBox로 옮긴다.
- Trip 실제 파일 연결 정보는 FileBox 저장소에만 저장하고, Application에서 조회/조립해 FE에는 role별 `FileAssetRdo` 이미지 응답을 내려준다.

## 결론

Spec 기준 설계 결론은 다음과 같다.

- Travel은 root entity 하나와 내부 VO들로 재구성한다.
- Travel 사진은 `TravelPhoto`가 아니라 `FileBoxItem`으로 표현한다.
- Trip 파일도 FileBox role 기반으로 통합한다.
- FileAsset은 계속 업로드 파일의 source of truth로 유지하고, FileBox는 도메인별 파일 연결 source of truth가 된다.
- API 요청은 Travel/Trip 단위로 유지하되, 파일 연결 payload만 공통 FileBox DTO로 통일한다.
- FileBox 자체는 내부 도메인으로만 다루고 외부 API로 직접 공개하지 않는다.
