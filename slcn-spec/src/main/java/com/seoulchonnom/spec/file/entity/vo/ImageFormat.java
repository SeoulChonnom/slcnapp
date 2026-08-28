package com.seoulchonnom.spec.file.entity.vo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 파생본 인코딩 포맷. 선언 순서가 곧 시도 순서다.
 * WebP가 JPEG보다 같은 품질에서 약 30% 작지만 네이티브 인코더에 의존하므로, 실패하면 JPEG로 내려간다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageFormat {
	WEBP("webp", "webp", "image/webp"),
	JPEG("jpeg", "jpg", "image/jpeg");

	/**
	 * ImageIO writer를 찾을 때 쓰는 포맷 이름.
	 */
	private final String formatName;
	private final String extension;
	private final String mimeType;
}
