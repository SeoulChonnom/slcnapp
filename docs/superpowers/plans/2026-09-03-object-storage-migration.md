# Object Storage Migration Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이미지 원본과 파생본의 저장소를 서버 로컬 디스크에서 오브젝트 스토리지(Cloudflare R2)로 옮기고, 원본 조회·다운로드를 presigned URL 302 리다이렉트로 전환해 서버 디스크 용량과 힙 메모리 문제를 없앤다.

**Architecture:** `ObjectStorage` 포트를 두고 `LocalFileObjectStorage`(개발·폴백)와 `R2ObjectStorage`(운영) 두 어댑터를 `slcn-aggregate`가 소유한다. 업로드는 임시 디렉터리에 원본을 받아 파생본을 만든 뒤 두 종류를 각각 `originals/`·`derived/` prefix로 올린다. 조회는 파생본만 Spring이 바이트로 서빙하고(기존 ETag/304 유지), 원본은 presigned URL을 만들어 302로 넘긴다. 기존 로컬 파일은 일회성 `ApplicationRunner`가 백필한다.

**Tech Stack:** Java 17, Spring Boot 3.5.12, Gradle 멀티모듈, AWS SDK for Java v2 (S3 호환 클라이언트 + presigner), MongoDB, Lombok, JUnit 5, Mockito, AssertJ

**Spec:** `docs/superpowers/specs/2026-09-03-object-storage-migration-design.md`

## Global Constraints

- 이 계획은 **Phase 1만** 구현한다. 상태머신(D5), presigned 업로드(D9), 완료 통보·reconciliation(D10), 워커(D8), CDN·서명 쿠키(D7), Photo-Rendition 모델(D3/D4)은 범위 밖이다.
- **업로드 크기 제한을 올리지 않는다.** `FileConstant.MAX_FILE_SIZE = 10 * 1024 * 1024L`와 `spring.servlet.multipart.max-file-size: 10MB`를 그대로 둔다. 제한 상향은 Phase 3의 몫이다.
- **허용 확장자를 바꾸지 않는다.** `FileConstant.EXT_REGEX_STRING = "jpg|png|jpeg|gif|svg"` 그대로다. RAW/HEIF 허용과 SVG 제거는 Phase 3의 몫이다.
- **API 계약(경로·파라미터·응답 바디)을 바꾸지 않는다.** `FileFacade`의 메서드 시그니처는 그대로 두고, 원본 조회만 200 바이트 응답 대신 302 리다이렉트를 반환한다.
- 오브젝트 키는 원본 `originals/{type}/{storedFilename}`, 파생본 `derived/{type}/{variantFilename}` 두 prefix로 나눈다. Phase 2에서 파생본에만 CDN·캐시 정책을 걸어야 하므로 지금 나눠 둔다.
- presigned URL 만료는 기본 300초다. 만료는 요청 시작 시점에만 검사되므로 전송 중 끊기지 않는다.
- presigned 302 응답에는 **ETag를 붙이지 않고 `Cache-Control: no-store`를 건다.** URL이 만료되므로 캐시하면 안 된다.
- 파생본 응답은 지금과 동일하게 바이트·ETag·`Cache-Control: private, max-age=86400`을 유지한다.
- `slcn.storage.provider`가 `r2`가 아니면 `LocalFileObjectStorage`가 선택되고, presigned URL을 만들 수 없으므로 지금과 같이 바이트를 서빙한다. **테스트와 로컬 개발이 R2 자격 증명 없이 그대로 돌아가야 한다.**
- 메타데이터 저장(`FileAssetStore.save`)은 오브젝트 업로드가 모두 끝난 뒤에 한다. 메타데이터가 없는 객체(고아)는 Phase 3의 reconciliation이 정리하고, 객체가 없는 메타데이터는 만들지 않는다.
- 모듈 의존 방향을 지킨다: `slcn-boot -> slcn-rest -> slcn-auth -> slcn-aggregate -> slcn-spec`. 새 모듈 간 의존을 추가하지 않는다.
- 들여쓰기는 탭이다. 주석은 한국어로, "왜"를 쓴다. 기존 파일의 주석 밀도를 따른다.
- 테스트 이름은 `action_shouldExpectedResult` 형식이다.
- 베이스라인은 `./gradlew test` 기준 353 tests / 0 failures다. 각 태스크 종료 시 이 수치는 늘기만 하고 실패는 0이어야 한다.

---

## File Structure

### Create

- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/ObjectStorage.java`: 오브젝트 스토리지 포트. put/getBytes/exists/presignedGetUrl 4개만 갖는다.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/ObjectKeys.java`: 원본/파생본 키 규칙을 한곳에 모은다.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/MimeTypes.java`: 파일명 확장자 → MIME 매핑. 경로 기반 조회에서 메타데이터 없이 MIME을 정해야 한다.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorage.java`: 로컬 디스크 어댑터.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorage.java`: S3 호환 어댑터.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/ObjectStorageConfiguration.java`: 어댑터 선택과 SDK 클라이언트 조립, 백필 러너 등록.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogic.java`: 기존 로컬 파일을 오브젝트 스토리지로 백필한다.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/ObjectKeysTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/MimeTypesTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorageTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorageTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogicTest.java`

### Modify

- `slcn-spec/src/main/java/com/seoulchonnom/spec/file/constant/FileConstant.java`: 파생본 파일명 전용 정규식 상수를 공개한다.
- `slcn-spec/src/main/java/com/seoulchonnom/spec/file/facade/sdo/ImageFileRdo.java`: `redirectUrl` 필드를 추가한다.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/util/FileUtils.java`: 업로드를 임시 디렉터리로 스테이징하고, 디스크 경로 의존(`directory`, `resolvePath`, `existsFileRef`, `saveImages`)을 걷어낸다.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileLogic.java`: 업로드/조회를 `ObjectStorage`로 옮기고 원본은 presigned 리다이렉트를 반환한다.
- `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/store/FileAssetStore.java`: 백필용 페이지 조회를 추가한다.
- `slcn-aggregate/build.gradle`: AWS SDK와 spring-boot-autoconfigure 의존성을 추가한다.
- `slcn-rest/src/main/java/com/seoulchonnom/rest/file/FileResource.java`: `redirectUrl`이 있으면 302를 반환하고, 다운로드는 `downloadImageFileById`를 호출한다.
- `slcn-boot/src/main/resources/application.yml`: `slcn.storage.*`와 `max-request-size`를 추가한다.
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/util/FileUtilsTest.java`
- `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileLogicTest.java`
- `slcn-rest/src/test/java/com/seoulchonnom/rest/file/FileResourceTest.java`
- `docs/file-asset.md`, `docs/image-asset-api.md`: 저장 위치와 302 동작을 반영한다.

---

## Task 1: 오브젝트 키와 MIME 규칙

**Files:**
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/ObjectStorage.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/ObjectKeys.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/MimeTypes.java`
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/file/constant/FileConstant.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/ObjectKeysTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/MimeTypesTest.java`

**Interfaces:**
- Consumes: `FileConstant`의 기존 private 상수 `UUID_REGEX_STRING`, `STORED_EXT_REGEX_STRING`.
- Produces:
  - `FileConstant.VARIANT_FILE_NAME_REGEX_STRING` (public static final String)
  - `interface ObjectStorage`: `void put(String key, Path source, String contentType) throws IOException`, `byte[] getBytes(String key) throws IOException`, `boolean exists(String key)`, `Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition)`
  - `ObjectKeys.ORIGINAL_PREFIX` = `"originals/"`, `ObjectKeys.DERIVED_PREFIX` = `"derived/"`
  - `String ObjectKeys.original(String type, String storedFilename)`
  - `String ObjectKeys.derived(String type, String variantFilename)`
  - `String ObjectKeys.of(String type, String filename)`
  - `boolean ObjectKeys.isDerived(String key)`
  - `String MimeTypes.ofFilename(String filename)`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/ObjectKeysTest.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ObjectKeysTest {
	private static final String UUID_NAME = "72d768d4-2b05-48f9-bee8-fee3b52e909f";

	@Test
	void original_shouldPlaceFileUnderOriginalsPrefix() {
		assertThat(ObjectKeys.original("travel", UUID_NAME + ".png"))
			.isEqualTo("originals/travel/" + UUID_NAME + ".png");
	}

	@Test
	void derived_shouldPlaceFileUnderDerivedPrefix() {
		assertThat(ObjectKeys.derived("travel", UUID_NAME + "_home-thumb.webp"))
			.isEqualTo("derived/travel/" + UUID_NAME + "_home-thumb.webp");
	}

	@Test
	void of_shouldChoosePrefixByVariantSuffix() {
		assertThat(ObjectKeys.of("travel", UUID_NAME + ".png"))
			.isEqualTo("originals/travel/" + UUID_NAME + ".png");
		assertThat(ObjectKeys.of("travel", UUID_NAME + "_home-feature.webp"))
			.isEqualTo("derived/travel/" + UUID_NAME + "_home-feature.webp");
	}

	@Test
	void of_shouldTreatUnknownShapesAsOriginalSoValidationStaysTheSingleGate() {
		assertThat(ObjectKeys.of("travel", "not-a-uuid.png"))
			.isEqualTo("originals/travel/not-a-uuid.png");
	}

	@Test
	void isDerived_shouldReflectThePrefix() {
		assertThat(ObjectKeys.isDerived("derived/travel/" + UUID_NAME + "_home-thumb.webp")).isTrue();
		assertThat(ObjectKeys.isDerived("originals/travel/" + UUID_NAME + ".png")).isFalse();
	}
}
```

`slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/MimeTypesTest.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MimeTypesTest {
	@Test
	void ofFilename_shouldMapKnownImageExtensions() {
		assertThat(MimeTypes.ofFilename("a.jpg")).isEqualTo("image/jpeg");
		assertThat(MimeTypes.ofFilename("a.jpeg")).isEqualTo("image/jpeg");
		assertThat(MimeTypes.ofFilename("a.png")).isEqualTo("image/png");
		assertThat(MimeTypes.ofFilename("a.gif")).isEqualTo("image/gif");
		assertThat(MimeTypes.ofFilename("a.svg")).isEqualTo("image/svg+xml");
		assertThat(MimeTypes.ofFilename("a.webp")).isEqualTo("image/webp");
	}

	@Test
	void ofFilename_shouldIgnoreCase() {
		assertThat(MimeTypes.ofFilename("A.PNG")).isEqualTo("image/png");
	}

	@Test
	void ofFilename_shouldFallBackToOctetStreamWhenExtensionIsUnknownOrMissing() {
		assertThat(MimeTypes.ofFilename("a.bin")).isEqualTo("application/octet-stream");
		assertThat(MimeTypes.ofFilename("noextension")).isEqualTo("application/octet-stream");
		assertThat(MimeTypes.ofFilename(null)).isEqualTo("application/octet-stream");
	}
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*ObjectKeysTest' --tests '*MimeTypesTest'`
Expected: 컴파일 실패 — `cannot find symbol: class ObjectKeys` / `class MimeTypes`

- [ ] **Step 3: `FileConstant`에 파생본 파일명 정규식을 추가한다**

`slcn-spec/src/main/java/com/seoulchonnom/spec/file/constant/FileConstant.java`의 `FILE_PATH_REGEX_STRING` 선언 바로 아래에 추가한다.

```java
	/**
	 * 파생본 저장 파일명. 원본과 달리 {uuid}_{variant} 접미사를 반드시 갖는다.
	 * 파일명만 받는 경로 기반 조회에서 원본과 파생본의 저장 prefix를 갈라야 하므로 별도로 둔다.
	 */
	public static final String VARIANT_FILE_NAME_REGEX_STRING =
		UUID_REGEX_STRING + "_[a-z][a-z0-9-]{1,30}\\.(" + STORED_EXT_REGEX_STRING + ")";
```

- [ ] **Step 4: 포트와 키·MIME 규칙을 구현한다**

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/ObjectStorage.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * 이미지 바이트의 저장소. 구현은 로컬 디스크와 S3 호환 오브젝트 스토리지 두 가지다.
 * 어느 쪽을 쓸지는 인프라 결정이므로 호출자는 이 포트만 안다.
 */
