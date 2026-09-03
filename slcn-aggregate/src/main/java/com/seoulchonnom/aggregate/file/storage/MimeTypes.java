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
