package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 요청에 빠진 매물은 기존 sortOrder를 유지한다. 중복 값은 허용하고
 * 조회 정렬에 결정적 2차 키(registeredTime, id)를 붙여 화면이 흔들리지 않게 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewedPropertyOrderUdo {
	private String propertyId;
	private int sortOrder;
}
