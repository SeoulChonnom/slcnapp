package com.seoulchonnom.spec.file.entity.vo;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 홈 화면이 사용하는 표지 이미지 파생본. 업로드 시점에 미리 생성되며, 원본은 항상 그대로 보존된다.
 */
@Getter
@RequiredArgsConstructor
public enum ImageVariant {
	HOME_FEATURE("home-feature", 960),
	HOME_THUMB("home-thumb", 320);

	private final String value;
	private final int width;

	/**
	 * 알 수 없는 값이면 비어 있는 Optional을 반환한다. 호출자는 원본으로 폴백해야 한다.
	 */
	public static Optional<ImageVariant> from(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}

		String normalized = value.trim();
		return Arrays.stream(values())
			.filter(variant -> variant.value.equalsIgnoreCase(normalized))
			.findFirst();
	}

	/**
	 * 요청 width를 커버할 수 있는 가장 작은 파생본을 고른다. 모든 파생본보다 크면 원본을 쓰라는 뜻이므로 비어 있는 값을 반환한다.
	 */
	public static Optional<ImageVariant> coveringWidth(Integer width) {
		if (width == null || width <= 0) {
			return Optional.empty();
		}

		return Arrays.stream(values())
			.filter(variant -> variant.width >= width)
			.min(Comparator.comparingInt(ImageVariant::getWidth));
	}

	@JsonValue
	public String getValue() {
		return value;
	}
}