public interface ObjectStorage {
	/**
	 * 업로드는 항상 임시 파일에서 올린다. 서버 힙에 바이트를 올리지 않기 위함이다.
	 */
	void put(String key, Path source, String contentType) throws IOException;

	/**
	 * 파생본처럼 작은 객체만 읽는다. 원본은 presignedGetUrl로 넘긴다.
	 */
	byte[] getBytes(String key) throws IOException;

	boolean exists(String key);

	/**
	 * 서명된 조회 URL. 서명을 지원하지 않는 구현은 비어 있는 값을 돌려주고,
	 * 호출자는 지금까지처럼 바이트를 직접 읽어 응답한다.
	 *
	 * @param contentDisposition 첨부 다운로드로 내려줄 때의 Content-Disposition 값. 인라인 조회면 null이다.
	 */
	Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition);
}
```

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/ObjectKeys.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import static com.seoulchonnom.spec.file.constant.FileConstant.*;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 오브젝트 키 규칙을 한곳에 모은다.
 * 원본과 파생본을 다른 prefix에 둬야 파생본에만 CDN과 캐시 정책을 걸 수 있다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ObjectKeys {
	public static final String ORIGINAL_PREFIX = "originals/";
	public static final String DERIVED_PREFIX = "derived/";

	public static String original(String type, String storedFilename) {
		return ORIGINAL_PREFIX + type + "/" + storedFilename;
	}

	public static String derived(String type, String variantFilename) {
		return DERIVED_PREFIX + type + "/" + variantFilename;
	}

	/**
	 * 경로 기반 조회는 파일명만 받으므로 파생본 접미사 유무로 prefix를 고른다.
	 * 형식을 벗어난 이름은 원본으로 취급한다. 형식 검증은 FileUtils.isValidFileRef가 이미 담당한다.
	 */
	public static String of(String type, String filename) {
		return filename != null && filename.matches(VARIANT_FILE_NAME_REGEX_STRING)
			? derived(type, filename)
			: original(type, filename);
	}

	public static boolean isDerived(String key) {
		return key != null && key.startsWith(DERIVED_PREFIX);
	}
}
```

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/MimeTypes.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import java.util.Locale;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 파일명에서 MIME 타입을 정한다.
 * 경로 기반 조회는 FileAsset 메타데이터 없이 파일명만 받으므로 확장자로 판단할 수밖에 없다.
 * JDK의 probeContentType은 OS 설정에 따라 webp에서 null을 돌려주므로 쓰지 않는다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MimeTypes {
	public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

	private static final Map<String, String> BY_EXTENSION = Map.of(
		"jpg", "image/jpeg",
		"jpeg", "image/jpeg",
		"png", "image/png",
		"gif", "image/gif",
		"svg", "image/svg+xml",
		"webp", "image/webp"
	);

	public static String ofFilename(String filename) {
		if (filename == null) {
			return DEFAULT_MIME_TYPE;
		}

		int pos = filename.lastIndexOf('.');
		if (pos < 0 || pos == filename.length() - 1) {
			return DEFAULT_MIME_TYPE;
		}

		String extension = filename.substring(pos + 1).toLowerCase(Locale.ROOT);
		return BY_EXTENSION.getOrDefault(extension, DEFAULT_MIME_TYPE);
	}
}
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*ObjectKeysTest' --tests '*MimeTypesTest' && ./gradlew :slcn-spec:test`
Expected: 둘 다 PASS. `--tests` 필터는 대상 태스크에 매칭되는 테스트가 없으면 실패하므로 spec 모듈은 따로 돌린다.

- [ ] **Step 6: 커밋한다**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/file/constant/FileConstant.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage
git commit -m "feat: 오브젝트 스토리지 포트와 키 규칙 추가"
```

---

## Task 2: 로컬 디스크 어댑터

**Files:**
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorage.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorageTest.java`

