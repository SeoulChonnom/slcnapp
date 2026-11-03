package com.seoulchonnom.spec.house.entity.vo;

/**
 * 학교급. 선언 순서(초·중·고)가 비교표의 표시 순서다.
 */
public enum SchoolLevel {
	E, M, H;

	/**
	 * 학교 표준데이터의 학교종류(schoolSe)를 학교급으로 바꾼다. 특수학교 등 그 밖의 값은 null이다.
	 */
	public static SchoolLevel ofSchoolType(String schoolType) {
		if (schoolType == null) {
			return null;
		}
		return switch (schoolType.strip()) {
			case "초등학교" -> E;
			case "중학교" -> M;
			case "고등학교" -> H;
			default -> null;
		};
	}
}
