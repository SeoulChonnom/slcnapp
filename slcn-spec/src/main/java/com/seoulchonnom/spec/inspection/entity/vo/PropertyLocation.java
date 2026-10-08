package com.seoulchonnom.spec.inspection.entity.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매물 위치. 좌표는 항상 행안부 좌표제공 API에서 온 값이다(요청으로 받지 않는다).
 * latitude/longitude는 WGS84, entX/entY는 행안부 원본(EPSG:5179)이다.
 * 같은 건물인지는 bdMgtSn(건물관리번호)으로 판단한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class PropertyLocation {
	/** ViewedPropertyJpo.bdMgtSn 컬럼 길이. 컬럼과 입력 검증이 같은 값을 쓴다. */
	public static final int BD_MGT_SN_MAX_LENGTH = 26;
	/** ViewedPropertyJpo.roadAddress 컬럼 길이. */
	public static final int ROAD_ADDRESS_MAX_LENGTH = 300;

	private String bdMgtSn;
	private String roadAddress;
	private double latitude;
	private double longitude;
	private double entX;
	private double entY;
}