**Interfaces:**
- Consumes: `ObjectStorage` (Task 1).
- Produces: `LocalFileObjectStorage(Path baseDirectory)` — 생성자 하나. 스프링 빈 애너테이션을 붙이지 않는다. 어댑터 선택은 Task 3의 설정 클래스가 한다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorageTest.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileObjectStorageTest {
	@TempDir
	Path tempDir;

	private LocalFileObjectStorage storage;

	@BeforeEach
	void setUp() {
		storage = new LocalFileObjectStorage(tempDir);
	}

	@Test
	void put_shouldCreateMissingDirectoriesAndCopyTheSource() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {1, 2, 3});

		storage.put("originals/travel/a.png", source, "image/png");

		assertThat(tempDir.resolve("originals/travel/a.png")).exists();
		assertThat(Files.readAllBytes(tempDir.resolve("originals/travel/a.png")))
			.containsExactly(1, 2, 3);
	}

	@Test
	void put_shouldOverwriteAnExistingObjectSoRetriesAreSafe() throws Exception {
		Path first = Files.write(tempDir.resolve("first.bin"), new byte[] {1});
		Path second = Files.write(tempDir.resolve("second.bin"), new byte[] {2});

		storage.put("originals/travel/a.png", first, "image/png");
		storage.put("originals/travel/a.png", second, "image/png");

		assertThat(storage.getBytes("originals/travel/a.png")).containsExactly(2);
	}

	@Test
	void getBytes_shouldReturnStoredBytes() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {9, 9});
		storage.put("derived/travel/a_home-thumb.webp", source, "image/webp");

		assertThat(storage.getBytes("derived/travel/a_home-thumb.webp")).containsExactly(9, 9);
	}

	@Test
	void getBytes_shouldThrowWhenObjectIsMissing() {
		assertThatThrownBy(() -> storage.getBytes("originals/travel/absent.png"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void exists_shouldReflectObjectPresence() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {1});
		storage.put("originals/travel/a.png", source, "image/png");

		assertThat(storage.exists("originals/travel/a.png")).isTrue();
		assertThat(storage.exists("originals/travel/b.png")).isFalse();
	}

	@Test
	void presignedGetUrl_shouldBeEmptySoCallersServeBytesThemselves() {
		assertThat(storage.presignedGetUrl("originals/travel/a.png", Duration.ofMinutes(5), null))
			.isEmpty();
	}

	@Test
	void put_shouldRejectKeysThatEscapeTheBaseDirectory() throws Exception {
		Path source = Files.write(tempDir.resolve("source.bin"), new byte[] {1});

		assertThatThrownBy(() -> storage.put("../escaped.png", source, "image/png"))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*LocalFileObjectStorageTest'`
Expected: 컴파일 실패 — `cannot find symbol: class LocalFileObjectStorage`

- [ ] **Step 3: 구현한다**

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorage.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

/**
 * 로컬 디스크 어댑터. 개발 환경과 R2 자격 증명이 없는 환경에서 쓴다.
 * 서명 URL을 만들 수 없으므로 presignedGetUrl은 항상 비어 있고, 호출자가 바이트를 직접 서빙한다.
 */
@RequiredArgsConstructor
public class LocalFileObjectStorage implements ObjectStorage {
	private final Path baseDirectory;

	@Override
	public void put(String key, Path source, String contentType) throws IOException {
		Path target = resolve(key);
		Files.createDirectories(target.getParent());
		Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	@Override
	public byte[] getBytes(String key) throws IOException {
		return Files.readAllBytes(resolve(key));
	}

	@Override
	public boolean exists(String key) {
		return Files.exists(resolve(key));
	}

	@Override
	public Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition) {
		return Optional.empty();
	}

	/**
	 * 키에 상위 경로가 섞여 들어와도 기준 디렉터리를 벗어나지 못하게 한다.
	 */
	private Path resolve(String key) {
		Path resolved = baseDirectory.resolve(key).normalize();
		if (!resolved.startsWith(baseDirectory)) {
			throw new IllegalArgumentException("Object key escapes base directory: " + key);
		}
		return resolved;
	}
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*LocalFileObjectStorageTest'`
Expected: PASS

- [ ] **Step 5: 커밋한다**

```bash
git add slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorage.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/LocalFileObjectStorageTest.java
git commit -m "feat: 로컬 디스크 ObjectStorage 어댑터 추가"
```

---

## Task 3: R2 어댑터와 부트 조립

**Files:**
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorage.java`
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/ObjectStorageConfiguration.java`
- Modify: `slcn-aggregate/build.gradle`
- Modify: `slcn-boot/src/main/resources/application.yml`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorageTest.java`

**Interfaces:**
- Consumes: `ObjectStorage` (Task 1), `LocalFileObjectStorage(Path)` (Task 2).
- Produces:
  - `R2ObjectStorage(S3Client s3Client, S3Presigner s3Presigner, String bucket)`
  - `ObjectStorageConfiguration#objectStorage(...)` — `ObjectStorage` 타입 빈 하나. Task 4 이후 `FileLogic`이 이 빈을 주입받는다.
  - 설정 키: `slcn.storage.provider`(기본 `local`), `slcn.storage.presigned-ttl-seconds`(기본 `300`), `slcn.storage.r2.endpoint|region|bucket|access-key|secret-key`

**설계 메모:** 어댑터 선택을 `slcn-boot`가 아니라 `slcn-aggregate/config`에 둔다. `slcn-boot`는 `slcn-rest`만 의존하므로 aggregate 타입이 컴파일 경로에 없고, 그것을 뚫으려면 모듈 의존을 추가해야 한다. 오브젝트 스토리지 어댑터는 `AggregateConfiguration`의 JPA/Mongo 스캔과 같은 범주의 영속성 인프라이므로 aggregate가 소유하는 편이 의존 그래프를 건드리지 않는다.

- [ ] **Step 1: AWS SDK 의존성을 추가한다**

`slcn-aggregate/build.gradle`의 `dependencies` 블록에서 `webp-imageio` 선언 아래에 추가한다.

```gradle
    // S3 호환 오브젝트 스토리지(Cloudflare R2) 클라이언트와 presigner.
    // R2와 S3가 같은 API이므로 endpoint만 바꿔 어느 쪽으로도 전환할 수 있다.
    implementation platform('software.amazon.awssdk:bom:2.54.11')
    implementation 'software.amazon.awssdk:s3'
```

- [ ] **Step 2: 의존성이 해석되는지 확인한다**

Run: `./gradlew :slcn-aggregate:dependencies --configuration compileClasspath | grep awssdk | head`
Expected: `software.amazon.awssdk:s3` 및 전이 의존성이 `2.54.11`로 출력된다

- [ ] **Step 3: 실패하는 테스트를 쓴다**

`slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorageTest.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class R2ObjectStorageTest {
	private final S3Client s3Client = mock(S3Client.class);
	private final S3Presigner s3Presigner = mock(S3Presigner.class);
	private final R2ObjectStorage storage = new R2ObjectStorage(s3Client, s3Presigner, "slcn-media");

	@TempDir
	Path tempDir;

	@Test
	void put_shouldSendBucketKeyAndContentType() throws Exception {
		Path source = Files.write(tempDir.resolve("a.png"), new byte[] {1, 2, 3});
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenReturn(PutObjectResponse.builder().build());

		storage.put("originals/travel/a.png", source, "image/png");

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
		assertThat(captor.getValue().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().key()).isEqualTo("originals/travel/a.png");
		assertThat(captor.getValue().contentType()).isEqualTo("image/png");
	}

	@Test
	void put_shouldWrapSdkFailuresAsIoExceptionSoCallersHandleOneType() throws Exception {
		Path source = Files.write(tempDir.resolve("a.png"), new byte[] {1});
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkClientException.create("boom"));

		assertThatThrownBy(() -> storage.put("originals/travel/a.png", source, "image/png"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void getBytes_shouldReturnObjectBytes() throws Exception {
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[] {7, 7}));

		assertThat(storage.getBytes("derived/travel/a_home-thumb.webp")).containsExactly(7, 7);
	}

	@Test
	void getBytes_shouldWrapMissingObjectAsIoException() {
		when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
			.thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertThatThrownBy(() -> storage.getBytes("derived/travel/absent.webp"))
			.isInstanceOf(IOException.class);
	}

	@Test
	void exists_shouldBeFalseWhenHeadObjectReportsNoSuchKey() {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
			.thenThrow(NoSuchKeyException.builder().message("missing").build());

		assertThat(storage.exists("originals/travel/absent.png")).isFalse();
	}

	@Test
	void exists_shouldBeTrueWhenHeadObjectSucceeds() {
		when(s3Client.headObject(any(HeadObjectRequest.class)))
			.thenReturn(HeadObjectResponse.builder().build());

		assertThat(storage.exists("originals/travel/a.png")).isTrue();
	}

	@Test
	void presignedGetUrl_shouldSignBucketKeyAndTtl() throws Exception {
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://r2.example.com/a.png?X-Amz-Signature=sig"));
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

		var url = storage.presignedGetUrl("originals/travel/a.png", Duration.ofSeconds(300), null);

		assertThat(url).contains("https://r2.example.com/a.png?X-Amz-Signature=sig");
		ArgumentCaptor<GetObjectPresignRequest> captor =
			ArgumentCaptor.forClass(GetObjectPresignRequest.class);
		verify(s3Presigner).presignGetObject(captor.capture());
		assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofSeconds(300));
		assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("slcn-media");
		assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("originals/travel/a.png");
		assertThat(captor.getValue().getObjectRequest().responseContentDisposition()).isNull();
	}

	@Test
	void presignedGetUrl_shouldCarryContentDispositionSoDownloadsKeepTheirFilename() throws Exception {
		PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
		when(presigned.url()).thenReturn(new URL("https://r2.example.com/a.png?X-Amz-Signature=sig"));
		when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

		storage.presignedGetUrl("originals/travel/a.png", Duration.ofSeconds(300),
			"attachment; filename*=UTF-8''%EC%82%AC%EC%A7%84.png");

		ArgumentCaptor<GetObjectPresignRequest> captor =
			ArgumentCaptor.forClass(GetObjectPresignRequest.class);
		verify(s3Presigner).presignGetObject(captor.capture());
		assertThat(captor.getValue().getObjectRequest().responseContentDisposition())
			.isEqualTo("attachment; filename*=UTF-8''%EC%82%AC%EC%A7%84.png");
	}
}
```

- [ ] **Step 4: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*R2ObjectStorageTest'`
Expected: 컴파일 실패 — `cannot find symbol: class R2ObjectStorage`

- [ ] **Step 5: R2 어댑터를 구현한다**

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorage.java`:

```java
package com.seoulchonnom.aggregate.file.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 호환 오브젝트 스토리지 어댑터. Cloudflare R2를 대상으로 하지만 endpoint만 바꾸면 S3에서도 동작한다.
 * SDK 예외는 전부 IOException으로 감싸 호출자가 저장소 종류를 몰라도 되게 한다.
 */
@Slf4j
@RequiredArgsConstructor
public class R2ObjectStorage implements ObjectStorage {
	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;

	@Override
	public void put(String key, Path source, String contentType) throws IOException {
		try {
			s3Client.putObject(PutObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.contentType(contentType)
				.build(), RequestBody.fromFile(source));
		} catch (SdkException e) {
			throw new IOException("Failed to put object: " + key, e);
		}
	}

	@Override
	public byte[] getBytes(String key) throws IOException {
		try {
			return s3Client.getObjectAsBytes(GetObjectRequest.builder()
				.bucket(bucket)
				.key(key)
				.build()).asByteArray();
		} catch (SdkException e) {
			throw new IOException("Failed to get object: " + key, e);
		}
	}

	/**
	 * HeadObject 실패는 백필의 중복 업로드로만 이어지고 업로드 자체는 멱등하므로, 없는 것으로 취급한다.
	 */
	@Override
	public boolean exists(String key) {
		try {
			s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (SdkException e) {
			log.debug("HeadObject reported the object as unavailable. key={}", key, e);
			return false;
		}
	}

	@Override
	public Optional<String> presignedGetUrl(String key, Duration ttl, String contentDisposition) {
		GetObjectRequest.Builder getObjectRequest = GetObjectRequest.builder()
			.bucket(bucket)
			.key(key);
		if (contentDisposition != null) {
			// 파일명과 attachment 여부를 서명된 쿼리 파라미터로 넘긴다. 바이트가 서버를 통과하지 않아도 저장 이름이 유지된다.
			getObjectRequest.responseContentDisposition(contentDisposition);
		}

		return Optional.of(s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
			.signatureDuration(ttl)
			.getObjectRequest(getObjectRequest.build())
			.build()).url().toString());
	}
}
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*R2ObjectStorageTest'`
Expected: PASS

- [ ] **Step 7: 어댑터 선택 설정을 작성한다**

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/ObjectStorageConfiguration.java`:

```java
package com.seoulchonnom.aggregate.config;

import java.net.URI;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.seoulchonnom.aggregate.file.storage.LocalFileObjectStorage;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.storage.R2ObjectStorage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 오브젝트 스토리지 어댑터 선택. 저장소 교체는 배포 환경 결정이므로 설정값 하나로 가른다.
 * r2가 아니면 로컬 디스크를 쓰므로 개발 환경과 테스트는 자격 증명 없이 그대로 돈다.
 */
@Configuration
public class ObjectStorageConfiguration {
	@Bean
	public ObjectStorage objectStorage(
		@Value("${slcn.storage.provider:local}") String provider,
		@Value("${slcn.upload.path}") String uploadPath,
		@Value("${slcn.storage.r2.endpoint:}") String endpoint,
		@Value("${slcn.storage.r2.region:auto}") String region,
		@Value("${slcn.storage.r2.bucket:}") String bucket,
		@Value("${slcn.storage.r2.access-key:}") String accessKey,
		@Value("${slcn.storage.r2.secret-key:}") String secretKey) {
		if (!"r2".equalsIgnoreCase(provider)) {
			return new LocalFileObjectStorage(Paths.get(uploadPath).toAbsolutePath().normalize());
		}

		StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
			AwsBasicCredentials.create(accessKey, secretKey));
		URI endpointUri = URI.create(endpoint);
		// R2는 virtual-host 스타일 버킷 주소를 쓰지 않으므로 path-style을 강제한다.
		S3Configuration serviceConfiguration = S3Configuration.builder()
			.pathStyleAccessEnabled(true)
			.build();

		S3Client s3Client = S3Client.builder()
			.endpointOverride(endpointUri)
			.region(Region.of(region))
			.credentialsProvider(credentials)
			.serviceConfiguration(serviceConfiguration)
			.build();
		S3Presigner s3Presigner = S3Presigner.builder()
			.endpointOverride(endpointUri)
			.region(Region.of(region))
			.credentialsProvider(credentials)
			.serviceConfiguration(serviceConfiguration)
			.build();

		return new R2ObjectStorage(s3Client, s3Presigner, bucket);
	}
}
```

- [ ] **Step 8: `application.yml`에 스토리지 설정을 추가한다**

`slcn-boot/src/main/resources/application.yml`의 `slcn:` 블록을 아래로 교체한다.

```yaml
slcn:
  domain: "${SLCN_DOMAIN_LIST:http://localhost:9090}"
  upload:
    # local 프로바이더의 기준 디렉터리이자, R2 백필의 원본 위치다.
    path: "${SLCN_UPLOAD_PATH}"
  storage:
    # local | r2. r2가 아니면 로컬 디스크에 저장하고 바이트를 직접 서빙한다.
    provider: "${SLCN_STORAGE_PROVIDER:local}"
    # presigned URL 만료. 만료는 요청 시작 시점에만 검사되므로 짧게 잡아도 대용량 전송이 끊기지 않는다.
    presigned-ttl-seconds: "${SLCN_STORAGE_PRESIGNED_TTL:300}"
    r2:
      endpoint: "${SLCN_R2_ENDPOINT:}"
      region: "${SLCN_R2_REGION:auto}"
      bucket: "${SLCN_R2_BUCKET:}"
      access-key: "${SLCN_R2_ACCESS_KEY:}"
      secret-key: "${SLCN_R2_SECRET_KEY:}"
```

- [ ] **Step 9: 애플리케이션 컨텍스트가 뜨는지 확인한다**

Run: `./gradlew :slcn-boot:test`
Expected: PASS. `ObjectStorage` 빈이 `local` 기본값으로 생성되어 기존 컨텍스트 테스트가 그대로 통과한다.

- [ ] **Step 10: 커밋한다**

```bash
git add slcn-aggregate/build.gradle \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorage.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/ObjectStorageConfiguration.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/storage/R2ObjectStorageTest.java \
        slcn-boot/src/main/resources/application.yml
git commit -m "feat: R2 ObjectStorage 어댑터와 스토리지 설정 추가"
```

---

## Task 4: 업로드 경로를 오브젝트 스토리지로 전환

**Files:**
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/util/FileUtils.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileLogic.java:41-65`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/util/FileUtilsTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileLogicTest.java`

**Interfaces:**
- Consumes: `ObjectStorage.put` (Task 1), `ObjectKeys.original/derived` (Task 1).
- Produces:
  - `FileUtils.StagedUpload` record: `(FileAsset fileAsset, Path stagingDirectory, Path originalPath)`
  - `FileUtils.stageUpload(MultipartFile multipartFile, String type) throws IOException` → `StagedUpload`
  - `FileUtils.writeVariants(FileAsset fileAsset, Path originalPath)` → `ImageProfile` (기존 1-인자 버전을 대체한다)
  - `FileUtils.deleteStaging(Path stagingDirectory)` → void
  - `FileLogic(FileUtils fileUtils, FileAssetStore fileAssetStore, ObjectStorage objectStorage)` — 생성자 인자가 셋으로 늘어난다.
- 삭제: `FileUtils.saveImages`, `FileUtils.saveImageAsset`. `saveImages`는 프로덕션 코드에서 호출되지 않는 죽은 메서드다(테스트에서만 쓰였다).
- **이 태스크에서는 `directory` 필드, `resolvePath`, `existsFileRef`를 남겨 둔다.** 조회 경로(`FileLogic.readImageFile`)가 아직 이들을 쓰고 있어 먼저 지우면 컴파일이 깨진다. Task 5에서 조회 경로를 옮기면서 함께 지운다. `isValidFileRef`와 `isValidFilePath`는 I/O가 없는 형식 검증이므로 계속 남는다.

- [ ] **Step 1: `FileUtilsTest`를 새 계약으로 고쳐 실패시킨다**

`setUp`은 그대로 둔다. `existsFileRef` 테스트가 아직 `directory` 주입을 필요로 한다.

`saveImages_shouldAcceptUppercaseImageExtensionWhenContentIsValid`, `saveImageAsset_shouldCreateTravelDirectoryAndReturnFileAsset` 두 테스트를 아래 하나로 교체한다.

```java
	@Test
	void stageUpload_shouldWriteTheOriginalToATemporaryDirectory() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "travel.PNG", "image/png", PNG_BYTES);

		var staged = fileUtils.stageUpload(file, "travel");

		assertThat(staged.fileAsset().getType()).isEqualTo(FileType.TRAVEL);
		assertThat(staged.fileAsset().getOriginalFilename()).isEqualTo("travel.PNG");
		assertThat(staged.fileAsset().getStoredFilename()).endsWith(".png");
		assertThat(staged.fileAsset().getPath())
			.isEqualTo("travel/" + staged.fileAsset().getStoredFilename());
		assertThat(staged.originalPath()).exists();
		assertThat(staged.originalPath().getFileName().toString())
			.isEqualTo(staged.fileAsset().getStoredFilename());
		assertThat(staged.originalPath().getParent()).isEqualTo(staged.stagingDirectory());

		fileUtils.deleteStaging(staged.stagingDirectory());
	}

	@Test
	void deleteStaging_shouldRemoveTheDirectoryAndItsContents() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "travel.png", "image/png", PNG_BYTES);
		var staged = fileUtils.stageUpload(file, "travel");

		fileUtils.deleteStaging(staged.stagingDirectory());

		assertThat(staged.stagingDirectory()).doesNotExist();
	}
