package com.seoulchonnom.spec.house.entity.vo;

import java.util.Arrays;
import java.util.Optional;

/**
 * 서울시 도시정비사업 통계의 사업유형. 원본 표기는 분기마다 띄어쓰기가 흔들려서 공백을 모두 지운 값으로 비교하고,
 * 저장·응답은 표준 표기(label)를 쓴다.
 */
public enum RedevelopmentProjectType {
	HOUSING_REDEVELOPMENT("주택정비형 재개발"),
	URBAN_REDEVELOPMENT("도시정비형 재개발"),
	APARTMENT_RECONSTRUCTION("공동주택 재건축"),
	APARTMENT_DISTRICT_RECONSTRUCTION("아파트지구 재건축"),
	DETACHED_RECONSTRUCTION("단독주택 재건축");

	private final String label;

	RedevelopmentProjectType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public static Optional<RedevelopmentProjectType> parse(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String compact = raw.replaceAll("\\s+", "");
		return Arrays.stream(values()).filter(type -> type.label.replace(" ", "").equals(compact)).findFirst();
	}
}
