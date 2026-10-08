package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 매물 위치 요청. 좌표는 받지 않는다 - 좌표는 BE가 행안부에서 조회한다.
 * coordKey는 저장된 위치와 다른 건물을 새로 골랐을 때만 필요하다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PropertyLocationInputSdo {
	private String bdMgtSn;
	private String roadAddress;
	private CoordKeySdo coordKey;
}
