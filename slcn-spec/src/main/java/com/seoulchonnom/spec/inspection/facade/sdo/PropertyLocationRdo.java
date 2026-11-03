package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 매물 위치 응답. 행안부 원본 좌표(entX/entY)는 노출하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyLocationRdo {
	private String bdMgtSn;
	private String roadAddress;
	private double latitude;
	private double longitude;
}