```

`saveImages_shouldRejectFileWhenContentTypeIsNotImage`와 `saveImages_shouldRejectFileWhenBytesAreNotImage`의 호출부를 `fileUtils.saveImages(file, "logo")`에서 `fileUtils.stageUpload(file, "logo")`로 바꾸고, 메서드 이름도 `stageUpload_shouldRejectFileWhenContentTypeIsNotImage`, `stageUpload_shouldRejectFileWhenBytesAreNotImage`로 바꾼다.

모든 `writeVariants` 테스트에서 `var fileAsset = fileUtils.saveImageAsset(file, "travel");`를 아래로 바꾸고, `fileUtils.writeVariants(fileAsset)` 호출을 `fileUtils.writeVariants(fileAsset, staged.originalPath())`로 바꾼다.

```java
		var staged = fileUtils.stageUpload(file, "travel");
		var fileAsset = staged.fileAsset();
```

`variantPath` 헬퍼는 임시 디렉터리를 기준으로 삼도록 인자를 하나 더 받는다.

```java
	private Path variantPath(FileUtils.ImageProfile profile, String variantName, Path stagingDirectory) {
		String filename = profile.variants().stream()
			.filter(variant -> variantName.equals(variant.getVariant()))
			.map(FileVariant::getFilename)
			.findFirst()
			.orElseThrow();
		return stagingDirectory.resolve(filename);
	}
```

`variantPath` 호출부를 모두 3-인자로 바꾼다. `writeVariants_shouldGenerateSmallerVariantsAndReportOriginalDimensions`의 두 줄이 대상이다.

```java
		Path feature = variantPath(profile, "home-feature", staged.stagingDirectory());
		Path thumb = variantPath(profile, "home-thumb", staged.stagingDirectory());
```

`writeVariants_shouldFallBackToJpegWhenWebpEncoderIsUnavailable`의 마지막 두 줄(빈 WebP 파일이 남지 않는지 확인)도 임시 디렉터리를 기준으로 바꾼다.

```java
			assertThat(Files.exists(variantPath(profile, "home-thumb", staged.stagingDirectory()))).isTrue();
			String base = fileAsset.getStoredFilename().replace(".png", "");
			assertThat(Files.exists(staged.stagingDirectory().resolve(base + "_home-thumb.webp"))).isFalse();
```

`writeVariants_shouldReturnEmptyProfileInsteadOfFailingWhenImageIsUnreadable`을 아래로 교체한다.

```java
	@Test
	void writeVariants_shouldReturnEmptyProfileInsteadOfFailingWhenImageIsUnreadable() throws Exception {
		Path broken = Files.writeString(tempDir.resolve("broken.svg"), "<svg></svg>");
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "broken.svg", "broken.svg", "image/svg+xml", 11L);

		var profile = fileUtils.writeVariants(fileAsset, broken);

		assertThat(profile.width()).isZero();
		assertThat(profile.variants()).isEmpty();
	}
```

`existsFileRef_shouldReflectFilePresence` 테스트는 그대로 둔다. Task 5에서 메서드와 함께 지운다.

사용하지 않게 된 import `com.seoulchonnom.spec.file.entity.vo.FileReference`를 지운다. `ReflectionTestUtils`는 계속 쓰이므로 남긴다.

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*FileUtilsTest'`
Expected: 컴파일 실패 — `cannot find symbol: method stageUpload(MultipartFile,String)`

- [ ] **Step 3: `FileUtils`를 스테이징 방식으로 바꾼다**

`FileUtils.java`에서 `saveImages`/`saveImageAsset`을 지우고 아래를 넣는다. `FileReference` import도 함께 지운다. `@Value` 필드와 `resolvePath`, `existsFileRef`는 조회 경로가 아직 쓰므로 남긴다.

```java
	/**
	 * 원본을 임시 디렉터리에 받아둔다. 최종 저장 위치가 서버 디스크가 아니므로 업로드 경로에 바로 쓰지 않는다.
	 * 임시 디렉터리는 오브젝트 업로드가 끝난 뒤 호출자가 deleteStaging으로 지운다.
	 */
	public StagedUpload stageUpload(MultipartFile multipartFile, String type) throws IOException {
		if (type == null || type.isEmpty() || !type.matches(AVAILABLE_PATH)) {
			throw new FilePathInvalidException();
		}

		if (multipartFile.getSize() > MAX_FILE_SIZE) {
			throw new FileSizeException();
		}

		validateImageFile(multipartFile);

		String filename = createSaveFileName(multipartFile.getOriginalFilename());
		Path stagingDirectory = Files.createTempDirectory("slcn-upload-");
		Path originalPath = stagingDirectory.resolve(filename);
		multipartFile.transferTo(originalPath);

		FileAsset fileAsset = new FileAsset(
			FileType.from(type),
			multipartFile.getOriginalFilename(),
			filename,
			multipartFile.getContentType(),
			multipartFile.getSize()
		);
		return new StagedUpload(fileAsset, stagingDirectory, originalPath);
	}

	/**
	 * 임시 디렉터리를 통째로 지운다. 정리 실패가 업로드를 실패시키면 안 되므로 로그만 남긴다.
	 */
	public void deleteStaging(Path stagingDirectory) {
		if (stagingDirectory == null) {
			return;
		}

		try (Stream<Path> paths = Files.walk(stagingDirectory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
		} catch (IOException e) {
			log.warn("Failed to clean staging directory. path={}", stagingDirectory, e);
		}
	}
```

`writeVariants`의 시그니처와 첫 줄만 바꾼다. 나머지 본문은 그대로다.

```java
	/**
	 * 임시 디렉터리의 원본에서 홈 화면용 파생본을 만들고 원본 픽셀 크기를 함께 읽는다.
	 * 파생본은 원본과 같은 임시 디렉터리에 쓰이고, 업로드는 호출자가 한다.
	 * 파생본 생성은 부가 작업이므로 실패해도 예외를 던지지 않는다. 업로드 자체는 성공해야 한다.
	 */
	public ImageProfile writeVariants(FileAsset fileAsset, Path originalPath) {
		BufferedImage source = readImage(originalPath);
		if (source == null) {
			log.warn("Variant generation skipped: unreadable image. path={}", fileAsset.getPath());
			return ImageProfile.empty();
		}
```

`ImageProfile` record 위에 `StagedUpload` record를 추가한다.

```java
	/**
	 * 임시 디렉터리에 받아둔 업로드 한 건.
	 */
	public record StagedUpload(FileAsset fileAsset, Path stagingDirectory, Path originalPath) {
	}
```

import에 `java.util.Comparator`와 `java.util.stream.Stream`을 추가하고, 더 이상 쓰지 않는 `java.io.File`, `com.seoulchonnom.spec.file.entity.vo.FileReference`를 지운다. `java.nio.file.Paths`는 `resolvePath`가 아직 쓰므로 남긴다.

- [ ] **Step 4: `FileUtilsTest`가 통과하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*FileUtilsTest'`
Expected: PASS

- [ ] **Step 5: `FileLogicTest`의 업로드 테스트를 새 계약으로 고친다**

필드 선언과 업로드 테스트를 아래로 교체한다.

```java
	private final FileUtils fileUtils = mock(FileUtils.class);
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final ObjectStorage objectStorage = mock(ObjectStorage.class);
	private final FileLogic fileLogic = new FileLogic(fileUtils, fileAssetStore, objectStorage);
```

```java
	@Test
	void uploadFiles_shouldSaveAssetsAndReturnStoredMetadata() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original)).thenReturn(new FileUtils.ImageProfile(1600, 900,
			List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp"))));
		when(fileAssetStore.save(fileAsset)).thenReturn(fileAsset);

		List<FileAsset> result = fileLogic.uploadFiles(List.of(file), "travel");

		assertThat(result).containsExactly(fileAsset);
		assertThat(fileAsset.getWidth()).isEqualTo(1600);
		assertThat(fileAsset.getHeight()).isEqualTo(900);
		assertThat(fileAsset.variantNames()).containsExactly("home-thumb");
		verify(fileAssetStore).save(fileAsset);
	}

	@Test
	void uploadFiles_shouldPutOriginalAndVariantsUnderTheirOwnPrefixes() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		Path variant = Files.write(staging.resolve("stored_home-thumb.webp"), new byte[] {2});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original)).thenReturn(new FileUtils.ImageProfile(1600, 900,
			List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp"))));
		when(fileAssetStore.save(fileAsset)).thenReturn(fileAsset);

		fileLogic.uploadFiles(List.of(file), "travel");

		verify(objectStorage).put("originals/travel/stored.png", original, "image/png");
		verify(objectStorage).put("derived/travel/stored_home-thumb.webp", variant, "image/webp");
	}

	@Test
	void uploadFiles_shouldNotSaveMetadataWhenTheObjectUploadFails() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original))
			.thenReturn(new FileUtils.ImageProfile(1600, 900, List.of()));
		doThrow(new IOException("boom")).when(objectStorage)
			.put("originals/travel/stored.png", original, "image/png");

		assertThatThrownBy(() -> fileLogic.uploadFiles(List.of(file), "travel"))
			.isInstanceOf(FileUploadException.class);
		verify(fileAssetStore, never()).save(any());
	}

	@Test
	void uploadFiles_shouldAlwaysCleanTheStagingDirectory() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "travel.png", "image/png", new byte[] {1});
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 1L);
		Path staging = tempDir.resolve("staging");
		Files.createDirectories(staging);
		Path original = Files.write(staging.resolve("stored.png"), new byte[] {1});
		when(fileUtils.stageUpload(file, "travel"))
			.thenReturn(new FileUtils.StagedUpload(fileAsset, staging, original));
		when(fileUtils.writeVariants(fileAsset, original))
			.thenReturn(new FileUtils.ImageProfile(1600, 900, List.of()));
		doThrow(new IOException("boom")).when(objectStorage)
			.put("originals/travel/stored.png", original, "image/png");

		assertThatThrownBy(() -> fileLogic.uploadFiles(List.of(file), "travel"))
			.isInstanceOf(FileUploadException.class);
		verify(fileUtils).deleteStaging(staging);
	}
```

import에 `java.io.IOException`, `java.nio.file.Path`, `com.seoulchonnom.aggregate.file.exception.FileUploadException`, `com.seoulchonnom.aggregate.file.storage.ObjectStorage`를 추가한다.

조회 테스트는 이 태스크에서 건드리지 않는다. 조회 경로가 그대로 남아 있으므로 기존 조회 테스트는 계속 통과해야 한다.

- [ ] **Step 6: `FileLogic`의 업로드 경로를 바꾼다**

`FileLogic`에 `ObjectStorage`를 주입하고 `uploadFileAsset`을 아래로 교체한다.

```java
	private final FileUtils fileUtils;
	private final FileAssetStore fileAssetStore;
	private final ObjectStorage objectStorage;
```

```java
	/**
	 * 원본을 임시 디렉터리에 받아 파생본을 만든 뒤, 오브젝트 업로드가 모두 끝난 다음에 메타데이터를 저장한다.
	 * 순서를 뒤집으면 객체가 없는 메타데이터가 생겨 조회가 깨진다. 반대 방향의 고아 객체는 조회에 영향을 주지 않는다.
	 */
	public FileAsset uploadFileAsset(MultipartFile file, String type) {
		FileUtils.StagedUpload staged = null;
		try {
			staged = fileUtils.stageUpload(file, type);
			FileAsset fileAsset = staged.fileAsset();
			String assetType = fileAsset.getType().getValue();
			FileUtils.ImageProfile profile = fileUtils.writeVariants(fileAsset, staged.originalPath());

			objectStorage.put(ObjectKeys.original(assetType, fileAsset.getStoredFilename()),
				staged.originalPath(), fileAsset.getMimeType());
			for (FileVariant variant : profile.variants()) {
				objectStorage.put(ObjectKeys.derived(assetType, variant.getFilename()),
					staged.stagingDirectory().resolve(variant.getFilename()), variant.getMimeType());
			}

			fileAsset.setWidth(profile.width());
			fileAsset.setHeight(profile.height());
			fileAsset.setVariants(new ArrayList<>(profile.variants()));
			return fileAssetStore.save(fileAsset);
		} catch (IOException e) {
			throw new FileUploadException();
		} finally {
			if (staged != null) {
				fileUtils.deleteStaging(staged.stagingDirectory());
			}
		}
	}
```

