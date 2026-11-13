package com.seoulchonnom.spec.house.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 관리자 보강 목록의 단지 한 건. 좌표 또는 건축물대장 보강이 실패했거나 보정을 기다리는 단지다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncompleteComplexRdo {
	private String complexId;
	private String aptSeq;
	private String name;
	private String districtName;
	private String umdName;
	/** 앞 0을 없앤 본번·부번 */
	private String bonbun;
	private String bubun;
	private String roadAddress;
	/** 보정이 없으면 null */
	private AddressOverrideRdo addressOverride;
	/** PENDING, DONE, FAILED */
	private String geocodeStatus;
	private String registerStatus;
	/** 최근 12개월 거래 건수(해제·원천 삭제 제외) */
	private long trades12m;
}
