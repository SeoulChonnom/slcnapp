package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 임장 목록 한 행이 지역을 가리킬 때 쓰는 최소 형태.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionAreaBriefRdo {
	private String areaId;
	private String name;
}