import에 `com.seoulchonnom.aggregate.file.storage.ObjectKeys`, `com.seoulchonnom.aggregate.file.storage.ObjectStorage`를 추가한다. 조회 경로(`getImageFile`, `getImageFileById`, `readImageFile`)와 `directory` 필드는 이 태스크에서 손대지 않는다.

- [ ] **Step 7: 업로드 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*FileUtilsTest' --tests '*FileLogicTest'`
Expected: PASS. 업로드 테스트는 새 계약으로, 조회 테스트는 손대지 않은 기존 경로로 모두 통과한다.

- [ ] **Step 8: 커밋한다**

```bash
git add slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/util/FileUtils.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileLogic.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/util/FileUtilsTest.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileLogicTest.java
git commit -m "feat: 업로드를 임시 디렉터리 스테이징과 오브젝트 스토리지 업로드로 전환"
```

---

## Task 5: 조회 경로를 오브젝트 스토리지와 presigned 리다이렉트로 전환

**Files:**
- Modify: `slcn-spec/src/main/java/com/seoulchonnom/spec/file/facade/sdo/ImageFileRdo.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileLogic.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/util/FileUtils.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileLogicTest.java`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/util/FileUtilsTest.java`

**Interfaces:**
- Consumes: `ObjectStorage.getBytes/presignedGetUrl`, `ObjectKeys.original/derived/of/isDerived`, `MimeTypes.ofFilename` (Task 1).
- Produces:
  - `ImageFileRdo.getRedirectUrl()` — 값이 있으면 `image`는 null이고 호출자는 302를 반환해야 한다.
  - `FileLogic.downloadImageFileById(String fileId, ImageVariant variant)` → `ImageFileRdo` (첨부 다운로드용 진입점)
  - `FileLogic.getImageFileById(String, ImageVariant)`, `FileLogic.getImageFileById(String)`, `FileLogic.getImageFile(String, String)` — 시그니처는 그대로이고 반환값에 `redirectUrl`이 실릴 수 있다.
  - 설정 키 `slcn.storage.presigned-ttl-seconds` 소비 (Task 3에서 추가)
- 삭제: `FileUtils.existsFileRef`, `FileUtils.resolvePath`, `FileUtils.directory` 필드. 파일 존재 확인은 "읽어 보고 실패하면 원본으로 폴백"으로 대체된다. 요청마다 HeadObject 왕복을 더하지 않기 위해서다.

- [ ] **Step 1: `ImageFileRdo`에 리다이렉트 필드를 추가한다**

`slcn-spec/src/main/java/com/seoulchonnom/spec/file/facade/sdo/ImageFileRdo.java`의 `downloadFilename` 아래에 추가한다.

```java
	/**
	 * 원본처럼 서버가 바이트를 통과시키지 않는 자산의 서명된 조회 URL.
	 * 값이 있으면 image는 비어 있고, 호출자는 302로 이 URL을 넘겨야 한다.
	 */
	private String redirectUrl;
```

- [ ] **Step 2: `FileLogicTest`의 조회 테스트를 새 계약으로 교체해 실패시킨다**

기존 조회 테스트 전부(`getImageFileById_*`)를 아래로 교체한다. `@TempDir`와 `java.nio.file.Files` import는 업로드 테스트가 여전히 쓰므로 남긴다.

```java
	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(fileLogic, "presignedTtlSeconds", 300L);
		// 기본값은 "서명할 수 없는 저장소"다. 리다이렉트를 검증하는 테스트만 개별로 덮어쓴다.
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), any()))
			.thenReturn(Optional.empty());
	}

	@Test
	void getImageFileById_shouldRedirectToAPresignedOriginalUrl() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(eq("originals/travel/stored.png"), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getRedirectUrl()).isEqualTo("https://r2.example.com/signed");
		assertThat(result.getImage()).isNull();
		assertThat(result.getVariant()).isEqualTo("original");
		verify(fileUtils).isValidFileRef("travel", "stored.png");
	}

	@Test
	void getImageFileById_shouldSignForTheConfiguredTtl() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		fileLogic.getImageFileById("file-1");

		verify(objectStorage).presignedGetUrl("originals/travel/stored.png", Duration.ofSeconds(300), null);
	}

	@Test
	void getImageFileById_shouldServeOriginalBytesWhenStorageCannotSign() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("originals/travel/stored.png")).thenReturn(new byte[] {1, 2, 3});

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getRedirectUrl()).isNull();
		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getMimeType()).isEqualTo("image/png");
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldRaiseFilePathInvalidWhenTheOriginalIsUnreadable() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("originals/travel/stored.png")).thenThrow(new IOException("missing"));

		assertThatThrownBy(() -> fileLogic.getImageFileById("file-1"))
			.isInstanceOf(FilePathInvalidException.class);
	}

	@Test
	void getImageFileById_shouldServeVariantBytesFromTheDerivedPrefix() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp")).thenReturn(new byte[] {9, 9});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(9, 9);
		assertThat(result.getMimeType()).isEqualTo("image/webp");
		assertThat(result.getVariant()).isEqualTo("home-thumb");
		assertThat(result.getRedirectUrl()).isNull();
	}

	@Test
	void getImageFileById_shouldNeverRedirectForVariantsSoTheirCacheKeysStayStable() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp")).thenReturn(new byte[] {9});
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), any()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getRedirectUrl()).isNull();
		assertThat(result.getImage()).containsExactly(9);
	}

	@Test
	void getImageFileById_shouldServeJpegVariantWhenThatIsWhatWasRecorded() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.jpg", "image/jpeg")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.jpg")).thenReturn(new byte[] {8});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getMimeType()).isEqualTo("image/jpeg");
		assertThat(result.getVariant()).isEqualTo("home-thumb");
	}

	@Test
	void getImageFileById_shouldFallBackToOriginalWhenVariantWasNeverGenerated() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("originals/travel/stored.png")).thenReturn(new byte[] {1, 2, 3});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldFallBackToOriginalWhenVariantIsRecordedButUnreadable() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "travel.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp"))
			.thenThrow(new IOException("missing"));
		when(objectStorage.getBytes("originals/travel/stored.png")).thenReturn(new byte[] {1, 2, 3});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getImage()).containsExactly(1, 2, 3);
		assertThat(result.getVariant()).isEqualTo("original");
	}

	@Test
	void getImageFileById_shouldCarryTheUploadedFilenameForDownloads() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "여행 사진.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getDownloadFilename()).isEqualTo("여행 사진.png");
	}

	@Test
	void getImageFileById_shouldStripPathAndControlCharactersFromTheUploadedFilename() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "../etc/pass\"wd.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), isNull()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFileById("file-1");

		assertThat(result.getDownloadFilename()).isEqualTo("passwd.png");
	}

	@Test
	void getImageFileById_shouldNameVariantDownloadsAfterTheOriginalFile() throws Exception {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "cover.png", "stored.png", "image/png", 3L);
		fileAsset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.getBytes("derived/travel/stored_home-thumb.webp")).thenReturn(new byte[] {9});

		var result = fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB);

		assertThat(result.getDownloadFilename()).isEqualTo("cover_home-thumb.webp");
	}

	@Test
	void downloadImageFileById_shouldPutAttachmentDispositionIntoTheSignedUrl() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "여행 사진.png", "stored.png", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), anyString()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.downloadImageFileById("file-1", null);

		assertThat(result.getRedirectUrl()).isEqualTo("https://r2.example.com/signed");
		ArgumentCaptor<String> disposition = ArgumentCaptor.forClass(String.class);
		verify(objectStorage).presignedGetUrl(eq("originals/travel/stored.png"), any(Duration.class),
			disposition.capture());
		assertThat(disposition.getValue()).startsWith("attachment;");
		// 한글 파일명은 RFC 5987로 인코딩되어야 한다.
		assertThat(disposition.getValue()).contains("filename*=UTF-8''");
	}

	@Test
	void downloadImageFileById_shouldFallBackToAGenericNameWhenTheAssetHasNoUsableFilename() {
		FileAsset fileAsset = new FileAsset(FileType.TRAVEL, "", "", "image/png", 3L);
		when(fileAssetStore.findById("file-1")).thenReturn(fileAsset);
		when(objectStorage.presignedGetUrl(anyString(), any(Duration.class), anyString()))
			.thenReturn(Optional.of("https://r2.example.com/signed"));

		fileLogic.downloadImageFileById("file-1", null);

		ArgumentCaptor<String> disposition = ArgumentCaptor.forClass(String.class);
		verify(objectStorage).presignedGetUrl(anyString(), any(Duration.class), disposition.capture());
		assertThat(disposition.getValue()).contains("download");
	}

	@Test
	void getImageFile_shouldRedirectForOriginalFilenames() {
		when(objectStorage.presignedGetUrl(eq("originals/logo/" + UUID_NAME + ".png"), any(Duration.class),
			isNull())).thenReturn(Optional.of("https://r2.example.com/signed"));

		var result = fileLogic.getImageFile("logo", UUID_NAME + ".png");

		assertThat(result.getRedirectUrl()).isEqualTo("https://r2.example.com/signed");
		verify(fileUtils).isValidFileRef("logo", UUID_NAME + ".png");
	}

	@Test
	void getImageFile_shouldServeVariantFilenamesAsBytesFromTheDerivedPrefix() throws Exception {
		when(objectStorage.getBytes("derived/logo/" + UUID_NAME + "_home-thumb.webp"))
			.thenReturn(new byte[] {5});

		var result = fileLogic.getImageFile("logo", UUID_NAME + "_home-thumb.webp");

		assertThat(result.getImage()).containsExactly(5);
		assertThat(result.getMimeType()).isEqualTo("image/webp");
		assertThat(result.getRedirectUrl()).isNull();
	}
```

클래스 상단에 상수를 추가한다.

```java
	private static final String UUID_NAME = "72d768d4-2b05-48f9-bee8-fee3b52e909f";
```

import에 `java.io.IOException`, `java.time.Duration`, `java.util.Optional`, `org.junit.jupiter.api.BeforeEach`, `org.mockito.ArgumentCaptor`, `org.springframework.test.util.ReflectionTestUtils`, `com.seoulchonnom.aggregate.file.exception.FilePathInvalidException`를 추가한다. `any`, `eq`, `anyString`, `isNull`은 `org.mockito.Mockito`가 `ArgumentMatchers`를 상속하므로 이미 있는 `static org.mockito.Mockito.*`로 해결된다. `static org.mockito.ArgumentMatchers.*`를 따로 넣으면 참조가 모호해지므로 넣지 않는다.

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*FileLogicTest'`
Expected: 컴파일 실패 — `cannot find symbol: method downloadImageFileById(String,ImageVariant)`

- [ ] **Step 4: `FileLogic`의 조회 경로를 교체한다**

`getImageFile`부터 파일 끝까지(`getImageFile`, `getImageFileById` 2종, `readImageFile`)를 아래로 교체한다.

