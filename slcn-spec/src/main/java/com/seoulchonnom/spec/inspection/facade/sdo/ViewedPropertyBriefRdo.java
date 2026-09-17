package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 지역 목록의 topProperty와 임장 목록의 topInterestProperty가 공유하는 형태.
 * 고르는 범위만 다르다 — 전자는 지역 전체, 후자는 그 회차 안.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewedPropertyBriefRdo {
	private String propertyId;
	private String complexName;
	private String name;
	private Integer interestLevel;
}
