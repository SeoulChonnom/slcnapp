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