```java
	/**
	 * 경로 기반 조회. 파일명만 받으므로 접미사로 원본/파생본을 가른다.
	 */
	public ImageFileRdo getImageFile(String type, String filename) {
		fileUtils.isValidFileRef(type, filename);

		String key = ObjectKeys.of(type, filename);
		if (!ObjectKeys.isDerived(key)) {
			Optional<ImageFileRdo> redirect = presignedRdo(key, ORIGINAL_VARIANT_TAG, filename, null);
			if (redirect.isPresent()) {
				return redirect.get();
			}
		}

		return ImageFileRdo.builder()
			.image(readBytes(key))
			.mimeType(MimeTypes.ofFilename(filename))
			.variant(ORIGINAL_VARIANT_TAG)
			.downloadFilename(filename)
			.build();
	}

	public ImageFileRdo getImageFileById(String fileId) {
		return getImageFileById(fileId, null);
	}

	/**
	 * 요청한 파생본이 없거나 읽히지 않으면 원본을 응답한다.
	 * 홈 화면이 파생본 하나 때문에 표지를 통째로 잃는 편보다 원본을 받는 편이 낫다.
	 */
	public ImageFileRdo getImageFileById(String fileId, ImageVariant variant) {
		return readImageFileById(fileId, variant, false);
	}

	/**
	 * 조회와 같은 자산을 첨부 파일로 내려준다.
	 * 원본은 리다이렉트로 나가므로 Content-Disposition을 서명 URL에 실어야 하고, 그래서 조회와 진입점을 나눈다.
	 */
	public ImageFileRdo downloadImageFileById(String fileId, ImageVariant variant) {
		return readImageFileById(fileId, variant, true);
	}

	private ImageFileRdo readImageFileById(String fileId, ImageVariant variant, boolean attachment) {
		FileAsset fileAsset = fileAssetStore.findById(fileId);
		String type = fileAsset.getType().getValue();

		Optional<FileVariant> fileVariant = fileAsset.findVariant(variant);
		if (fileVariant.isPresent()) {
			FileVariant resolved = fileVariant.get();
			Optional<ImageFileRdo> derived = readDerived(type, fileAsset, resolved);
			if (derived.isPresent()) {
				return derived.get();
			}
			log.warn("Variant recorded but unreadable, serving original. fileId={}, variant={}",
				fileId, resolved.getVariant());
		}

		return readOriginal(type, fileAsset, attachment);
	}

	/**
	 * 파생본은 작고 재사용률이 높다. 서명 URL은 서명할 때마다 값이 바뀌어 캐시가 매번 빗나가므로 계속 바이트로 서빙한다.
	 * 존재 확인을 따로 하지 않는 것은 요청마다 HeadObject 왕복이 하나 더 붙기 때문이다. 읽어 보고 실패하면 원본으로 폴백한다.
	 */
	private Optional<ImageFileRdo> readDerived(String type, FileAsset fileAsset, FileVariant resolved) {
		fileUtils.isValidFileRef(type, resolved.getFilename());

		try {
			return Optional.of(ImageFileRdo.builder()
				.image(objectStorage.getBytes(ObjectKeys.derived(type, resolved.getFilename())))
				.mimeType(resolved.getMimeType())
				.variant(resolved.getVariant())
				.downloadFilename(fileAsset.downloadFilename(resolved))
				.build());
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/**
	 * 원본은 100 MB에 이를 수 있어 서버 힙에 올리지 않는다. 서명할 수 있으면 클라이언트를 저장소로 직접 보낸다.
	 */
	private ImageFileRdo readOriginal(String type, FileAsset fileAsset, boolean attachment) {
		String filename = fileAsset.getStoredFilename();
		fileUtils.isValidFileRef(type, filename);

		String key = ObjectKeys.original(type, filename);
		String downloadFilename = fileAsset.downloadFilename(null);
		Optional<ImageFileRdo> redirect = presignedRdo(key, ORIGINAL_VARIANT_TAG, downloadFilename,
			attachment ? attachmentDisposition(downloadFilename) : null);
		if (redirect.isPresent()) {
			return redirect.get();
		}

		return ImageFileRdo.builder()
			.image(readBytes(key))
			.mimeType(fileAsset.getMimeType())
			.variant(ORIGINAL_VARIANT_TAG)
			.downloadFilename(downloadFilename)
			.build();
	}

	private Optional<ImageFileRdo> presignedRdo(String key, String variantTag, String downloadFilename,
		String contentDisposition) {
		return objectStorage.presignedGetUrl(key, Duration.ofSeconds(presignedTtlSeconds), contentDisposition)
			.map(url -> ImageFileRdo.builder()
				.redirectUrl(url)
				.variant(variantTag)
				.downloadFilename(downloadFilename)
				.build());
	}

	private byte[] readBytes(String key) {
		try {
			return objectStorage.getBytes(key);
		} catch (IOException e) {
			throw new FilePathInvalidException();
		}
	}

	/**
	 * 서명 URL에 실어 보낼 Content-Disposition. 한글 파일명이 들어올 수 있으므로 RFC 5987로 인코딩한다.
	 */
	private String attachmentDisposition(String filename) {
		String safeFilename = StringUtils.hasText(filename) ? filename : "download";
		return ContentDisposition.attachment()
			.filename(safeFilename, StandardCharsets.UTF_8)
			.build()
			.toString();
	}
```

`directory` 필드를 지우고 TTL 설정을 넣는다.

```java
	@Value("${slcn.storage.presigned-ttl-seconds:300}")
	private long presignedTtlSeconds;
```

import을 정리한다. 추가: `java.nio.charset.StandardCharsets`, `java.time.Duration`, `org.springframework.http.ContentDisposition`, `org.springframework.util.StringUtils`, `com.seoulchonnom.aggregate.file.storage.MimeTypes`. 삭제: `java.nio.file.Files`, `java.nio.file.Path`, `java.nio.file.Paths`.

- [ ] **Step 5: `FileUtils`에서 디스크 경로 잔재를 지운다**

`FileUtils.java`에서 아래를 삭제한다.

- `@Value("${slcn.upload.path}") private String directory;` 필드
- `existsFileRef(String type, String filename)`
- `resolvePath(String type, String filename)`
- import `org.springframework.beans.factory.annotation.Value`, `java.nio.file.Paths`

`FileUtilsTest`에서 `existsFileRef_shouldReflectFilePresence` 테스트를 삭제하고, `setUp`을 아래로 바꾼다. import `org.springframework.test.util.ReflectionTestUtils`도 지운다.

```java
	@BeforeEach
	void setUp() {
		// FileUtils는 더 이상 업로드 디렉터리를 알지 않는다. 원본은 임시 디렉터리에 받아 오브젝트 스토리지로 올린다.
	}
```

`@BeforeEach` 메서드가 비게 되면 `setUp` 자체와 `org.junit.jupiter.api.BeforeEach` import를 함께 지운다.

