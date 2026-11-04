package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 좌표를 기다리거나 못 찾은 정비사업 구역 한 건.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncompleteRedevelopmentRdo {
	private String code;
	private String name;
	private String districtName;
	private String jibunAddress;
	private String roadAddress;
	private String geocodeQuery;
	/** PENDING, FAILED */
	private String geocodeStatus;
	private int geocodeAttempts;
}
