package com.seoulchonnom.spec.house.entity.vo;

public enum DealingType {
	BROKER, DIRECT, UNKNOWN;

	/**
	 * 실거래 응답의 dealingGbn 값을 옮긴다. 빈 값이나 모르는 값은 UNKNOWN이다.
	 */
	public static DealingType ofLabel(String label) {
		if (label == null) {
			return UNKNOWN;
		}
		return switch (label.trim()) {
			case "중개거래" -> BROKER;
			case "직거래" -> DIRECT;
			default -> UNKNOWN;
		};
	}
}
