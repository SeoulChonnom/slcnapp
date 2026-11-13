package com.seoulchonnom.spec.house.entity.vo;

import java.util.Arrays;
import java.util.Optional;

/**
 * 정비사업 추진단계. 공백을 모두 지운 값으로 비교하고 저장·응답은 label 그대로다.
 */
public enum RedevelopmentStage {
	ZONE_DESIGNATED("구역지정"),
	COMMITTEE("추진위"),
	ASSOCIATION("조합설립"),
	ARCHITECTURAL_REVIEW("건축심의"),
	PROJECT_IMPLEMENTATION("사업시행"),
	MANAGEMENT_DISPOSITION("관리처분"),
	CONSTRUCTION_START("착공");

	private final String label;

	RedevelopmentStage(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public static Optional<RedevelopmentStage> parse(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String compact = raw.replaceAll("\\s+", "");
		return Arrays.stream(values()).filter(stage -> stage.label.equals(compact)).findFirst();
	}
}
