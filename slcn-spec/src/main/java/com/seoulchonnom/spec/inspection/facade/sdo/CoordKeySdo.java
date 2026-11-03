package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행안부 좌표제공 API 조회에 필요한 주소 식별값. 주소 검색 응답으로 내려가고, 위치 저장 요청에서 다시 받는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CoordKeySdo {
	private String admCd;
	private String rnMgtSn;
	private String udrtYn;
	private String buldMnnm;
	private String buldSlno;
}
