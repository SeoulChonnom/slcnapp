package com.seoulchonnom.spec.inspection.facade.sdo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 헤더 문구("6개 지역을 10번 걸었고, 매물 17건을 봤습니다")용 전역 요약.
 * keyword/revisitIntent/page 어느 것에도 영향받지 않는다. hidden=false인 지역 기준이다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionAreaTotalsRdo {
	private long areaCount;
	private long visitCount;
	private long propertyCount;
}
