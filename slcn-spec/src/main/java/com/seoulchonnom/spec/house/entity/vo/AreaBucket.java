package com.seoulchonnom.spec.house.entity.vo;

import java.math.BigDecimal;

import com.seoulchonnom.spec.house.constant.HouseConstant;

/**
 * 전용면적 구간. 경계는 from 포함, to 미포함이다.
 */
public enum AreaBucket {
	B59, B84, OTHER;

	public static AreaBucket of(BigDecimal exclusiveArea) {
		return of(exclusiveArea, HouseConstant.AREA_B59_FROM, HouseConstant.AREA_B59_TO,
			HouseConstant.AREA_B84_FROM, HouseConstant.AREA_B84_TO);
	}

	/**
	 * 설정으로 경계를 덮어쓸 때 쓴다.
	 */
	public static AreaBucket of(BigDecimal exclusiveArea, BigDecimal b59From, BigDecimal b59To,
		BigDecimal b84From, BigDecimal b84To) {
		if (exclusiveArea == null) {
			return OTHER;
		}
		if (inRange(exclusiveArea, b59From, b59To)) {
			return B59;
		}
		if (inRange(exclusiveArea, b84From, b84To)) {
			return B84;
		}
		return OTHER;
	}

	private static boolean inRange(BigDecimal value, BigDecimal from, BigDecimal to) {
		return value.compareTo(from) >= 0 && value.compareTo(to) < 0;
	}
}
