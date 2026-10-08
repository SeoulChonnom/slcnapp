package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현재 위치로 쓸 주소의 좌표 조회 키. 주소 검색 후보의 coordKey를 그대로 보낸다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CurrentLocationSdo {
	private CoordKeySdo coordKey;
}