- [ ] **Step 6: aggregate 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-spec:test :slcn-aggregate:test`
Expected: PASS

- [ ] **Step 7: 커밋한다**

```bash
git add slcn-spec/src/main/java/com/seoulchonnom/spec/file/facade/sdo/ImageFileRdo.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileLogic.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/util/FileUtils.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileLogicTest.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/util/FileUtilsTest.java
git commit -m "feat: 원본 조회를 presigned URL로, 파생본 조회를 오브젝트 스토리지 읽기로 전환"
```

> 이 시점에서 `slcn-rest`는 컴파일되고 기존 테스트도 통과하지만, `FileResource.toImageResponse`가 `getImage().length`를 읽으므로 **실제 R2 환경에서 원본을 조회하면 NPE가 난다.** Task 6이 이 구멍을 닫는다. 두 태스크는 연이어 실행한다.

---

## Task 6: REST 어댑터의 302 응답

**Files:**
- Modify: `slcn-rest/src/main/java/com/seoulchonnom/rest/file/FileResource.java`
- Test: `slcn-rest/src/test/java/com/seoulchonnom/rest/file/FileResourceTest.java`

**Interfaces:**
- Consumes: `ImageFileRdo.getRedirectUrl()`, `FileLogic.downloadImageFileById(String, ImageVariant)` (Task 5).
- Produces: HTTP 계약 변화 — 원본 조회·다운로드가 `302 Found` + `Location` + `Cache-Control: no-store`를 반환한다. 파생본 응답은 지금과 동일하다.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`FileResourceTest`에 아래를 추가한다.

```java
	@Test
	void getFileById_shouldRedirectWhenTheLogicReturnsASignedUrl() {
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(redirectRdo("original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertEquals(HttpStatus.FOUND, response.getStatusCode());
		assertEquals("https://r2.example.com/signed", response.getHeaders().getLocation().toString());
		assertNull(response.getBody());
	}

	@Test
	void getFileById_shouldNotCacheOrTagARedirectBecauseTheSignedUrlExpires() {
		when(fileLogic.getImageFileById("file-1", null)).thenReturn(redirectRdo("original"));

		var response = fileResource.getFileById("file-1", null, null, null, null);

		assertNull(response.getHeaders().getETag());
		assertEquals("no-store", response.getHeaders().getCacheControl());
	}

	@Test
	void downloadFileById_shouldRedirectAndLeaveTheDispositionToTheSignedUrl() {
		when(fileLogic.downloadImageFileById("file-1", null)).thenReturn(redirectRdo("original"));

		var response = fileResource.downloadFileById("file-1", null, null);

		assertEquals(HttpStatus.FOUND, response.getStatusCode());
		assertEquals("https://r2.example.com/signed", response.getHeaders().getLocation().toString());
		assertNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
	}

	@Test
	void getFile_shouldRedirectWhenTheLogicReturnsASignedUrl() {
		when(fileLogic.getImageFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png"))
			.thenReturn(redirectRdo("original"));

		var response = fileResource.getFile("logo", "72d768d4-2b05-48f9-bee8-fee3b52e909f.png", null);

		assertEquals(HttpStatus.FOUND, response.getStatusCode());
	}

	@Test
	void downloadFileById_shouldStillStreamVariantBytesWithItsOwnDisposition() {
		when(fileLogic.downloadImageFileById("file-1", ImageVariant.HOME_THUMB))
			.thenReturn(imageRdo(new byte[] {9}, "image/webp", "home-thumb", "cover_home-thumb.webp"));

		var response = fileResource.downloadFileById("file-1", "home-thumb", null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
			.contains("cover_home-thumb.webp"));
	}

	private ImageFileRdo redirectRdo(String variant) {
		return ImageFileRdo.builder()
			.redirectUrl("https://r2.example.com/signed")
			.variant(variant)
			.downloadFilename("cover.png")
			.build();
	}
```

기존 다운로드 테스트 5개의 스텁과 검증을 `getImageFileById`에서 `downloadImageFileById`로 바꾼다. 대상은 `downloadFileById_shouldReturnOriginalAsAttachmentWithUploadedFilename`, `downloadFileById_shouldReturnRequestedVariantWhenItIsKnown`, `downloadFileById_shouldFallBackToOriginalForUnknownVariantSoSavesAreNeverDownscaled`, `downloadFileById_shouldNotShareItsEtagWithTheInlineResponse`, `downloadFileById_shouldUseAFallbackNameWhenTheAssetHasNoUsableFilename`이다.

- `when(fileLogic.getImageFileById("file-1", null))` → `when(fileLogic.downloadImageFileById("file-1", null))`
- `when(fileLogic.getImageFileById("file-1", ImageVariant.HOME_THUMB))` → `when(fileLogic.downloadImageFileById("file-1", ImageVariant.HOME_THUMB))`
- `verify(fileLogic, never()).getImageFileById("file-1", ImageVariant.HOME_FEATURE)` → `verify(fileLogic, never()).downloadImageFileById("file-1", ImageVariant.HOME_FEATURE)`
- `downloadFileById_shouldReturnNotModifiedWhenEtagMatches`의 `verify(fileLogic, never()).getImageFileById(anyString(), any())` → `verify(fileLogic, never()).downloadImageFileById(anyString(), any())`

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-rest:test --tests '*FileResourceTest'`
Expected: 컴파일 실패 — `cannot find symbol: method redirectRdo` 및 `downloadImageFileById`를 아직 `FileResource`가 호출하지 않아 `downloadFileById_shouldRedirect...`가 NPE로 실패

- [ ] **Step 3: `FileResource`를 고친다**

`getFileById`, `downloadFileById`, `getFile` 세 메서드의 본문 마지막 부분을 아래로 바꾼다.

```java
		ImageFileRdo imageFileRdo = fileLogic.getImageFileById(fileId, requestedVariant);
		if (StringUtils.hasText(imageFileRdo.getRedirectUrl())) {
			return redirectResponse(imageFileRdo.getRedirectUrl());
		}

		return toImageResponse(imageFileRdo, etagOf(fileId, imageFileRdo.getVariant()));
```

```java
		ImageFileRdo imageFileRdo = fileLogic.downloadImageFileById(fileId, requestedVariant);
		if (StringUtils.hasText(imageFileRdo.getRedirectUrl())) {
			return redirectResponse(imageFileRdo.getRedirectUrl());
		}

		return toImageResponse(imageFileRdo, downloadEtagOf(fileId, imageFileRdo.getVariant()),
			attachmentDisposition(imageFileRdo.getDownloadFilename()));
```

```java
		ImageFileRdo imageFileRdo = fileLogic.getImageFile(type, filename);
		if (StringUtils.hasText(imageFileRdo.getRedirectUrl())) {
			return redirectResponse(imageFileRdo.getRedirectUrl());
		}

		return toImageResponse(imageFileRdo, etag);
```

`imageCacheControl` 아래에 리다이렉트 응답 헬퍼를 추가한다.

```java
	/**
	 * 서명 URL은 만료되므로 캐시하면 안 되고, ETag를 붙이면 만료된 URL이 재사용된다.
	 * 바이트 자체는 불변이라 클라이언트가 이전에 받은 200 응답의 캐시는 그대로 유효하다.
	 */
	private ResponseEntity<byte[]> redirectResponse(String redirectUrl) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(redirectUrl))
			.cacheControl(CacheControl.noStore())
			.build();
	}
```

import에 `java.net.URI`를 추가한다.

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-rest:test --tests '*FileResourceTest'`
Expected: PASS

- [ ] **Step 5: `FileFacade`의 문서 문자열을 실제 동작에 맞춘다**

`slcn-spec/src/main/java/com/seoulchonnom/spec/file/facade/FileFacade.java`의 `getFileById`, `downloadFileById`, `getFile`에 302 응답을 명시한다. 세 메서드의 `@ApiResponse` 목록에 아래를 추가한다.

```java
	@ApiResponse(responseCode = "302", description = "원본은 오브젝트 스토리지의 서명된 URL로 리다이렉트")
```

`getFileById`의 `description` 끝에 아래 문장을 덧붙인다.

```
"원본은 오브젝트 스토리지의 서명된 URL로 302 리다이렉트하고, 축소본은 서버가 바이트를 그대로 응답합니다."
```

- [ ] **Step 6: 모듈 전체 테스트를 돌린다**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, failures 0

- [ ] **Step 7: 커밋한다**

```bash
git add slcn-rest/src/main/java/com/seoulchonnom/rest/file/FileResource.java \
        slcn-rest/src/test/java/com/seoulchonnom/rest/file/FileResourceTest.java \
        slcn-spec/src/main/java/com/seoulchonnom/spec/file/facade/FileFacade.java
git commit -m "feat: 원본 이미지 조회와 다운로드를 302 리다이렉트로 응답"
```

---

## Task 7: 기존 로컬 파일 백필

**Files:**
- Create: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogic.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/store/FileAssetStore.java`
- Modify: `slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/ObjectStorageConfiguration.java`
- Modify: `slcn-aggregate/build.gradle`
- Modify: `slcn-boot/src/main/resources/application.yml`
- Test: `slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogicTest.java`

**Interfaces:**
- Consumes: `ObjectStorage.exists/put`, `ObjectKeys.original/derived` (Task 1), `FileAssetStore` (기존).
- Produces:
  - `FileAssetStore.findPage(int page, int size)` → `List<FileAsset>`
  - `FileAssetMigrationLogic.migrateLegacyFiles()` → `FileAssetMigrationLogic.MigrationReport`
  - `record MigrationReport(int uploaded, int skipped, int missing, int failed)`
  - `enum MigrationOutcome { UPLOADED, SKIPPED, MISSING, FAILED }`
  - 설정 키 `slcn.storage.migration.enabled`(기본 `false`), `slcn.storage.migration.batch-size`(기본 `100`)

**동작 규칙:** 백필은 MongoDB `file_asset`을 페이지 단위로 훑어 `slcn.upload.path` 하위의 기존 레이아웃(`{type}/{filename}`)에서 읽고 새 키(`originals/`, `derived/`)로 올린다. **이미 존재하는 객체는 건너뛰므로 몇 번을 다시 돌려도 안전하다.** 백필 중 새 업로드가 들어와도 새 업로드는 이미 오브젝트 스토리지로 직행하므로, 페이지 이동으로 인해 건너뛰거나 중복 처리되어도 결과가 달라지지 않는다.

- [ ] **Step 1: 부트 의존성을 추가한다**

`slcn-aggregate/build.gradle`의 AWS SDK 선언 아래에 추가한다.

```gradle
    // 백필 러너를 설정 플래그로 켜고 끄기 위한 ApplicationRunner와 @ConditionalOnProperty.
    implementation 'org.springframework.boot:spring-boot'
    implementation 'org.springframework.boot:spring-boot-autoconfigure'
```

- [ ] **Step 2: 실패하는 테스트를 쓴다**

`slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogicTest.java`:

```java
package com.seoulchonnom.aggregate.file.logic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileType;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

class FileAssetMigrationLogicTest {
	private final FileAssetStore fileAssetStore = mock(FileAssetStore.class);
	private final ObjectStorage objectStorage = mock(ObjectStorage.class);
	private final FileAssetMigrationLogic migrationLogic =
		new FileAssetMigrationLogic(fileAssetStore, objectStorage);

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(migrationLogic, "legacyDirectory", tempDir.toString());
		ReflectionTestUtils.setField(migrationLogic, "batchSize", 2);
	}

	@Test
	void migrateLegacyFiles_shouldUploadOriginalAndVariantsUnderTheirNewKeys() throws Exception {
		Path travel = Files.createDirectories(tempDir.resolve("travel"));
		Path original = Files.write(travel.resolve("stored.png"), new byte[] {1});
		Path variant = Files.write(travel.resolve("stored_home-thumb.webp"), new byte[] {2});
		FileAsset asset = new FileAsset(FileType.TRAVEL, "cover.png", "stored.png", "image/png", 1L);
		asset.setVariants(List.of(new FileVariant("home-thumb", "stored_home-thumb.webp", "image/webp")));
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(asset));

		var report = migrationLogic.migrateLegacyFiles();

		verify(objectStorage).put("originals/travel/stored.png", original, "image/png");
		verify(objectStorage).put("derived/travel/stored_home-thumb.webp", variant, "image/webp");
		assertThat(report.uploaded()).isEqualTo(2);
		assertThat(report.failed()).isZero();
	}

	@Test
	void migrateLegacyFiles_shouldSkipObjectsThatAreAlreadyUploadedSoRerunsAreSafe() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/stored.png"), new byte[] {1});
		FileAsset asset = new FileAsset(FileType.TRAVEL, "cover.png", "stored.png", "image/png", 1L);
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(asset));
		when(objectStorage.exists("originals/travel/stored.png")).thenReturn(true);

		var report = migrationLogic.migrateLegacyFiles();

		verify(objectStorage, never()).put(anyString(), any(Path.class), anyString());
		assertThat(report.skipped()).isEqualTo(1);
	}

	@Test
	void migrateLegacyFiles_shouldCountMissingLegacyFilesInsteadOfFailing() {
		FileAsset asset = new FileAsset(FileType.TRAVEL, "cover.png", "gone.png", "image/png", 1L);
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(asset));

		var report = migrationLogic.migrateLegacyFiles();

		assertThat(report.missing()).isEqualTo(1);
		assertThat(report.uploaded()).isZero();
	}

	@Test
	void migrateLegacyFiles_shouldKeepGoingWhenOneUploadFails() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/a.png"), new byte[] {1});
		Files.write(tempDir.resolve("travel/b.png"), new byte[] {2});
		FileAsset first = new FileAsset(FileType.TRAVEL, "a.png", "a.png", "image/png", 1L);
		FileAsset second = new FileAsset(FileType.TRAVEL, "b.png", "b.png", "image/png", 1L);
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(first, second));
		doThrow(new IOException("boom")).when(objectStorage)
			.put(eq("originals/travel/a.png"), any(Path.class), anyString());

		var report = migrationLogic.migrateLegacyFiles();

		assertThat(report.failed()).isEqualTo(1);
		assertThat(report.uploaded()).isEqualTo(1);
	}

	@Test
	void migrateLegacyFiles_shouldWalkEveryPageUntilTheStoreRunsOut() throws Exception {
		Files.createDirectories(tempDir.resolve("travel"));
		Files.write(tempDir.resolve("travel/a.png"), new byte[] {1});
		Files.write(tempDir.resolve("travel/b.png"), new byte[] {2});
		Files.write(tempDir.resolve("travel/c.png"), new byte[] {3});
		when(fileAssetStore.findPage(0, 2)).thenReturn(List.of(
			new FileAsset(FileType.TRAVEL, "a.png", "a.png", "image/png", 1L),
			new FileAsset(FileType.TRAVEL, "b.png", "b.png", "image/png", 1L)));
		when(fileAssetStore.findPage(1, 2)).thenReturn(List.of(
			new FileAsset(FileType.TRAVEL, "c.png", "c.png", "image/png", 1L)));

		var report = migrationLogic.migrateLegacyFiles();

		assertThat(report.uploaded()).isEqualTo(3);
		verify(fileAssetStore).findPage(0, 2);
		verify(fileAssetStore).findPage(1, 2);
	}
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*FileAssetMigrationLogicTest'`
Expected: 컴파일 실패 — `cannot find symbol: class FileAssetMigrationLogic`

- [ ] **Step 4: `FileAssetStore`에 페이지 조회를 추가한다**

```java
	/**
	 * 백필 전용 페이지 조회. 자산 전체를 한 번에 메모리에 올리지 않기 위해 나눠 읽는다.
	 */
	public List<FileAsset> findPage(int page, int size) {
		return fileAssetRepository.findAll(PageRequest.of(page, size))
			.map(fileAssetDocMapper::toDomain)
			.getContent();
	}
```

import에 `java.util.List`, `org.springframework.data.domain.PageRequest`를 추가한다.

- [ ] **Step 5: 백필 로직을 구현한다**

`slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogic.java`:

```java
package com.seoulchonnom.aggregate.file.logic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.seoulchonnom.aggregate.file.storage.ObjectKeys;
import com.seoulchonnom.aggregate.file.storage.ObjectStorage;
import com.seoulchonnom.aggregate.file.store.FileAssetStore;
import com.seoulchonnom.spec.file.entity.FileAsset;
import com.seoulchonnom.spec.file.entity.vo.FileVariant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 디스크에 이미 쌓인 원본과 파생본을 오브젝트 스토리지로 옮긴다.
 * 이미 올라간 객체는 건너뛰므로 몇 번을 다시 돌려도 안전하고, 중간에 끊겨도 이어서 돌리면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAssetMigrationLogic {
	private final FileAssetStore fileAssetStore;
	private final ObjectStorage objectStorage;

	/**
	 * 이전 저장 레이아웃({type}/{filename})의 기준 디렉터리. 백필이 끝나면 더 이상 읽지 않는다.
	 */
	@Value("${slcn.upload.path}")
	private String legacyDirectory;

	@Value("${slcn.storage.migration.batch-size:100}")
	private int batchSize;

	public MigrationReport migrateLegacyFiles() {
		int uploaded = 0;
		int skipped = 0;
		int missing = 0;
		int failed = 0;

		for (int page = 0; ; page++) {
			List<FileAsset> assets = fileAssetStore.findPage(page, batchSize);
			if (assets.isEmpty()) {
				break;
			}

			for (FileAsset asset : assets) {
				for (MigrationOutcome outcome : migrateAsset(asset)) {
					switch (outcome) {
						case UPLOADED -> uploaded++;
						case SKIPPED -> skipped++;
						case MISSING -> missing++;
						case FAILED -> failed++;
					}
				}
			}

			// 마지막 페이지가 정확히 batchSize로 떨어지지 않는 한 여기서 끝난다. 빈 페이지 조회를 한 번 아낀다.
			if (assets.size() < batchSize) {
				break;
			}
		}

		return new MigrationReport(uploaded, skipped, missing, failed);
	}

	private List<MigrationOutcome> migrateAsset(FileAsset asset) {
		String type = asset.getType().getValue();
		List<MigrationOutcome> outcomes = new ArrayList<>();

		outcomes.add(migrateOne(ObjectKeys.original(type, asset.getStoredFilename()), type,
			asset.getStoredFilename(), asset.getMimeType()));
		for (FileVariant variant : asset.getVariants()) {
			outcomes.add(migrateOne(ObjectKeys.derived(type, variant.getFilename()), type,
				variant.getFilename(), variant.getMimeType()));
		}

		return outcomes;
	}

	private MigrationOutcome migrateOne(String key, String type, String filename, String contentType) {
		if (objectStorage.exists(key)) {
			return MigrationOutcome.SKIPPED;
		}

		Path source = Paths.get(legacyDirectory).resolve(type).resolve(filename).normalize();
		if (!Files.exists(source)) {
			log.warn("Legacy file is missing, nothing to migrate. key={}, path={}", key, source);
			return MigrationOutcome.MISSING;
		}

		try {
			objectStorage.put(key, source, contentType);
			return MigrationOutcome.UPLOADED;
		} catch (IOException e) {
			// 한 건이 실패해도 나머지는 계속 옮긴다. 실패분은 다시 돌리면 이어서 처리된다.
			log.error("Failed to migrate legacy file. key={}, path={}", key, source, e);
			return MigrationOutcome.FAILED;
		}
	}

	public enum MigrationOutcome {
		UPLOADED, SKIPPED, MISSING, FAILED
	}

	/**
	 * 옮긴 결과. 배포 로그 한 줄로 확인할 수 있어야 하므로 개수만 센다.
	 */
	public record MigrationReport(int uploaded, int skipped, int missing, int failed) {
	}
}
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

Run: `./gradlew :slcn-aggregate:test --tests '*FileAssetMigrationLogicTest'`
Expected: PASS

- [ ] **Step 7: 백필 러너를 등록한다**

`ObjectStorageConfiguration`에 `@Slf4j`를 붙이고 아래 빈을 추가한다.

```java
	/**
	 * 기존 로컬 파일 백필. 반복 실행이 필요 없고 진행 상황을 배포 로그에서 바로 봐야 하므로 기동 시 1회로 둔다.
	 * 한 번 돌리고 나면 플래그를 내린다. 다시 켜도 이미 올라간 객체는 건너뛴다.
	 */
	@Bean
	@ConditionalOnProperty(name = "slcn.storage.migration.enabled", havingValue = "true")
	public ApplicationRunner objectStorageMigrationRunner(FileAssetMigrationLogic fileAssetMigrationLogic) {
		return args -> log.info("Object storage migration finished. {}",
			fileAssetMigrationLogic.migrateLegacyFiles());
	}
```

import에 `org.springframework.boot.ApplicationRunner`, `org.springframework.boot.autoconfigure.condition.ConditionalOnProperty`, `com.seoulchonnom.aggregate.file.logic.FileAssetMigrationLogic`, `lombok.extern.slf4j.Slf4j`를 추가한다.

- [ ] **Step 8: `application.yml`에 백필 설정을 추가한다**

`slcn.storage` 블록의 `r2` 아래에 추가한다.

```yaml
    migration:
      # 기존 로컬 파일을 오브젝트 스토리지로 옮긴다. 배포 1회만 켜고 다시 내린다.
      enabled: "${SLCN_STORAGE_MIGRATION_ENABLED:false}"
      batch-size: "${SLCN_STORAGE_MIGRATION_BATCH_SIZE:100}"
```

- [ ] **Step 9: 컨텍스트가 기본값에서 러너 없이 뜨는지 확인한다**

Run: `./gradlew :slcn-boot:test`
Expected: PASS. `slcn.storage.migration.enabled` 기본값이 `false`이므로 러너 빈이 만들어지지 않는다.

- [ ] **Step 10: 커밋한다**

```bash
git add slcn-aggregate/build.gradle \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogic.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/file/store/FileAssetStore.java \
        slcn-aggregate/src/main/java/com/seoulchonnom/aggregate/config/ObjectStorageConfiguration.java \
        slcn-aggregate/src/test/java/com/seoulchonnom/aggregate/file/logic/FileAssetMigrationLogicTest.java \
        slcn-boot/src/main/resources/application.yml
git commit -m "feat: 기존 로컬 이미지 파일 오브젝트 스토리지 백필 추가"
```

---

## Task 8: multipart 설정 정리와 문서 갱신

**Files:**
- Modify: `slcn-boot/src/main/resources/application.yml`
- Modify: `docs/file-asset.md`
- Modify: `docs/image-asset-api.md`

**Interfaces:**
- Consumes: 앞선 모든 태스크의 결과.
- Produces: 없음. 설정과 문서만 바꾼다.

- [ ] **Step 1: `max-request-size`를 명시한다**

`slcn-boot/src/main/resources/application.yml`의 `spring.servlet.multipart` 블록을 아래로 교체한다.

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      # 미설정 시 Spring Boot 기본값 10MB가 요청 전체에 적용되어 다중 업로드가 첫 파일에서 막힌다.
      # 파일당 제한(10MB)은 그대로 두고 요청 총량만 연다.
      max-request-size: 60MB
```

- [ ] **Step 2: 설정이 반영되는지 확인한다**

Run: `./gradlew :slcn-boot:test`
Expected: PASS

- [ ] **Step 3: `docs/file-asset.md`를 갱신한다**

"개요"의 첫 문장을 바꾼다.

```
파일 업로드는 실제 이미지 파일을 오브젝트 스토리지에 저장하고, 파일 메타데이터는 MongoDB `file_asset` 컬렉션에 `FileAsset`으로 저장한다.
```

"업로드 제약"에 아래 한 줄을 추가한다.

```
- 다중 업로드는 요청 전체 60 MB까지다 (`spring.servlet.multipart.max-request-size`)
```

"저장 방식" 섹션 전체를 아래로 교체한다.

````markdown
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
- 로컬 프로바이더는 서명 URL을 만들 수 없으므로 원본도 바이트로 응답한다. 개발 환경의 동작은 이전과 같다.

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
````

- [ ] **Step 4: `docs/image-asset-api.md`를 갱신한다**

"하위 호환" 목록의 `GET /assets/files/{fileId}` 항목을 아래로 교체한다.

```
- `GET /assets/files/{fileId}` — **쿼리 파라미터를 붙이지 않으면 원본이 나온다.** 다만 이제 원본은 오브젝트 스토리지의 서명된 URL로 `302` 리다이렉트되고, 브라우저와 `fetch`는 리다이렉트를 자동으로 따라가므로 FE 코드 변경은 필요 없다. 축소본(`variant`, `width`)은 지금까지처럼 서버가 바이트를 직접 응답한다.
```

"인증" 섹션 끝에 아래 문단을 추가한다.

```
원본 요청이 302로 나가면 `Location`의 서명 URL에는 인증 쿠키가 필요하지 않다. 서명 자체가 접근 권한이고 기본 만료는 300초다. 따라서 서명 URL을 그대로 공유하면 만료 전까지 누구나 열 수 있다. FE는 이 URL을 저장하거나 외부에 노출하지 않는다.

축소본은 계속 서버가 응답하므로 `sessionId` 쿠키 인증과 `img` 태그 사용 방식이 그대로다.
```

- [ ] **Step 5: 전체 테스트를 돌린다**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. 아래로 실패 0을 확인한다.

```bash
find . -path "*/build/test-results/test/TEST-*.xml" \
  -exec grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' {} \; \
  | awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END {print "tests="t, "skipped="s, "failures="f, "errors="e}'
```

Expected: `failures=0 errors=0`, `tests`는 베이스라인 353보다 크다

- [ ] **Step 6: 커밋한다**

```bash
git add slcn-boot/src/main/resources/application.yml docs/file-asset.md docs/image-asset-api.md
git commit -m "docs: 오브젝트 스토리지 저장 방식과 multipart 요청 제한 문서화"
```

---

## 완료 게이트

Phase 1이 끝났다고 말하기 전에 아래를 모두 확인한다.

- [ ] `./gradlew test`가 BUILD SUCCESSFUL이고 failures/errors가 0이다.
- [ ] `slcn.storage.provider`를 설정하지 않은 상태(기본 `local`)에서 `./gradlew :slcn-boot:bootRun --args='--spring.profiles.active=dev'`가 뜨고, 업로드·조회·다운로드가 이전과 동일하게 동작한다.
- [ ] `SLCN_STORAGE_PROVIDER=r2`와 R2 자격 증명을 넣은 환경에서 업로드 한 건을 올린 뒤 버킷에 `originals/travel/{uuid}.{ext}`와 `derived/travel/{uuid}_home-thumb.{ext}`가 생긴다.
- [ ] 같은 환경에서 `GET /api/assets/files/{fileId}`가 `302`와 `Location` 헤더를 반환하고, 그 URL로 원본 바이트를 받을 수 있다.
- [ ] `GET /api/assets/files/{fileId}?variant=home-thumb`은 여전히 `200`과 ETag를 반환하고, 같은 ETag로 재요청하면 `304`가 나온다.
- [ ] `GET /api/assets/files/{fileId}/download`의 리다이렉트 URL로 받은 응답의 `Content-Disposition`에 업로드 당시 파일명이 담긴다. 한글 파일명으로도 확인한다.
- [ ] `SLCN_STORAGE_MIGRATION_ENABLED=true`로 한 번 기동해 백필 로그(`Object storage migration finished. MigrationReport[...]`)를 확인하고, 두 번째 기동에서 `uploaded=0, skipped=N`이 나오는지 확인한다.
- [ ] 백필 후 플래그를 `false`로 되돌린다.
- [ ] 버킷에 `AbortIncompleteMultipartUpload` 라이프사이클 규칙(7일)을 건다. Phase 1은 multipart 업로드를 쓰지 않지만, 규칙을 미리 걸어 두면 Phase 3에서 잊지 않는다.

## 운영 배포 순서

1. R2 버킷을 만들고 자격 증명을 발급한다. 이 시점에는 `SLCN_STORAGE_PROVIDER`를 설정하지 않는다.
2. 코드를 배포한다. 프로바이더가 `local`이므로 동작은 이전과 같고, 저장 키만 새 레이아웃으로 바뀐다.
3. `SLCN_STORAGE_PROVIDER=r2`와 R2 환경변수를 넣고 `SLCN_STORAGE_MIGRATION_ENABLED=true`로 한 번 기동해 백필한다.
4. 백필 로그에서 `failed=0`을 확인한다. 실패가 있으면 원인을 고치고 다시 기동한다(이미 올라간 객체는 건너뛴다).
5. `SLCN_STORAGE_MIGRATION_ENABLED`를 `false`로 되돌리고 재기동한다.
6. 로컬 디스크의 기존 파일은 **최소 한 주기 이상 지켜본 뒤에** 지운다. 백필이 멱등하므로 서두를 이유가 없다.

## Phase 1이 남기는 것

아래는 의도적으로 Phase 1에서 다루지 않는다. 설계 문서의 Phase 2~4에서 처리한다.

- 파생본이 아직 서버를 통과한다. CDN + 서명 쿠키는 Phase 2다.
- 업로드 바이트가 아직 서버를 통과한다. 10MB 제한이 유지되므로 `ImageIO` 경로의 메모리 문제는 경계 안에 머문다. presigned 업로드는 Phase 3이다.
- `FileAsset`에 상태 필드가 없다. 오브젝트 업로드 후 메타데이터 저장이 실패하면 고아 객체가 남는다. 상태머신과 reconciliation은 Phase 3이다.
- RAW/HEIF는 여전히 업로드가 거부된다. Phase 3이다.
- SVG 업로드가 유지된다. 제거는 Phase 3이다.
